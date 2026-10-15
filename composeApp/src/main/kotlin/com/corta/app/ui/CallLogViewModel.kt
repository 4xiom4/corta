package com.corta.app.ui

import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import com.corta.db.CallLogEntry
import com.corta.db.FilterRule
import com.corta.domain.ChileanNumberType
import com.corta.domain.ChileanTelecomClassifier
import com.corta.domain.ChileanTelecomMeta
import com.corta.domain.CortaRepository
import com.corta.domain.FilterAction
import com.corta.domain.PhoneNumberUtils
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@Immutable
data class CallLogUiModel(
    val id: String,
    val number: String,
    val name: String?,
    val date: Long,
    val type: Int,
    val duration: Long,
    val matchingRule: FilterRule?,
    val telecomMeta: ChileanTelecomMeta = ChileanTelecomClassifier.classify(number),
    val isBlocked: Boolean = false,
    val isMissed: Boolean = false,
    val isPingCall: Boolean = false,
    val isIncoming: Boolean = false,
    val isOutgoing: Boolean = false,
    val isContact: Boolean = false,
    val displayName: String = number,
    val formattedNumber: String = number,
    val tag: String = "Llamada",
    val dateDisplay: String = "",
    val subtitleText: String = "",
    val simSlot: Int? = null
)

fun buildCallLogUiModel(
    id: String,
    number: String,
    name: String?,
    date: Long,
    type: Int,
    duration: Long,
    matchingRule: FilterRule?,
    simSlot: Int? = null
): CallLogUiModel {
    val telecomMeta = ChileanTelecomClassifier.classify(number)
    val isBlocked = type == CallLog.Calls.REJECTED_TYPE ||
        type == CallLog.Calls.BLOCKED_TYPE ||
        matchingRule?.action == FilterAction.BLOCK.name
    val isMissed = type == CallLog.Calls.MISSED_TYPE
    val isOutgoing = type == CallLog.Calls.OUTGOING_TYPE
    val isAnsweredIncoming = type == CallLog.Calls.INCOMING_TYPE
    // In telephony, all calls directed to this phone (answered, missed, blocked) are incoming calls
    val isIncoming = type != CallLog.Calls.OUTGOING_TYPE
    val isPingCall = duration in 0..2 && (isMissed || type == CallLog.Calls.REJECTED_TYPE)
    val isContact = !name.isNullOrBlank()

    val formattedNum = PhoneNumberUtils.formatDisplayNumber(number)
    val resolvedName = resolveCallDisplayName(
        number = number,
        contactName = name,
        matchingRule = matchingRule
    )
    val displayName = if (resolvedName == number) formattedNum else resolvedName

    val tag = when {
        isBlocked -> "Spam"
        telecomMeta.type == ChileanNumberType.SUBTEL_TELEMARKETING_809 || number.contains("809") -> "Comercial"
        isPingCall -> "Fantasma"
        telecomMeta.type == ChileanNumberType.EMERGENCY_NATIONAL -> "Emergencia"
        telecomMeta.type == ChileanNumberType.MUNICIPAL_SECURITY_14XX -> "Municipal"
        telecomMeta.isVerifiedOfficialService -> "Oficial"
        isContact -> "Contacto"
        telecomMeta.type == ChileanNumberType.MOBILE -> "Móvil"
        telecomMeta.type == ChileanNumberType.FIXED_LANDLINE -> "Fijo"
        else -> "Llamada"
    }

    val dateDisplay = formatCallDateChile(date)
    val durationPart = formatCallDuration(duration)
    val statusPart = when {
        isBlocked -> "Silenciada automáticamente"
        telecomMeta.type == ChileanNumberType.SUBTEL_TELEMARKETING_809 -> "Silenciada automáticamente"
        isPingCall -> "Llamada fantasma (ping)"
        isMissed -> "No contestada"
        isAnsweredIncoming && durationPart.isNotBlank() -> "Entrante ($durationPart)"
        isAnsweredIncoming -> "Entrante"
        isOutgoing && durationPart.isNotBlank() -> "Saliente ($durationPart)"
        isOutgoing -> "Saliente"
        else -> "Llamada"
    }

    val subtitleText = if (displayName != formattedNum && formattedNum.isNotBlank()) {
        "$formattedNum · $statusPart"
    } else {
        statusPart
    }

    return CallLogUiModel(
        id = id,
        number = number,
        name = name,
        date = date,
        type = type,
        duration = duration,
        matchingRule = matchingRule,
        telecomMeta = telecomMeta,
        isBlocked = isBlocked,
        isMissed = isMissed,
        isPingCall = isPingCall,
        isIncoming = isIncoming,
        isOutgoing = isOutgoing,
        isContact = isContact,
        displayName = displayName,
        formattedNumber = formattedNum,
        tag = tag,
        dateDisplay = dateDisplay,
        subtitleText = subtitleText,
        simSlot = simSlot
    )
}

