package com.corta.app.ui.sms

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import com.corta.db.FilterRule
import com.corta.domain.ChileanSmsMeta
import com.corta.domain.ChileanTelecomClassifier
import com.corta.domain.CortaRepository
import com.corta.domain.FilterAction
import com.corta.domain.PhoneNumberUtils
import com.corta.domain.SmsMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.Immutable
import timber.log.Timber
import java.util.Collections

@Immutable
data class SmsUiModel(
    val message: SmsMessage,
    val displayName: String,
    val telecomMeta: ChileanSmsMeta = ChileanTelecomClassifier.classifySms(
        address = message.address,
        body = message.body,
        isKnownContact = displayName != message.address && !displayName.startsWith("+")
    )
)

data class BlockFilters(
    val exact: Set<String>,
    val prefix: List<String>,
    val regex: List<Regex>
)

data class RulesAndFilters(
    val rules: List<FilterRule>,
    val filters: BlockFilters
)

fun resolveSmsDisplayName(
    address: String,
    contactName: String?,
    matchingRule: FilterRule?
): String {
    return when {
        !contactName.isNullOrBlank() -> contactName.trim()
        matchingRule != null && matchingRule.source.equals("USER", ignoreCase = true) && !matchingRule.alias.isNullOrBlank() -> matchingRule.alias!!.trim()
        else -> address
    }
}

fun resolveContactName(
    context: Context,
    address: String,
    contactNameCache: MutableMap<String, String?>
): String? {
    if (address.isBlank()) return null

    // Alphanumeric sender IDs (e.g., "BancoEstado", "WOM", "Uber") are never phone contacts.
    // Querying PhoneLookup with them can throw IllegalArgumentException in ContactsProvider.
    if (address.any { it.isLetter() }) {
        contactNameCache[address] = null
        return null
    }

    val normalized = PhoneNumberUtils.normalize(address)

    // Check in-memory cache (raw and normalized)
    if (contactNameCache.containsKey(address)) {
        return contactNameCache[address]
    }
    if (normalized.isNotBlank() && contactNameCache.containsKey(normalized)) {
        val cached = contactNameCache[normalized]
        contactNameCache[address] = cached
        return cached
    }

    // Guard: Only query if READ_CONTACTS is granted. Do NOT cache negative result if permission is missing,
    // so when granted later, subsequent page loads will successfully fetch contacts.
    val hasPermission = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED
    if (!hasPermission) {
        return null
    }

    val name = try {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(address)
        )
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (nameIndex != -1) {
                    cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }?.trim()
                } else null
            } else null
        }
    } catch (e: Exception) {
        Timber.tag("Corta.SmsPaging").w(e, "Error resolving contact name for address: $address")
        null
    }

    contactNameCache[address] = name
    if (normalized.isNotBlank()) {
        contactNameCache[normalized] = name
    }
    return name
}

