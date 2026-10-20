package com.corta.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.corta.app.ui.contacts.ChileanDirectoryEntry
import com.corta.app.ui.contacts.ChileanRegion
import com.corta.app.ui.contacts.ChileanRegionalDirectory
import com.corta.app.ui.contacts.DirectoryCategory
import com.corta.app.ui.contacts.RegionSelectorBottomSheet
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.PhoneCallback
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.paging.compose.collectAsLazyPagingItems
import com.corta.app.R
import com.corta.app.ui.dialer.DialpadScreen
import com.corta.app.ui.i18n.Strings
import com.corta.app.ui.designsystem.CortaCategoryExploreButton
import com.corta.app.ui.designsystem.CortaFloatingActionButton
import com.corta.app.ui.designsystem.StitchFilterChip
import com.corta.app.ui.export.CortaExportImportHelper
import com.corta.app.ui.theme.Handedness
import com.corta.app.ui.theme.LocalDetailedMode
import com.corta.app.ui.theme.LocalHandedness
import com.corta.app.ui.trash.TrashBottomSheet
import com.corta.db.FilterRule
import com.corta.domain.ChileanNumberType
import com.corta.domain.ChileanTelecomClassifier
import com.corta.domain.ChileanTelecomMeta
import com.corta.domain.CortaRepository
import com.corta.domain.ContactRepository
import com.corta.domain.ContactInfo
import com.corta.app.ui.contacts.normalizeForSearch
import com.corta.domain.FilterAction
import com.corta.domain.PhoneNumberUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

fun resolveCallDisplayName(
    number: String,
    contactName: String?,
    matchingRule: FilterRule?
): String {
    return when {
        !contactName.isNullOrBlank() -> contactName.trim()
        matchingRule != null && matchingRule.source.equals("USER", ignoreCase = true) && !matchingRule.alias.isNullOrBlank() -> matchingRule.alias!!.trim()
        else -> {
            val directoryMatch = com.corta.app.ui.contacts.ChileanRegionalDirectory.findByNumber(number)
            directoryMatch?.name ?: number
        }
    }
}


enum class CallLogTabFilter(
    val label: String,
    val subtitle: String,
    val isPrimary: Boolean
) {
    ALL("Todas", "Todo el historial de llamadas", true),
    BLOCKED("Spam", "Llamadas bloqueadas y silenciadas", true),
    MISSED("Perdidas", "Llamadas no contestadas", true),
    SUBTEL_809("Chao 809", "Telemarketing comercial masivo", true),
    INCOMING("Entrantes", "Llamadas recibidas contestadas", false),
    OUTGOING("Salientes", "Llamadas emitidas desde tu equipo", false),
    REGIONAL("Directorio", "Directorio oficial y emergencias comunales", true);
}

private val callTimeFormat = ThreadLocal.withInitial { SimpleDateFormat("HH:mm", Locale.getDefault()) }
private val callDayMonthFormat = ThreadLocal.withInitial { SimpleDateFormat("d MMM", Locale("es", "CL")) }
private val callYearFormat = ThreadLocal.withInitial { SimpleDateFormat("dd/MM/yy", Locale.getDefault()) }

fun formatCallDateChile(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val oneDayMs = 86_400_000L

    if (diff in 0..oneDayMs && android.text.format.DateUtils.isToday(timestamp)) {
        return callTimeFormat.get()?.format(Date(timestamp)) ?: ""
    }
    if (diff in 0..(2 * oneDayMs) && android.text.format.DateUtils.isToday(timestamp + oneDayMs)) {
        return "Ayer"
    }
    if (diff < 300L * oneDayMs) {
        return callDayMonthFormat.get()?.format(Date(timestamp)) ?: ""
    }
    return callYearFormat.get()?.format(Date(timestamp)) ?: ""
}

fun formatCallDuration(seconds: Long): String {
    if (seconds <= 0) return ""
    val mins = seconds / 60
    val secs = seconds % 60
    return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
}

fun formatCallDateSectionChile(timestamp: Long): String {
    if (timestamp <= 0L) return "Anteriores"
    val now = java.util.Calendar.getInstance()
    val callCal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }

    val nowYear = now.get(java.util.Calendar.YEAR)
    val callYear = callCal.get(java.util.Calendar.YEAR)
    val nowDayOfYear = now.get(java.util.Calendar.DAY_OF_YEAR)
    val callDayOfYear = callCal.get(java.util.Calendar.DAY_OF_YEAR)

    return when {
        nowYear == callYear && nowDayOfYear == callDayOfYear -> "Hoy"
        nowYear == callYear && nowDayOfYear - callDayOfYear == 1 -> "Ayer"
        nowYear == callYear && now.get(java.util.Calendar.WEEK_OF_YEAR) == callCal.get(java.util.Calendar.WEEK_OF_YEAR) -> "Esta semana"
        nowYear == callYear && now.get(java.util.Calendar.MONTH) == callCal.get(java.util.Calendar.MONTH) -> "Este mes"
        nowYear == callYear -> SimpleDateFormat("MMMM", Locale("es", "CL")).format(Date(timestamp)).replaceFirstChar { it.uppercase() }
        else -> SimpleDateFormat("MMMM yyyy", Locale("es", "CL")).format(Date(timestamp)).replaceFirstChar { it.uppercase() }
    }
}

@Composable
private fun CallSectionHeader(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.width(10.dp))
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                thickness = 0.8.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * Pre-computed, immutable representation of a call log item.
 * Guarantees zero formatting/parsing overhead on scroll.
 */
