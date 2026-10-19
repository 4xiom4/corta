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
import com.corta.app.ui.contacts.ChileanRegionalDirectory
import com.corta.app.ui.contacts.DirectoryCategory
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
    simSlot: Int? = null,
    screeningAction: FilterAction? = null
): CallLogUiModel {
    val telecomMeta = ChileanTelecomClassifier.classify(number)
    val isExplicitlyBlocked = type == CallLog.Calls.BLOCKED_TYPE ||
        matchingRule?.action == FilterAction.BLOCK.name ||
        screeningAction == FilterAction.BLOCK
    val isBlocked = isExplicitlyBlocked
    val isMissed = type == CallLog.Calls.MISSED_TYPE || (type == CallLog.Calls.REJECTED_TYPE && !isExplicitlyBlocked)
    val isOutgoing = type == CallLog.Calls.OUTGOING_TYPE
    val isAnsweredIncoming = type == CallLog.Calls.INCOMING_TYPE
    val isIncoming = type == CallLog.Calls.INCOMING_TYPE

    // Una llamada perdida común NO es una llamada fantasma.
    // Solo se califica como "Fantasma" (ping wangiri) si la duración fue <= 2s, no fue contestada,
    // y proviene de telemarketing 809, números desconocidos internacionales o patrones sospechosos.
    val isSuspiciousPingNumber = telecomMeta.type == ChileanNumberType.SUBTEL_TELEMARKETING_809 ||
        (number.startsWith("+") && !number.startsWith("+56")) ||
        telecomMeta.type == ChileanNumberType.UNKNOWN
    val isPingCall = duration in 0..2 && isMissed && isSuspiciousPingNumber && !isBlocked && name.isNullOrBlank()
    val isContact = !name.isNullOrBlank()

    val formattedNum = PhoneNumberUtils.formatDisplayNumber(number)
    val directoryEntry = ChileanRegionalDirectory.findByNumber(number)
    val resolvedName = resolveCallDisplayName(
        number = number,
        contactName = name,
        matchingRule = matchingRule
    )
    val displayName = when {
        resolvedName != number -> resolvedName
        directoryEntry != null -> directoryEntry.name
        else -> formattedNum
    }

    val tag = when {
        isBlocked -> "Spam"
        telecomMeta.type == ChileanNumberType.SUBTEL_TELEMARKETING_809 || number.contains("809") -> "Comercial"
        isPingCall -> "Fantasma"
        telecomMeta.type == ChileanNumberType.EMERGENCY_NATIONAL || directoryEntry?.category == DirectoryCategory.EMERGENCY -> "Emergencia"
        telecomMeta.type == ChileanNumberType.MUNICIPAL_SECURITY_14XX || directoryEntry?.category == DirectoryCategory.MUNICIPAL -> "Municipal"
        telecomMeta.isVerifiedOfficialService || directoryEntry != null -> "Oficial"
        isContact -> "Contacto"
        isMissed -> "Perdida"
        telecomMeta.type == ChileanNumberType.MOBILE -> "Móvil"
        telecomMeta.type == ChileanNumberType.FIXED_LANDLINE -> "Fijo"
        else -> "Llamada"
    }

    val dateDisplay = formatCallDateChile(date)
    val durationPart = formatCallDuration(duration)
    val location = directoryEntry?.comuna ?: telecomMeta.cityOrRegion
    val locationPrefix = if (!location.isNullOrBlank()) "$location · " else ""

    val statusPart = when {
        isBlocked -> "${locationPrefix}Silenciada automáticamente"
        telecomMeta.type == ChileanNumberType.SUBTEL_TELEMARKETING_809 -> "${locationPrefix}Silenciada automáticamente"
        isPingCall -> "${locationPrefix}Llamada fantasma (ping)"
        isMissed -> "${locationPrefix}No contestada"
        type == CallLog.Calls.REJECTED_TYPE -> "${locationPrefix}Llamada rechazada"
        isAnsweredIncoming && durationPart.isNotBlank() -> "${locationPrefix}Entrante ($durationPart)"
        isAnsweredIncoming -> "${locationPrefix}Entrante"
        isOutgoing && durationPart.isNotBlank() -> "Saliente ($durationPart)"
        isOutgoing -> "Saliente"
        else -> "${locationPrefix}Llamada"
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
            val matchedInternalLogIds = mutableSetOf<Long>()

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

            Timber.tag(TAG).d("CallLog query: position=$position loadSize=$loadSize cursorCount=${cursor?.count} internalLogsCount=${internalLogs.size}")
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
                    val callDate = it.getLong(dateIdx)
                    val rawType = it.getInt(typeIdx)
                    val duration = it.getLong(durIdx)

                    val matchingRule = ruleCache.getOrPut(number) {
                        repository.getRuleForNumber(number, rules)
                    }

                    // Unificar con logs internos para saber la decisión de Corta (BLOCK / WARN / ALLOW)
                    // sin duplicar la llamada en pantalla
                    val normalizedNum = PhoneNumberUtils.normalize(number)
                    val matchingInternal = internalLogs.firstOrNull { internal ->
                        PhoneNumberUtils.normalize(internal.phoneNumber) == normalizedNum &&
                            kotlin.math.abs(internal.timestamp - callDate) < 30_000L
                    }

                    val screeningAction = matchingInternal?.let { matched ->
                        matchedInternalLogIds.add(matched.id)
                        try { FilterAction.valueOf(matched.actionTaken) } catch (e: Exception) { null }
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
                            date = callDate,
                            type = rawType,
                            duration = duration,
                            matchingRule = matchingRule,
                            simSlot = simSlot,
                            screeningAction = screeningAction
                        )
                    )
                }
            }

            // Si es la página inicial, incluir únicamente logs internos huérfanos que el sistema Android
            // no haya registrado en CallLog.Calls (por ejemplo, llamadas bloqueadas antes de sonar en ciertos fabricantes)
            if (position == 0) {
                internalLogs.filter { it.id !in matchedInternalLogIds }.forEach { orphanLog ->
                    val rule = ruleCache.getOrPut(orphanLog.phoneNumber) {
                        repository.getRuleForNumber(orphanLog.phoneNumber, rules)
                    }
                    val orphanAction = try { FilterAction.valueOf(orphanLog.actionTaken) } catch (e: Exception) { FilterAction.BLOCK }
                    val syntheticType = when (orphanAction) {
                        FilterAction.BLOCK -> CallLog.Calls.BLOCKED_TYPE
                        FilterAction.MUTE -> CallLog.Calls.MISSED_TYPE
                        else -> CallLog.Calls.INCOMING_TYPE
                    }
                    resultLogs.add(
                        buildCallLogUiModel(
                            id = "internal_${orphanLog.id}",
                            number = orphanLog.phoneNumber,
                            name = null,
                            date = orphanLog.timestamp,
                            type = syntheticType,
                            duration = orphanLog.duration,
                            matchingRule = rule,
                            screeningAction = orphanAction
                        )
                    )
                }
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