class CallLogPagingSource(
    private val context: Context,
    private val repository: CortaRepository,
    private val internalLogs: List<CallLogEntry>,
    private val rules: List<FilterRule>
) : PagingSource<Int, CallLogUiModel>() {

    private val ruleCache = mutableMapOf<String, FilterRule?>()

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CallLogUiModel> = withContext(Dispatchers.IO) {
        try {
            val position = params.key ?: 0
            val loadSize = params.loadSize

            val resultLogs = mutableListOf<CallLogUiModel>()

            // On initial load (position 0), prepend internal logs recorded directly by Corta
            if (position == 0) {
                internalLogs.forEach { internalLog ->
                    val rule = ruleCache.getOrPut(internalLog.phoneNumber) {
                        repository.getRuleForNumber(internalLog.phoneNumber, rules)
                    }
                    resultLogs.add(
                        buildCallLogUiModel(
                            id = "internal_${internalLog.id}",
                            number = internalLog.phoneNumber,
                            name = null,
                            date = internalLog.timestamp,
                            type = CallLog.Calls.BLOCKED_TYPE,
                            duration = internalLog.duration,
                            matchingRule = rule
                        )
                    )
                }
            }

            val projection = arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.DATE,
                CallLog.Calls.TYPE,
                CallLog.Calls.DURATION,
                "subscription_id",
                CallLog.Calls.PHONE_ACCOUNT_ID
            )

            // Safe Android 15 & API 26+ pagination using Bundle without raw SQL LIMIT/OFFSET in sortOrder
            val cursor = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val queryArgs = Bundle().apply {
                        putInt(ContentResolver.QUERY_ARG_LIMIT, loadSize)
                        putInt(ContentResolver.QUERY_ARG_OFFSET, position)
                        putStringArray(
                            ContentResolver.QUERY_ARG_SORT_COLUMNS,
                            arrayOf(CallLog.Calls.DATE)
                        )
                        putInt(
                            ContentResolver.QUERY_ARG_SORT_DIRECTION,
                            ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                        )
                    }
                    context.contentResolver.query(
                        CallLog.Calls.CONTENT_URI,
                        projection,
                        queryArgs,
                        null
                    )
                } else {
                    // Safe query for pre-O: standard sortOrder without SQL injection clauses
                    context.contentResolver.query(
                        CallLog.Calls.CONTENT_URI,
                        projection,
                        null,
                        null,
                        "${CallLog.Calls.DATE} DESC"
                    )
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed querying CallLog ContentResolver")
                null
            }

            var itemsRead = 0
            cursor?.use {
                val idIdx = it.getColumnIndex(CallLog.Calls._ID)
                val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)
                val subIdIdx = it.getColumnIndex("subscription_id")
                val accountIdIdx = it.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)

                // On older APIs without Bundle support, advance cursor to the requested position
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    if (position > 0 && !it.moveToPosition(position - 1)) {
                        return@use
                    }
                }

                while (it.moveToNext() && itemsRead < loadSize) {
                    itemsRead++
                    val number = it.getString(numIdx) ?: "Desconocido"
                    val matchingRule = ruleCache.getOrPut(number) {
                        repository.getRuleForNumber(number, rules)
                    }

                    val simSlot = when {
                        subIdIdx != -1 && !it.isNull(subIdIdx) -> {
                            val subId = it.getInt(subIdIdx)
                            if (subId == 2) 2 else if (subId == 1) 1 else null
                        }
                        accountIdIdx != -1 && !it.isNull(accountIdIdx) -> {
                            val acc = it.getString(accountIdIdx) ?: ""
                            if (acc.contains("1")) 2 else if (acc.contains("0")) 1 else null
                        }
                        else -> null
                    }

                    resultLogs.add(
                        buildCallLogUiModel(
                            id = it.getString(idIdx) ?: "call_${System.currentTimeMillis()}_$itemsRead",
                            number = number,
                            name = it.getString(nameIdx),
                            date = it.getLong(dateIdx),
                            type = it.getInt(typeIdx),
                            duration = it.getLong(durIdx),
                            matchingRule = matchingRule,
                            simSlot = simSlot
                        )
                    )
                }
            }

            if (position == 0) {
                resultLogs.sortByDescending { it.date }
            }

            val nextKey = if (itemsRead < loadSize) null else position + itemsRead

            LoadResult.Page(
                data = resultLogs,
                prevKey = if (position == 0) null else maxOf(0, position - loadSize),
                nextKey = nextKey
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error loading page in CallLogPagingSource")
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, CallLogUiModel>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(state.config.pageSize)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(state.config.pageSize)
        }
    }

    private companion object {
        const val TAG = "Corta.CallLogPaging"
    }
}

class CallLogViewModel(
    private val context: Context,
    private val repository: CortaRepository
) : ViewModel() {

    private var currentInternalLogs: List<CallLogEntry> = emptyList()
    private var currentRules: List<FilterRule> = emptyList()
    private var currentPagingSource: CallLogPagingSource? = null

    init {
        viewModelScope.launch {
            repository.observeCallLogs().collect { logs ->
                currentInternalLogs = logs
                currentPagingSource?.invalidate()
            }
        }
        viewModelScope.launch {
            repository.observeRules().collect { rules ->
                currentRules = rules
                currentPagingSource?.invalidate()
            }
        }
    }

    val callLogs: Flow<PagingData<CallLogUiModel>> = Pager(
        config = PagingConfig(
            pageSize = 50,
            enablePlaceholders = false,
            initialLoadSize = 100,
            prefetchDistance = 25
        )
    ) {
        CallLogPagingSource(context, repository, currentInternalLogs, currentRules).also {
            currentPagingSource = it
        }
    }.flow.cachedIn(viewModelScope)
}