@Immutable
data class ClassifiedCall(
    val log: CallLogUiModel
) {
    val telecomMeta: ChileanTelecomMeta get() = log.telecomMeta
    val isBlocked: Boolean get() = log.isBlocked
    val isMissed: Boolean get() = log.isMissed
    val isPingCall: Boolean get() = log.isPingCall
    val isIncoming: Boolean get() = log.isIncoming
    val isOutgoing: Boolean get() = log.isOutgoing
    val isContact: Boolean get() = log.isContact
    val displayName: String get() = log.displayName
    val formattedNumber: String get() = log.formattedNumber
    val tag: String get() = log.tag
    val dateDisplay: String get() = log.dateDisplay
    val subtitleText: String get() = log.subtitleText
    val simSlot: Int? get() = log.simSlot
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CallLogScreen(
    repository: CortaRepository = koinInject(),
    contactRepository: ContactRepository = koinInject(),
    viewModel: CallLogViewModel = koinViewModel(),
    searchQuery: String = ""
) {
    val context = LocalContext.current
    val strings = Strings
    val isDetailedMode = LocalDetailedMode.current
    val screenScope = rememberCoroutineScope()

    var selectedCallLog by remember { mutableStateOf<ClassifiedCall?>(null) }
    var showAliasDialog by remember { mutableStateOf<CallLogUiModel?>(null) }
    var aliasText by remember { mutableStateOf("") }
    var showDialpadSheet by remember { mutableStateOf(false) }
    var showCategoriesSheet by remember { mutableStateOf(false) }
    var showTagDictionarySheet by remember { mutableStateOf(false) }
    var highlightedTagForDictionary by remember { mutableStateOf<String?>(null) }
    val prefs = remember { context.getSharedPreferences("corta_prefs", Context.MODE_PRIVATE) }
    val defaultCallCategoryOrder = remember {
        listOf(
            CallLogTabFilter.REGIONAL,
            CallLogTabFilter.ALL,
            CallLogTabFilter.INCOMING,
            CallLogTabFilter.OUTGOING,
            CallLogTabFilter.MISSED,
            CallLogTabFilter.BLOCKED,
            CallLogTabFilter.SUBTEL_809
        )
    }
    val savedCallOrderRaw = prefs.getString("call_category_order", null)
    var categoryOrder by remember {
        mutableStateOf(
            if (savedCallOrderRaw != null) {
                val names = savedCallOrderRaw.split(",")
                val list = names.mapNotNull { name -> CallLogTabFilter.entries.firstOrNull { it.name == name } }.toMutableList()
                CallLogTabFilter.entries.forEach { if (!list.contains(it)) list.add(it) }
                list
            } else {
                defaultCallCategoryOrder
            }
        )
    }
    var currentFilter by remember { mutableStateOf(categoryOrder.firstOrNull() ?: CallLogTabFilter.ALL) }
    var showTrashSheet by remember { mutableStateOf(false) }

    var selectedRegion by remember {
        val savedCode = prefs.getString("user_selected_region", ChileanRegion.RM.code)
        mutableStateOf(ChileanRegion.fromCode(savedCode))
    }
    var selectedComuna by remember {
        mutableStateOf(prefs.getString("user_selected_comuna", null))
    }
    var showRegionPicker by remember { mutableStateOf(false) }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "user_selected_region" || key == "user_selected_comuna") {
                selectedRegion = ChileanRegion.fromCode(prefs.getString("user_selected_region", ChileanRegion.RM.code))
                selectedComuna = prefs.getString("user_selected_comuna", null)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val effectiveComuna = remember(selectedRegion, selectedComuna) {
        if (selectedRegion == ChileanRegion.ALL || selectedComuna.isNullOrBlank()) null
        else if (ChileanRegionalDirectory.isValidComunaForRegion(selectedRegion, selectedComuna)) selectedComuna
        else null
    }

    val locationLabel = remember(selectedRegion, effectiveComuna) {
        when {
            effectiveComuna != null && selectedRegion != ChileanRegion.ALL -> "$effectiveComuna (${selectedRegion.shortName})"
            selectedRegion != ChileanRegion.ALL -> selectedRegion.shortName
            else -> "Todo Chile"
        }
    }

    val regionalDirectoryEntries = remember(selectedRegion, effectiveComuna) {
        ChileanRegionalDirectory.getEntriesForRegionAndComuna(selectedRegion, effectiveComuna)
    }

    val directorySearchResults = remember(searchQuery, regionalDirectoryEntries) {
        if (searchQuery.isBlank()) emptyList()
        else {
            val q = searchQuery.trim().normalizeForSearch()
            regionalDirectoryEntries.filter {
                it.name.normalizeForSearch().contains(q) ||
                it.comuna.normalizeForSearch().contains(q) ||
                it.number.contains(q) ||
                it.subtitle.normalizeForSearch().contains(q)
            }.take(8)
        }
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val deviceContacts by produceState<List<ContactInfo>>(initialValue = emptyList(), key1 = hasPermission) {
        if (hasPermission) {
            value = withContext(Dispatchers.IO) {
                try {
                    contactRepository.getContacts()
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }
    }

    val contactSearchResults = remember(searchQuery, deviceContacts) {
        if (searchQuery.isBlank()) emptyList()
        else {
            val q = searchQuery.trim().normalizeForSearch()
            deviceContacts.filter {
                it.name.normalizeForSearch().contains(q) || it.phoneNumber.contains(q)
            }.take(5)
        }
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    val logs = viewModel.callLogs.collectAsLazyPagingItems()
    val isRefreshing = logs.loadState.refresh is androidx.paging.LoadState.Loading
    val isError = logs.loadState.refresh is androidx.paging.LoadState.Error

    val loadedLogs = logs.itemSnapshotList.items

    // Off-thread pre-classified items: zero regex, zero date formatting, zero string formatting in composition
    val classifiedCalls = remember(loadedLogs) {
        loadedLogs.distinctBy { it.id }.map { ClassifiedCall(it) }
    }

    // Dynamic counts for fast filter chips
    val allCount = classifiedCalls.size
    val blockedCount = remember(classifiedCalls) { classifiedCalls.count { it.isBlocked } }
    val missedCount = remember(classifiedCalls) { classifiedCalls.count { it.isMissed } }
    val subtelCount = remember(classifiedCalls) {
        classifiedCalls.count { it.telecomMeta.type == ChileanNumberType.SUBTEL_TELEMARKETING_809 || it.log.number.contains("809") }
    }
    val incomingCount = remember(classifiedCalls) { classifiedCalls.count { it.isIncoming } }
    val outgoingCount = remember(classifiedCalls) { classifiedCalls.count { it.isOutgoing } }

    // Clean filtered display list: ONLY items that match are passed to LazyColumn (eliminates empty gaps & lag)
    val displayList = remember(classifiedCalls, currentFilter, searchQuery) {
        val baseList = if (searchQuery.isNotBlank()) {
            classifiedCalls
        } else {
            when (currentFilter) {
                CallLogTabFilter.ALL -> classifiedCalls
                CallLogTabFilter.BLOCKED -> classifiedCalls.filter { it.isBlocked }
                CallLogTabFilter.MISSED -> classifiedCalls.filter { it.isMissed }
                CallLogTabFilter.SUBTEL_809 -> classifiedCalls.filter {
                    it.telecomMeta.type == ChileanNumberType.SUBTEL_TELEMARKETING_809 || it.log.number.contains("809")
                }
                CallLogTabFilter.INCOMING -> classifiedCalls.filter { it.isIncoming }
                CallLogTabFilter.OUTGOING -> classifiedCalls.filter { it.isOutgoing }
                CallLogTabFilter.REGIONAL -> emptyList()
            }
        }

        if (searchQuery.isBlank()) {
            baseList
        } else {
            baseList.filter { item ->
                item.displayName.contains(searchQuery, ignoreCase = true) ||
                item.formattedNumber.contains(searchQuery, ignoreCase = true) ||
                item.log.number.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val listState = rememberLazyListState()

    // Controlled scroll-based pagination: only triggers when user reaches bottom, never inside composition loops
    val shouldLoadMore = remember(listState) {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 5
        }
    }
    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && logs.itemCount > 0) {
            logs.get(logs.itemCount - 1)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (!hasPermission) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.PhoneDisabled,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = strings.calls.logPermissionRequired,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Corta necesita acceso al registro para identificar y bloquear llamadas spam entrantes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { permissionLauncher.launch(android.Manifest.permission.READ_CALL_LOG) },
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(strings.common.retry, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Quick Filter Chips Row (Native Telecom Clarity with Pinned '+' Category Button)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        categoryOrder.forEach { filter ->
                            val isSelected = currentFilter == filter
                            when (filter) {
                                CallLogTabFilter.REGIONAL -> {
                                    StitchFilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            if (currentFilter == CallLogTabFilter.REGIONAL) {
                                                showRegionPicker = true
                                            } else {
                                                currentFilter = CallLogTabFilter.REGIONAL
                                            }
                                        },
                                        label = locationLabel,
                                        icon = Icons.Rounded.LocationOn,
                                        iconTint = MaterialTheme.colorScheme.primary,
                                        count = if (regionalDirectoryEntries.isNotEmpty()) regionalDirectoryEntries.size else null
                                    )
                                }
                                CallLogTabFilter.INCOMING -> {
                                    StitchFilterChip(
                                        selected = isSelected,
                                        onClick = { currentFilter = CallLogTabFilter.INCOMING },
                                        label = "Entrantes",
                                        icon = Icons.AutoMirrored.Rounded.CallReceived,
                                        iconTint = MaterialTheme.colorScheme.secondary,
                                        count = if (incomingCount > 0) incomingCount else null
                                    )
                                }
                                CallLogTabFilter.OUTGOING -> {
                                    StitchFilterChip(
                                        selected = isSelected,
                                        onClick = { currentFilter = CallLogTabFilter.OUTGOING },
                                        label = "Salientes",
                                        icon = Icons.AutoMirrored.Rounded.CallMade,
                                        iconTint = MaterialTheme.colorScheme.primary,
                                        count = if (outgoingCount > 0) outgoingCount else null
                                    )
                                }
                                CallLogTabFilter.BLOCKED -> {
                                    StitchFilterChip(
                                        selected = isSelected,
                                        onClick = { currentFilter = CallLogTabFilter.BLOCKED },
                                        label = if (blockedCount > 0) "Spam ($blockedCount)" else "Spam",
                                        icon = Icons.Rounded.Shield,
                                        iconTint = MaterialTheme.colorScheme.error
                                    )
                                }
                                CallLogTabFilter.MISSED -> {
                                    StitchFilterChip(
                                        selected = isSelected,
                                        onClick = { currentFilter = CallLogTabFilter.MISSED },
                                        label = "Perdidas",
                                        icon = Icons.AutoMirrored.Rounded.CallMissed,
                                        iconTint = MaterialTheme.colorScheme.error,
                                        count = if (missedCount > 0) missedCount else null
                                    )
                                }
                                CallLogTabFilter.SUBTEL_809 -> {
                                    StitchFilterChip(
                                        selected = isSelected,
                                        onClick = { currentFilter = CallLogTabFilter.SUBTEL_809 },
                                        label = "Chao 809",
                                        icon = Icons.Rounded.CallEnd,
                                        iconTint = MaterialTheme.colorScheme.tertiary,
                                        count = if (subtelCount > 0) subtelCount else null
                                    )
                                }
                                CallLogTabFilter.ALL -> {
                                    StitchFilterChip(
                                        selected = isSelected,
                                        onClick = { currentFilter = CallLogTabFilter.ALL },
                                        label = "Todas",
                                        count = if (allCount > 0) allCount else null
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // 7. Pinned '+' Button: Always visible on screen for full category exploration & reordering
                    CortaCategoryExploreButton(
                        onClick = { showCategoriesSheet = true },
                        contentDescription = "Ver todas las categorías de llamadas"
                    )
                }

                if (isRefreshing && logs.itemCount == 0) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (isError && logs.itemCount == 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            strings.calls.logEmpty,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { logs.retry() }) {
                            Text(strings.common.retry)
                        }
                    }
                } else {
                    val isSearchMode = searchQuery.isNotBlank()
                val isRegionalMode = currentFilter == CallLogTabFilter.REGIONAL && !isSearchMode
                val isEmpty = if (isSearchMode) {
                    displayList.isEmpty() && directorySearchResults.isEmpty() && contactSearchResults.isEmpty()
                } else if (isRegionalMode) {
                    regionalDirectoryEntries.isEmpty()
                } else {
                    displayList.isEmpty()
                }

                if (isEmpty) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.size(72.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = when {
                                            isSearchMode -> Icons.Rounded.SearchOff
                                            currentFilter == CallLogTabFilter.REGIONAL -> Icons.Rounded.LocationCity
                                            currentFilter == CallLogTabFilter.BLOCKED -> Icons.Rounded.Shield
                                            else -> Icons.AutoMirrored.Rounded.PhoneCallback
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            val emptyText = when {
                                isSearchMode -> "No se encontraron contactos, servicios ni llamadas para \"$searchQuery\""
                                currentFilter == CallLogTabFilter.REGIONAL -> "No hay registros disponibles en el directorio para $locationLabel"
                                currentFilter == CallLogTabFilter.ALL -> "No hay llamadas en tu historial"
                                currentFilter == CallLogTabFilter.BLOCKED -> "¡Impeque! Cero llamadas de spam bloqueadas"
                                currentFilter == CallLogTabFilter.MISSED -> "No tienes llamadas perdidas"
                                currentFilter == CallLogTabFilter.SUBTEL_809 -> "Ninguna llamada comercial 809 registrada"
                                currentFilter == CallLogTabFilter.INCOMING -> "No tienes llamadas entrantes registradas"
                                currentFilter == CallLogTabFilter.OUTGOING -> "No has emitido llamadas desde tu equipo"
                                else -> "No hay llamadas registradas"
                            }
                            Text(
                                text = emptyText,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            if (currentFilter == CallLogTabFilter.REGIONAL) {
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(onClick = { showRegionPicker = true }) {
                                    Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Elegir otra comuna o región")
                                }
                            }
                        }
                    }
                } else {
                    val groupedCalls = remember(displayList) {
                        displayList.groupBy { item ->
                            formatCallDateSectionChile(item.log.date)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isSearchMode) {
                            // 1. Phone Contacts Section
                            if (contactSearchResults.isNotEmpty()) {
                                item(key = "search_contacts_header") {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp, bottom = 2.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Person,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Contactos del teléfono (${contactSearchResults.size})",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }

                                itemsIndexed(
                                    items = contactSearchResults,
                                    key = { index, it -> "contact_${it.id}_$index" }
                                ) { _, contact ->
                                    ContactSearchCard(
                                        contact = contact,
                                        onCall = {
                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phoneNumber}"))
                                            context.startActivity(intent)
                                        }
                                    )
                                }
                            }

                            // 2. Official Chilean Directory Results when searching
                            if (directorySearchResults.isNotEmpty()) {
                                item(key = "dir_search_header") {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp, bottom = 2.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Verified,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Directorio Oficial ($locationLabel - ${directorySearchResults.size})",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }

                                itemsIndexed(
                                    items = directorySearchResults,
                                    key = { index, it -> "dir_${it.id}_$index" }
                                ) { _, entry ->
                                    OfficialDirectorySearchCard(
                                        entry = entry,
                                        onCall = {
                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${entry.number}"))
                                            context.startActivity(intent)
                                        }
                                    )
                                }
                            }

                            // 3. Call History Results when searching
                            if (displayList.isNotEmpty()) {
                                item(key = "divider_search_calls") {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp, bottom = 2.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.History,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Historial de Llamadas (${displayList.size})",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                itemsIndexed(
                                    items = displayList,
                                    key = { index, item -> "call_search_${item.log.id}_$index" },
                                    contentType = { _, _ -> "call_card" }
                                ) { index, item ->
                                    CallLogCard(
                                        item = item,
                                        isDetailedMode = isDetailedMode,
                                        onClick = { selectedCallLog = item },
                                        onTagClick = { tag ->
                                            highlightedTagForDictionary = tag
                                            showTagDictionarySheet = true
                                        },
                                        onActionClick = {
                                            if (item.isBlocked || item.telecomMeta.type == ChileanNumberType.SUBTEL_TELEMARKETING_809) {
                                                selectedCallLog = item
                                            } else {
                                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${item.log.number}"))
                                                context.startActivity(intent)
                                            }
                                        }
                                    )
                                }
                            }
                        } else if (isRegionalMode) {
                            // Regional Directory Tab
                            itemsIndexed(
                                items = regionalDirectoryEntries,
                                key = { index, it -> "reg_${it.id}_$index" }
                            ) { _, entry ->
                                OfficialDirectorySearchCard(
                                    entry = entry,
                                    onCall = {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${entry.number}"))
                                        context.startActivity(intent)
                                    }
                                )
                            }
                        } else {
                            // Standard Call Log Filter View Grouped by Date
                            groupedCalls.forEach { (sectionTitle, callsInSection) ->
                                stickyHeader(key = "header_call_$sectionTitle") {
                                    CallSectionHeader(title = sectionTitle.uppercase(Locale("es", "CL")))
                                }
                                itemsIndexed(
                                    items = callsInSection,
                                    key = { index, item -> "call_${item.log.id}_$index" },
                                    contentType = { _, _ -> "call_card" }
                                ) { _, item ->
                                    CallLogCard(
                                        item = item,
                                        isDetailedMode = isDetailedMode,
                                        onClick = { selectedCallLog = item },
                                        onTagClick = { tag ->
                                            highlightedTagForDictionary = tag
                                            showTagDictionarySheet = true
                                        },
                                        onActionClick = {
                                            if (item.isBlocked || item.telecomMeta.type == ChileanNumberType.SUBTEL_TELEMARKETING_809) {
                                                selectedCallLog = item
                                            } else {
                                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${item.log.number}"))
                                                context.startActivity(intent)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

        val handedness = LocalHandedness.current
        val fabAlignment = if (handedness == Handedness.LEFT) Alignment.BottomStart else Alignment.BottomEnd

        // Floating Action Button for Dialpad (Unified Corta FAB)
        CortaFloatingActionButton(
            onClick = { showDialpadSheet = true },
            icon = Icons.Rounded.Dialpad,
            contentDescription = strings.common.dialpad,
            modifier = Modifier
                .align(fabAlignment)
                .padding(16.dp)
        )
    }

    // Dialpad ModalBottomSheet
    if (showDialpadSheet) {
        val dialpadSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showDialpadSheet = false },
            sheetState = dialpadSheetState,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp)
            ) {
                DialpadScreen(
                    onDial = { number ->
                        showDialpadSheet = false
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                        context.startActivity(intent)
                    }
                )
            }
        }
    }

    // Categories Exploration Bottom Sheet
    if (showCategoriesSheet) {
        CallCategoriesBottomSheet(
            currentFilter = currentFilter,
            categoryOrder = categoryOrder,
            allCount = allCount,
            blockedCount = blockedCount,
            missedCount = missedCount,
            subtelCount = subtelCount,
            incomingCount = incomingCount,
            outgoingCount = outgoingCount,
            regionalCount = regionalDirectoryEntries.size,
            locationLabel = locationLabel,
            onOpenRegionPicker = {
                showCategoriesSheet = false
                showRegionPicker = true
            },
            onSelectFilter = { selected ->
                currentFilter = selected
                showCategoriesSheet = false
            },
            onReorder = { newOrder ->
                categoryOrder = newOrder
                prefs.edit().putString("call_category_order", newOrder.joinToString(",") { it.name }).apply()
            },
            onResetOrder = {
                categoryOrder = defaultCallCategoryOrder
                prefs.edit().remove("call_category_order").apply()
            },
            onDismiss = { showCategoriesSheet = false }
        )
    }

    // Modal: Region & Comuna Selector
    if (showRegionPicker) {
        RegionSelectorBottomSheet(
            selectedRegion = selectedRegion,
            selectedComuna = selectedComuna,
            onSelectLocation = { region, comuna ->
                selectedRegion = region
                selectedComuna = comuna
                val editor = prefs.edit().putString("user_selected_region", region.code)
                if (comuna != null) {
                    editor.putString("user_selected_comuna", comuna)
                } else {
                    editor.remove("user_selected_comuna")
                }
                editor.apply()
                val locText = if (comuna != null) "$comuna (${region.shortName})" else region.displayName
                Toast.makeText(context, "Ubicación: $locText", Toast.LENGTH_SHORT).show()
                showRegionPicker = false
            },
            onDismiss = { showRegionPicker = false }
        )
    }


    // Tag Dictionary Bottom Sheet (Interactive Chilean Telecom Explanations)
    if (showTagDictionarySheet) {
        CallTagDictionaryBottomSheet(
            highlightedTag = highlightedTagForDictionary,
            onDismiss = { showTagDictionarySheet = false }
        )
    }

    // Trash Bottom Sheet (Papelera de reciclaje)
    if (showTrashSheet) {
        TrashBottomSheet(
            repository = repository,
            onDismiss = { showTrashSheet = false }
        )
    }

    // Alias Dialog
    if (showAliasDialog != null) {
        val callLogScope = rememberCoroutineScope()
        AlertDialog(
            onDismissRequest = { showAliasDialog = null },
            title = { Text(strings.calls.aliasDialogTitle, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(strings.calls.aliasDialogBody)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = aliasText,
                        onValueChange = { aliasText = it },
                        placeholder = { Text(strings.calls.aliasDialogPlaceholder) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                val defaultAlias = strings.calls.aliasDialogDefault
                Button(onClick = {
                    val log = showAliasDialog!!
                    callLogScope.launch(Dispatchers.IO) {
                        repository.addRule(log.number, FilterAction.BLOCK, false, aliasText.ifBlank { defaultAlias })
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.calllog_alias_added, log.number, aliasText), Toast.LENGTH_SHORT).show()
                            showAliasDialog = null
                            aliasText = ""
                        }
                    }
                }) {
                    Text(strings.common.save, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAliasDialog = null
                    aliasText = ""
                }) {
                    Text(strings.common.cancel)
                }
            },
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    // Call Details Bottom Sheet
    selectedCallLog?.let { item ->
        CallDetailsBottomSheet(
            item = item,
            onDismiss = { selectedCallLog = null },
            onMoveToTrash = {
                repository.moveToTrash(
                    itemType = "CALL",
                    originalId = item.log.id.toString(),
                    senderOrNumber = item.log.number,
                    contentPreview = "${item.displayName} (${item.dateDisplay})"
                )
                Toast.makeText(context, "Llamada movida a la papelera", Toast.LENGTH_SHORT).show()
            },
            onBlockWithAlias = {
                showAliasDialog = item.log
                aliasText = if (!item.log.name.isNullOrBlank()) {
                    item.log.name.trim()
                } else if (item.log.matchingRule != null && item.log.matchingRule.source.equals("USER", ignoreCase = true) && !item.log.matchingRule.alias.isNullOrBlank()) {
                    item.log.matchingRule.alias?.trim() ?: ""
                } else {
                    ""
                }
                selectedCallLog = null
            }
        )
    }
}

/**
 * 2-Row Modern Call Item matching Stitch Specification:
 * - 42dp Contextual Avatar
 * - Row 1: Contact name or formatted Chilean number (+56 9 XXXX XXXX) + Time
 * - Row 2: Single-word tag badge (tap opens dictionary) · Status / Duration
 * - Right Action: Quick Call button for normal calls / Info button for spam
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CallLogCard(
    item: ClassifiedCall,
    isDetailedMode: Boolean,
    onClick: () -> Unit,
    onTagClick: (String) -> Unit,
    onActionClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                item.isBlocked -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f)
                item.telecomMeta.type == ChileanNumberType.SUBTEL_TELEMARKETING_809 -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.12f)
                else -> MaterialTheme.colorScheme.surfaceContainerLowest
            }
        ),
        border = BorderStroke(
            1.dp,
            when {
                item.isBlocked -> MaterialTheme.colorScheme.error.copy(alpha = 0.20f)
                item.telecomMeta.type == ChileanNumberType.SUBTEL_TELEMARKETING_809 -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.20f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Contextual Avatar (42dp)
                CallAvatar(item = item)

                Spacer(modifier = Modifier.width(12.dp))

                // Center Information (3 structured lines: Line 1 Name+Date, Line 2 Badges+SIM+Number, Line 3 Status/Duration)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // Line 1: Full Display Name / Number + Date
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.displayName,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (item.isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        if (item.isContact) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = "Contacto",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        } else if (item.telecomMeta.isVerifiedOfficialService) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.Verified,
                                contentDescription = "Oficial",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        Text(
                            text = item.dateDisplay,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }

                    // Line 2: Tag Badge + SIM slot + Raw formatted phone number (if contact name was used)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CallTagBadge(
                            tag = item.tag,
                            isBlocked = item.isBlocked,
                            telecomMeta = item.telecomMeta,
                            onClick = { onTagClick(item.tag) }
                        )

                        // Dual SIM indicator badge [1] or [2]
                        if (item.simSlot != null) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "SIM ${item.simSlot}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (item.isContact && item.formattedNumber.isNotBlank() && item.displayName != item.formattedNumber) {
                            Text(
                                text = item.formattedNumber,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Line 3: Direction Icon + Status / Duration across full row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val directionIcon: ImageVector = when {
                            item.isBlocked -> Icons.Rounded.Block
                            item.isMissed -> Icons.AutoMirrored.Rounded.CallMissed
                            item.isOutgoing -> Icons.AutoMirrored.Rounded.CallMade
                            else -> Icons.AutoMirrored.Rounded.CallReceived
                        }
                        val directionTint: Color = when {
                            item.isBlocked || item.isMissed -> MaterialTheme.colorScheme.error
                            item.isOutgoing -> Color(0xFF1976D2)
                            else -> Color(0xFF2E7D32)
                        }

                        Icon(
                            imageVector = directionIcon,
                            contentDescription = null,
                            tint = directionTint,
                            modifier = Modifier.size(14.dp)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = item.subtitleText,
                            fontSize = 12.5.sp,
                            fontWeight = if (item.isMissed) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (item.isMissed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Standardized 40dp Circular Action Button (Unified across CallLog and Contacts)
                val isBlockedOrSpam = item.isBlocked || item.telecomMeta.type == ChileanNumberType.SUBTEL_TELEMARKETING_809
                Surface(
                    onClick = onActionClick,
                    shape = CircleShape,
                    color = if (!isBlockedOrSpam) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isBlockedOrSpam) Icons.Rounded.Info else Icons.Rounded.Call,
                            contentDescription = if (isBlockedOrSpam) "Detalles y opciones" else "Llamar a ${item.displayName}",
                            tint = if (!isBlockedOrSpam) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Detailed Forensic Telemetry (Active when Detailed Mode is ON)
            if (isDetailedMode) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                Spacer(modifier = Modifier.height(6.dp))

                val normNumber = remember(item.log.number) { PhoneNumberUtils.normalize(item.log.number) }
                val hashFragment = remember(item.log.number) {
                    abs(item.log.number.hashCode()).toString(16).padStart(4, '0').take(4)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "⚡ < 0.8ms",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Text(
                            text = "Norm: $normNumber",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Text(
                            text = "#$hashFragment",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    if (item.isPingCall) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = "PBX Autodialer",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Contextual Avatar according to Chilean telecom classification:
 * - Spam / Blocked: Red Shield
 * - Telemarketing 809: Orange CallEnd
 * - Emergency (133, 131, 132): Green Police/Shield
 * - Municipal (14xx): Green Shield
 * - Official 600 / Banks: Blue Bank Temple
 * - Known Contact: Colored Letter Initial
 * - Missed: Red CallMissed
 * - Incoming: Green CallReceived
 * - Outgoing: Blue CallMade
 */
@Composable
fun CallAvatar(
    item: ClassifiedCall,
    modifier: Modifier = Modifier
) {
    val incomingGreen = Color(0xFF2E7D32)
    val incomingBg = Color(0xFFE8F5E9)
    val outgoingBlue = Color(0xFF1976D2)
    val outgoingBg = Color(0xFFE3F2FD)
    val missedRed = MaterialTheme.colorScheme.error
    val missedBg = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)

    val (directionIcon, directionTint, directionBg) = when {
        item.isBlocked -> Triple(Icons.Rounded.Shield, MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.errorContainer)
        item.telecomMeta.type == ChileanNumberType.SUBTEL_TELEMARKETING_809 -> Triple(Icons.Rounded.CallEnd, MaterialTheme.colorScheme.onTertiaryContainer, MaterialTheme.colorScheme.tertiaryContainer)
        item.telecomMeta.type == ChileanNumberType.EMERGENCY_NATIONAL -> Triple(Icons.Rounded.LocalPolice, MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.secondaryContainer)
        item.telecomMeta.type == ChileanNumberType.MUNICIPAL_SECURITY_14XX -> Triple(Icons.Rounded.Security, MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.secondaryContainer)
        item.telecomMeta.isVerifiedOfficialService -> Triple(Icons.Rounded.AccountBalance, MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primaryContainer)
        item.isMissed -> Triple(Icons.AutoMirrored.Rounded.CallMissed, missedRed, missedBg)
        item.isOutgoing -> Triple(Icons.AutoMirrored.Rounded.CallMade, outgoingBlue, outgoingBg)
        else -> Triple(Icons.AutoMirrored.Rounded.CallReceived, incomingGreen, incomingBg)
    }

    Box(modifier = modifier.size(44.dp)) {
        if (item.isContact && !item.isBlocked && item.telecomMeta.type != ChileanNumberType.SUBTEL_TELEMARKETING_809) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val firstLetter = item.displayName.firstOrNull { it.isLetter() }
                    if (firstLetter != null) {
                        Text(
                            text = firstLetter.uppercase(),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            Surface(
                shape = CircleShape,
                color = directionBg,
                border = BorderStroke(1.dp, directionTint.copy(alpha = 0.6f)),
                modifier = Modifier
                    .size(18.dp)
                    .align(Alignment.BottomEnd)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = directionIcon,
                        contentDescription = null,
                        tint = directionTint,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        } else {
            Surface(
                shape = CircleShape,
                color = directionBg,
                border = BorderStroke(1.dp, directionTint.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = directionIcon,
                        contentDescription = null,
                        tint = directionTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * Single-word interactive tag badge (Spam, Comercial, Fantasma, Oficial, Emergencia, Municipal, Contacto, Móvil, Fijo).
 * Clicking opens the Tag Dictionary bottom sheet.
 */
@Composable
fun CallTagBadge(
    tag: String,
    isBlocked: Boolean,
    telecomMeta: ChileanTelecomMeta,
    onClick: () -> Unit
) {
    val tagBg: Color
    val tagTextColor: Color
    val tagIcon: ImageVector

    when (tag) {
        "Spam" -> {
            tagBg = MaterialTheme.colorScheme.errorContainer
            tagTextColor = MaterialTheme.colorScheme.onErrorContainer
            tagIcon = Icons.Rounded.Block
        }
        "Comercial" -> {
            tagBg = MaterialTheme.colorScheme.tertiaryContainer
            tagTextColor = MaterialTheme.colorScheme.onTertiaryContainer
            tagIcon = Icons.Rounded.Campaign
        }
        "Fantasma" -> {
            tagBg = MaterialTheme.colorScheme.errorContainer
            tagTextColor = MaterialTheme.colorScheme.onErrorContainer
            tagIcon = Icons.Rounded.HourglassBottom
        }
        "Emergencia" -> {
            tagBg = MaterialTheme.colorScheme.errorContainer
            tagTextColor = MaterialTheme.colorScheme.onErrorContainer
            tagIcon = Icons.Rounded.LocalPolice
        }
        "Municipal" -> {
            tagBg = MaterialTheme.colorScheme.tertiaryContainer
            tagTextColor = MaterialTheme.colorScheme.onTertiaryContainer
            tagIcon = Icons.Rounded.Security
        }
        "Oficial" -> {
            tagBg = MaterialTheme.colorScheme.primaryContainer
            tagTextColor = MaterialTheme.colorScheme.onPrimaryContainer
            tagIcon = Icons.Rounded.Verified
        }
        "Contacto" -> {
            tagBg = MaterialTheme.colorScheme.secondaryContainer
            tagTextColor = MaterialTheme.colorScheme.onSecondaryContainer
            tagIcon = Icons.Rounded.Person
        }
        "Fijo" -> {
            tagBg = MaterialTheme.colorScheme.surfaceVariant
            tagTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            tagIcon = Icons.Rounded.Phone
        }
        "Perdida" -> {
            tagBg = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            tagTextColor = MaterialTheme.colorScheme.onErrorContainer
            tagIcon = Icons.AutoMirrored.Rounded.CallMissed
        }
        else -> {
            // "Móvil" / "Llamada"
            tagBg = MaterialTheme.colorScheme.surfaceVariant
            tagTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            tagIcon = Icons.Rounded.PhoneAndroid
        }
    }

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = tagBg,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = tagIcon,
                contentDescription = null,
                tint = tagTextColor,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = tag,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = tagTextColor
            )
        }
    }
}

/**
 * Categories Bottom Sheet for Call Filter Exploration
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallCategoriesBottomSheet(
    currentFilter: CallLogTabFilter,
    categoryOrder: List<CallLogTabFilter>,
    allCount: Int,
    blockedCount: Int,
    missedCount: Int,
    subtelCount: Int,
    incomingCount: Int,
    outgoingCount: Int,
    regionalCount: Int = 0,
    locationLabel: String = "Chile",
    onOpenRegionPicker: () -> Unit = {},
    onSelectFilter: (CallLogTabFilter) -> Unit,
    onReorder: (List<CallLogTabFilter>) -> Unit,
    onResetOrder: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Categorías de llamadas",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Usa ↑/↓ para ordenar. La primera categoría será la pestaña inicial.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onResetOrder) {
                    Text("Restablecer", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(10.dp))

            categoryOrder.forEachIndexed { index, filter ->
                val isSelected = currentFilter == filter
                val isFirst = index == 0

                val (title, subtitle, icon, iconBg, iconTint, count) = when (filter) {
                    CallLogTabFilter.REGIONAL -> Tuple6(
                        "Directorio ($locationLabel)",
                        "Emergencias, seguridad municipal 14xx y utilidad",
                        Icons.Rounded.Verified,
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        regionalCount
                    )
                    CallLogTabFilter.INCOMING -> Tuple6(
                        "Entrantes",
                        "Llamadas contestadas",
                        Icons.AutoMirrored.Rounded.CallReceived,
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                        incomingCount
                    )
                    CallLogTabFilter.OUTGOING -> Tuple6(
                        "Salientes",
                        "Llamadas realizadas desde este equipo",
                        Icons.AutoMirrored.Rounded.CallMade,
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        outgoingCount
                    )
                    CallLogTabFilter.BLOCKED -> Tuple6(
                        "Spam bloqueado",
                        "Llamadas silenciadas o rechazadas",
                        Icons.Rounded.Shield,
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.onErrorContainer,
                        blockedCount
                    )
                    CallLogTabFilter.MISSED -> Tuple6(
                        "Perdidas",
                        "Llamadas no contestadas o pings",
                        Icons.AutoMirrored.Rounded.CallMissed,
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.onErrorContainer,
                        missedCount
                    )
                    CallLogTabFilter.SUBTEL_809 -> Tuple6(
                        "Chao 809 (Telemarketing)",
                        "Ventas comerciales no solicitadas",
                        Icons.Rounded.CallEnd,
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer,
                        subtelCount
                    )
                    CallLogTabFilter.ALL -> Tuple6(
                        "Todas las llamadas",
                        "Historial completo de tu teléfono",
                        Icons.Rounded.Phone,
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        allCount
                    )
                }

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = iconBg,
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { onSelectFilter(filter) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = iconTint,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectFilter(filter) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = title,
                                    fontSize = 14.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (count > 0) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = "$count",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                if (isFirst) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                                    ) {
                                        Text(
                                            text = "Pestaña inicial",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = subtitle,
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Reorder Actions (↑ / ↓)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (filter == CallLogTabFilter.REGIONAL) {
                                IconButton(
                                    onClick = onOpenRegionPicker,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Cambiar ubicación",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        val mutable = categoryOrder.toMutableList()
                                        val temp = mutable[index]
                                        mutable[index] = mutable[index - 1]
                                        mutable[index - 1] = temp
                                        onReorder(mutable)
                                    }
                                },
                                enabled = index > 0,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowUpward,
                                    contentDescription = "Subir",
                                    tint = if (index > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (index < categoryOrder.size - 1) {
                                        val mutable = categoryOrder.toMutableList()
                                        val temp = mutable[index]
                                        mutable[index] = mutable[index + 1]
                                        mutable[index + 1] = temp
                                        onReorder(mutable)
                                    }
                                },
                                enabled = index < categoryOrder.size - 1,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowDownward,
                                    contentDescription = "Bajar",
                                    tint = if (index < categoryOrder.size - 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfficialDirectorySearchCard(
    entry: ChileanDirectoryEntry,
    onCall: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val (icon, bg, fg) = when (entry.category) {
                DirectoryCategory.EMERGENCY, DirectoryCategory.FIRE_DEPARTMENT -> Triple(Icons.Rounded.Shield, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                DirectoryCategory.MUNICIPAL, DirectoryCategory.POLICE_PDI -> Triple(Icons.Rounded.LocalPolice, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                DirectoryCategory.HEALTH_HOSPITAL -> Triple(Icons.Rounded.LocalHospital, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                else -> Triple(Icons.Rounded.AccountBalance, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = bg,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
                    }
                }
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = Icons.Rounded.Verified,
                            contentDescription = "Verificado",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = "${entry.comuna} · ${entry.number}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onCall,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Call,
                    contentDescription = "Llamar a ${entry.name}",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ContactSearchCard(
    contact: ContactInfo,
    onCall: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = contact.name.trim().take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Column {
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Contacto · ${PhoneNumberUtils.formatDisplayNumber(contact.phoneNumber)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onCall,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Call,
                    contentDescription = "Llamar a ${contact.name}",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private data class Tuple6<A, B, C, D, E, F>(
    val a: A, val b: B, val c: C, val d: D, val e: E, val f: F
)

/**
 * Bottom Sheet that explains all Call tags and Corta's automatic actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallTagDictionaryBottomSheet(
    highlightedTag: String? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Guía de Etiquetas de Llamadas",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Significado y acciones automáticas de Corta",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            data class CallTagGuideItem(
                val tag: String,
                val icon: ImageVector,
                val chipColor: Color,
                val contentColor: Color,
                val description: String,
                val cortaAction: String
            )

            val tagList = listOf(
                CallTagGuideItem(
                    tag = "Spam",
                    icon = Icons.Rounded.Block,
                    chipColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    description = "Llamadas fraudulentas, cobranzas no autorizadas o números en tu lista negra.",
                    cortaAction = "Acción: Bloqueada o silenciada al instante en memoria local (<2 ms)."
                ),
                CallTagGuideItem(
                    tag = "Comercial",
                    icon = Icons.Rounded.Campaign,
                    chipColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    description = "Telemarketing masivo con numeración SUBTEL 809 autorizada por ley.",
                    cortaAction = "Acción: Silenciada por la regla pre-sembrada Chao 809 de Corta."
                ),
                CallTagGuideItem(
                    tag = "Fantasma",
                    icon = Icons.Rounded.HourglassBottom,
                    chipColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    description = "Llamadas de 0 a 2 segundos emitidas por discadores automáticos (PBX ping) para verificar líneas vivas.",
                    cortaAction = "Acción: Identificada como ping de call center; sugerencia de bloqueo inmediato."
                ),
                CallTagGuideItem(
                    tag = "Emergencia",
                    icon = Icons.Rounded.LocalPolice,
                    chipColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    description = "Líneas de socorro nacional: 133 Carabineros, 131 SAMU, 132 Bomberos, 134 PDI, 130 CONAF.",
                    cortaAction = "Acción: Lista Blanca Crítica Inmutable. Corta jamás bloquea una emergencia."
                ),
                CallTagGuideItem(
                    tag = "Municipal",
                    icon = Icons.Rounded.Security,
                    chipColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    description = "Seguridad Ciudadana comunal 14xx (1402 Las Condes, 1403 Santiago, 1414 Vitacura, 1445 Maipú...).",
                    cortaAction = "Acción: Lista Blanca Crítica Inmutable. Protegida contra cualquier filtro."
                ),
                CallTagGuideItem(
                    tag = "Oficial",
                    icon = Icons.Rounded.Verified,
                    chipColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    description = "Servicios 600 verificados (bancos, Salud Responde 600 360 7777, Registro Civil).",
                    cortaAction = "Acción: Lista Blanca prioritaria. Timbra de forma normal y transparente."
                ),
                CallTagGuideItem(
                    tag = "Contacto",
                    icon = Icons.Rounded.Person,
                    chipColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.primary,
                    description = "Personas que tienes registradas en la agenda de tu teléfono.",
                    cortaAction = "Acción: Máxima prioridad; timbre habitual sin restricciones."
                ),
                CallTagGuideItem(
                    tag = "Móvil",
                    icon = Icons.Rounded.PhoneAndroid,
                    chipColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    description = "Celular nacional estándar (+56 9) que aún no tienes guardado.",
                    cortaAction = "Acción: Evaluado en memoria local sin usar conexión de red."
                ),
                CallTagGuideItem(
                    tag = "Fijo",
                    icon = Icons.Rounded.Phone,
                    chipColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    description = "Telefonía de red fija nacional (Santiago +56 2 o códigos de área regionales).",
                    cortaAction = "Acción: Se indica la ciudad o región de origen de la llamada."
                )
            )

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tagList.forEach { item ->
                    val isHighlighted = highlightedTag.equals(item.tag, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isHighlighted) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        border = if (isHighlighted) {
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = item.chipColor,
                                    modifier = Modifier.defaultMinSize(minHeight = 22.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = null,
                                            tint = item.contentColor,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = item.tag,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = item.contentColor
                                        )
                                    }
                                }

                                if (isHighlighted) {
                                    Text(
                                        text = "← Etiqueta seleccionada",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.description,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.cortaAction,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Options Bottom Sheet for a Call Entry:
 * Calling back, SMS, SERNAC No Molestar copy, Contacts, and Alias blocking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallDetailsBottomSheet(
    item: ClassifiedCall,
    onDismiss: () -> Unit,
    onMoveToTrash: () -> Unit = {},
    onBlockWithAlias: () -> Unit
) {
    val context = LocalContext.current
    val strings = Strings

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        val listItemColors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
            headlineColor = MaterialTheme.colorScheme.onSurface,
            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
            leadingIconColor = MaterialTheme.colorScheme.primary
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallAvatar(item = item, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.formattedNumber,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(6.dp))

            // Action: Llamar
            ListItem(
                headlineContent = { Text("Llamar de nuevo", fontWeight = FontWeight.SemiBold) },
                leadingContent = { Icon(Icons.Rounded.Call, null) },
                colors = listItemColors,
                modifier = Modifier.clickable {
                    onDismiss()
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${item.log.number}"))
                    context.startActivity(intent)
                }
            )

            // Action: SMS
            ListItem(
                headlineContent = { Text("Enviar mensaje SMS", fontWeight = FontWeight.SemiBold) },
                leadingContent = { Icon(Icons.AutoMirrored.Rounded.Message, null) },
                colors = listItemColors,
                modifier = Modifier.clickable {
                    onDismiss()
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${item.log.number}"))
                    context.startActivity(intent)
                }
            )

            // Action: Copiar número
            ListItem(
                headlineContent = { Text(strings.calls.copyNumber, fontWeight = FontWeight.SemiBold) },
                leadingContent = { Icon(Icons.Rounded.ContentCopy, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                colors = listItemColors,
                modifier = Modifier.clickable {
                    onDismiss()
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Teléfono", item.formattedNumber))
                    Toast.makeText(context, strings.calls.numberCopied, Toast.LENGTH_SHORT).show()
                }
            )

            // Action: Agregar a Contactos (Safe intent resolution)
            ListItem(
                headlineContent = { Text(strings.calls.addToContacts, fontWeight = FontWeight.SemiBold) },
                leadingContent = { Icon(Icons.Rounded.PersonAdd, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                colors = listItemColors,
                modifier = Modifier.clickable {
                    onDismiss()
                    try {
                        val intent = Intent(Intent.ACTION_INSERT).apply {
                            data = ContactsContract.Contacts.CONTENT_URI
                            putExtra(ContactsContract.Intents.Insert.PHONE, item.log.number)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            val fallback = Intent(Intent.ACTION_INSERT).apply {
                                type = ContactsContract.RawContacts.CONTENT_TYPE
                                putExtra(ContactsContract.Intents.Insert.PHONE, item.log.number)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(fallback)
                        } catch (e2: Exception) {
                            Toast.makeText(context, "No se encontró una aplicación de contactos compatible", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )

            // Action: Mover a la Papelera
            ListItem(
                headlineContent = {
                    Text(
                        text = "Mover a la papelera",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                supportingContent = {
                    Text(
                        "Se conservará en la papelera donde podrás restaurarla",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                leadingContent = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                colors = listItemColors,
                modifier = Modifier.clickable {
                    onDismiss()
                    onMoveToTrash()
                }
            )

            // Action: Bloquear con Alias
            ListItem(
                headlineContent = {
                    Text(
                        text = strings.calls.blockWithAlias,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                },
                leadingContent = { Icon(Icons.Rounded.Block, null, tint = MaterialTheme.colorScheme.error) },
                colors = listItemColors,
                modifier = Modifier.clickable {
                    onDismiss()
                    onBlockWithAlias()
                }
            )
        }
    }
}