class SmsPagingSource(
    private val context: Context,
    private val cortaRepository: CortaRepository,
    private val rulesAndFilters: RulesAndFilters,
    private val isBlockedTab: Boolean,
    private val contactNameCache: MutableMap<String, String?> = Collections.synchronizedMap(mutableMapOf())
) : PagingSource<Int, SmsUiModel>() {

    private val addressBlockCache = mutableMapOf<String, Boolean>()
    private val ruleCache = mutableMapOf<String, FilterRule?>()

    private fun isAddressBlocked(address: String): Boolean {
        return addressBlockCache.getOrPut(address) {
            // Whitelisted numbers (2FA OTP, public services, emergencies) are NEVER blocked
            if (cortaRepository.isWhitelistedNumber(address)) {
                return@getOrPut false
            }

            val normalized = PhoneNumberUtils.normalize(address)
            val filters = rulesAndFilters.filters
            normalized in filters.exact ||
                address in filters.exact ||
                filters.prefix.any { (normalized.isNotEmpty() && normalized.startsWith(it)) || address.startsWith(it) } ||
                filters.regex.any { it.containsMatchIn(normalized) || it.containsMatchIn(address) }
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SmsUiModel> = withContext(Dispatchers.IO) {
        try {
            val position = params.key ?: 0
            val loadSize = params.loadSize

            val messages = mutableListOf<SmsUiModel>()

            // On initial load (position 0) for blocked tab, prepend internal blocked SMS recorded by Corta
            if (isBlockedTab && position == 0) {
                try {
                    val internalBlocked = cortaRepository.observeBlockedSms().first()
                    internalBlocked.forEach { blocked ->
                        val contactName = resolveContactName(context, blocked.sender, contactNameCache)
                        val rule = if (ruleCache.containsKey(blocked.sender)) {
                            ruleCache[blocked.sender]
                        } else {
                            val r = cortaRepository.getRuleForNumber(blocked.sender, rulesAndFilters.rules)
                            ruleCache[blocked.sender] = r
                            r
                        }
                        val displayName = resolveSmsDisplayName(
                            address = blocked.sender,
                            contactName = contactName,
                            matchingRule = rule
                        )

                        val meta = ChileanTelecomClassifier.classifySms(
                            address = blocked.sender,
                            body = blocked.body,
                            isKnownContact = displayName != blocked.sender && !displayName.startsWith("+"),
                            isBlocked = true
                        )

                        messages.add(
                            SmsUiModel(
                                message = SmsMessage(
                                    id = "blocked_${blocked.id}",
                                    threadId = "BLOCKED_${blocked.sender}",
                                    address = blocked.sender,
                                    body = blocked.body,
                                    date = blocked.timestamp,
                                    type = 1
                                ),
                                displayName = displayName,
                                telecomMeta = meta
                            )
                        )
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Error loading internal blocked SMS")
                }
            }

            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE
            )

            // Safe Android 15 & API 26+ pagination using Bundle
            val cursor = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val queryArgs = Bundle().apply {
                        putInt(ContentResolver.QUERY_ARG_LIMIT, loadSize)
                        putInt(ContentResolver.QUERY_ARG_OFFSET, position)
                        putStringArray(
                            ContentResolver.QUERY_ARG_SORT_COLUMNS,
                            arrayOf(Telephony.Sms.DATE)
                        )
                        putInt(
                            ContentResolver.QUERY_ARG_SORT_DIRECTION,
                            ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                        )
                    }
                    context.contentResolver.query(
                        Telephony.Sms.CONTENT_URI,
                        projection,
                        queryArgs,
                        null
                    )
                } else {
                    context.contentResolver.query(
                        Telephony.Sms.CONTENT_URI,
                        projection,
                        null,
                        null,
                        "${Telephony.Sms.DATE} DESC"
                    )
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed querying Telephony.Sms ContentResolver")
                null
            }

            val threadIdsSeen = mutableSetOf<String>()
            var itemsRead = 0

            cursor?.use {
                val idIndex = it.getColumnIndex(Telephony.Sms._ID)
                val threadIdIndex = it.getColumnIndex(Telephony.Sms.THREAD_ID)
                val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
                val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)

                // Check whether ContentResolver actually honored QUERY_ARG_OFFSET
                val honoredArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.extras?.getStringArrayList(ContentResolver.EXTRA_HONORED_ARGS)
                } else null
                val offsetHonored = honoredArgs?.contains(ContentResolver.QUERY_ARG_OFFSET) == true

                // If not honored by the provider (e.g. system SmsProvider on Android/MIUI),
                // advance cursor manually to requested position
                if (!offsetHonored && position > 0) {
                    if (!it.moveToPosition(position - 1)) {
                        return@use
                    }
                }

                while (it.moveToNext() && itemsRead < loadSize) {
                    itemsRead++
                    val threadId = it.getString(threadIdIndex) ?: "thread_$itemsRead"
                    if (threadIdsSeen.add(threadId)) {
                        val address = it.getString(addressIndex) ?: "Desconocido"
                        val isBlocked = isAddressBlocked(address)

                        if ((isBlockedTab && isBlocked) || (!isBlockedTab && !isBlocked)) {
                            val contactName = resolveContactName(context, address, contactNameCache)
                            val rule = if (ruleCache.containsKey(address)) {
                                ruleCache[address]
                            } else {
                                val r = cortaRepository.getRuleForNumber(address, rulesAndFilters.rules)
                                ruleCache[address] = r
                                r
                            }
                            val displayName = resolveSmsDisplayName(
                                address = address,
                                contactName = contactName,
                                matchingRule = rule
                            )

                            val body = it.getString(bodyIndex) ?: ""
                            val meta = ChileanTelecomClassifier.classifySms(
                                address = address,
                                body = body,
                                isKnownContact = displayName != address && !displayName.startsWith("+"),
                                isBlocked = isBlocked
                            )

                            messages.add(
                                SmsUiModel(
                                    message = SmsMessage(
                                        id = it.getString(idIndex) ?: "sms_$itemsRead",
                                        threadId = threadId,
                                        address = address,
                                        body = body,
                                        date = it.getLong(dateIndex),
                                        type = it.getInt(typeIndex)
                                    ),
                                    displayName = displayName,
                                    telecomMeta = meta
                                )
                            )
                        }
                    }
                }
            }

            if (position == 0) {
                messages.sortByDescending { it.message.date }
            }

            val deduplicatedMessages = messages.distinctBy { it.message.threadId.ifBlank { it.message.id } }
            val nextKey = if (itemsRead < loadSize) null else position + itemsRead

            LoadResult.Page(
                data = deduplicatedMessages,
                prevKey = if (position == 0) null else maxOf(0, position - loadSize),
                nextKey = nextKey
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error loading page in SmsPagingSource")
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, SmsUiModel>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(state.config.pageSize)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(state.config.pageSize)
        }
    }

    private companion object {
        const val TAG = "Corta.SmsPaging"
    }
}

class SmsViewModel(
    private val context: Context,
    private val cortaRepository: CortaRepository
) : ViewModel() {

    val contactNameCache: MutableMap<String, String?> = Collections.synchronizedMap(mutableMapOf())

    private var currentRulesAndFilters = RulesAndFilters(
        rules = emptyList(),
        filters = BlockFilters(emptySet(), emptyList(), emptyList())
    )
    private var currentInboxPagingSource: SmsPagingSource? = null
    private var currentBlockedPagingSource: SmsPagingSource? = null

    init {
        viewModelScope.launch {
            cortaRepository.observeRules().collect { rules ->
                val blockExact = HashSet<String>()
                val blockPrefix = mutableListOf<String>()
                val blockRegex = mutableListOf<Regex>()

                rules.filter { it.action == FilterAction.BLOCK.name }.forEach { rule ->
                    if (rule.isRegex == true) {
                        runCatching { Regex(rule.pattern) }.getOrNull()?.let { blockRegex.add(it) }
                    } else {
                        val normalizedPattern = PhoneNumberUtils.normalize(rule.pattern)
                        blockExact.add(normalizedPattern)
                        blockExact.add(rule.pattern)
                        if (rule.pattern.length < 11) blockPrefix.add(rule.pattern)
                        if (normalizedPattern.length < 11) blockPrefix.add(normalizedPattern)
                    }
                }

                currentRulesAndFilters = RulesAndFilters(
                    rules = rules,
                    filters = BlockFilters(blockExact, blockPrefix, blockRegex)
                )

                currentInboxPagingSource?.invalidate()
                currentBlockedPagingSource?.invalidate()
            }
        }
    }

    val inboxSms: Flow<PagingData<SmsUiModel>> = Pager(
        config = PagingConfig(pageSize = 50, enablePlaceholders = false, initialLoadSize = 80, prefetchDistance = 20)
    ) {
        SmsPagingSource(
            context = context,
            cortaRepository = cortaRepository,
            rulesAndFilters = currentRulesAndFilters,
            isBlockedTab = false,
            contactNameCache = contactNameCache
        ).also {
            currentInboxPagingSource = it
        }
    }.flow.cachedIn(viewModelScope)

    val blockedSms: Flow<PagingData<SmsUiModel>> = Pager(
        config = PagingConfig(pageSize = 50, enablePlaceholders = false, initialLoadSize = 80, prefetchDistance = 20)
    ) {
        SmsPagingSource(
            context = context,
            cortaRepository = cortaRepository,
            rulesAndFilters = currentRulesAndFilters,
            isBlockedTab = true,
            contactNameCache = contactNameCache
        ).also {
            currentBlockedPagingSource = it
        }
    }.flow.cachedIn(viewModelScope)

    fun clearContactCache() {
        contactNameCache.clear()
        currentInboxPagingSource?.invalidate()
        currentBlockedPagingSource?.invalidate()
    }
}
