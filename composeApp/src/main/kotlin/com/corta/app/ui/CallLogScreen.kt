package com.corta.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.corta.app.ui.theme.LocalDetailedMode
import com.corta.app.ui.trash.TrashBottomSheet
import com.corta.db.FilterRule
import com.corta.domain.ChileanNumberType
import com.corta.domain.ChileanTelecomClassifier
import com.corta.domain.ChileanTelecomMeta
import com.corta.domain.CortaRepository
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
        else -> number
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
    OUTGOING("Salientes", "Llamadas emitidas desde tu equipo", false);
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
    val initialCallFilter = remember {
        val savedName = prefs.getString("default_call_tab", CallLogTabFilter.INCOMING.name)
        CallLogTabFilter.entries.firstOrNull { it.name == savedName } ?: CallLogTabFilter.INCOMING
    }
    var currentFilter by remember { mutableStateOf(initialCallFilter) }
    var showTrashSheet by remember { mutableStateOf(false) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
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
        loadedLogs.map { ClassifiedCall(it) }
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
        val baseList = when (currentFilter) {
            CallLogTabFilter.ALL -> classifiedCalls
            CallLogTabFilter.BLOCKED -> classifiedCalls.filter { it.isBlocked }
            CallLogTabFilter.MISSED -> classifiedCalls.filter { it.isMissed }
            CallLogTabFilter.SUBTEL_809 -> classifiedCalls.filter {
                it.telecomMeta.type == ChileanNumberType.SUBTEL_TELEMARKETING_809 || it.log.number.contains("809")
            }
            CallLogTabFilter.INCOMING -> classifiedCalls.filter { it.isIncoming }
            CallLogTabFilter.OUTGOING -> classifiedCalls.filter { it.isOutgoing }
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
                        // 1. Entrantes (DEFAULT)
                        StitchFilterChip(
                            selected = currentFilter == CallLogTabFilter.INCOMING,
                            onClick = { currentFilter = CallLogTabFilter.INCOMING },
                            label = "Entrantes",
                            icon = Icons.AutoMirrored.Rounded.CallReceived,
                            iconTint = MaterialTheme.colorScheme.secondary,
                            count = if (incomingCount > 0) incomingCount else null
                        )

                        // 2. Salientes
                        StitchFilterChip(
                            selected = currentFilter == CallLogTabFilter.OUTGOING,
                            onClick = { currentFilter = CallLogTabFilter.OUTGOING },
                            label = "Salientes",
                            icon = Icons.AutoMirrored.Rounded.CallMade,
                            iconTint = MaterialTheme.colorScheme.primary,
                            count = if (outgoingCount > 0) outgoingCount else null
                        )

                        // 3. Spam bloqueado
                        StitchFilterChip(
                            selected = currentFilter == CallLogTabFilter.BLOCKED,
                            onClick = { currentFilter = CallLogTabFilter.BLOCKED },
                            label = if (blockedCount > 0) "Spam ($blockedCount)" else "Spam",
                            icon = Icons.Rounded.Shield,
                            iconTint = MaterialTheme.colorScheme.error
                        )

                        // 4. Perdidas
                        StitchFilterChip(
                            selected = currentFilter == CallLogTabFilter.MISSED,
                            onClick = { currentFilter = CallLogTabFilter.MISSED },
                            label = "Perdidas",
                            count = if (missedCount > 0) missedCount else null
                        )

                        // 5. Chao 809 (SUBTEL 809)
                        StitchFilterChip(
                            selected = currentFilter == CallLogTabFilter.SUBTEL_809,
                            onClick = { currentFilter = CallLogTabFilter.SUBTEL_809 },
                            label = "Chao 809",
                            icon = Icons.Rounded.CallEnd,
                            iconTint = MaterialTheme.colorScheme.tertiary,
                            count = if (subtelCount > 0) subtelCount else null
                        )

                        // 6. Todas
                        StitchFilterChip(
                            selected = currentFilter == CallLogTabFilter.ALL,
                            onClick = { currentFilter = CallLogTabFilter.ALL },
                            label = "Todas",
                            count = if (allCount > 0) allCount else null
                        )
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
                } else if (displayList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.size(72.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (currentFilter == CallLogTabFilter.BLOCKED) Icons.Rounded.Shield else Icons.AutoMirrored.Rounded.PhoneCallback,
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            val emptyText = when (currentFilter) {
                                CallLogTabFilter.ALL -> "No hay llamadas en tu historial"
                                CallLogTabFilter.BLOCKED -> "¡Impeque! Cero llamadas de spam bloqueadas"
                                CallLogTabFilter.MISSED -> "No tienes llamadas perdidas"
                                CallLogTabFilter.SUBTEL_809 -> "Ninguna llamada comercial 809 registrada"
                                CallLogTabFilter.INCOMING -> "No tienes llamadas entrantes registradas"
                                CallLogTabFilter.OUTGOING -> "No has emitido llamadas desde tu equipo"
                            }
                            Text(
                                text = emptyText,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(
                            items = displayList,
                            key = { _, item -> item.log.id },
                            contentType = { _, _ -> "call_card" }
                        ) { index, item ->
                            if (index >= displayList.size - 5 && logs.itemCount > 0) {
                                SideEffect {
                                    logs.get(logs.itemCount - 1)
                                }
                            }
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

                        // Background Protection Status Banner (Stitch Specification)
                        item(key = "protection_banner") {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp, bottom = 12.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Verified,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Protección activa en segundo plano",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Corta silencia automáticamente el spam y telemarketing sin interrumpirte. Filtros locales siempre activos en memoria (<2 ms).",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 17.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Action Button for Dialpad (Unified Corta FAB)
        CortaFloatingActionButton(
            onClick = { showDialpadSheet = true },
            icon = Icons.Rounded.Dialpad,
            contentDescription = strings.common.dialpad,
            modifier = Modifier
                .align(Alignment.BottomEnd)
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
            allCount = allCount,
            blockedCount = blockedCount,
            missedCount = missedCount,
            subtelCount = subtelCount,
            incomingCount = incomingCount,
            outgoingCount = outgoingCount,
            onSelectFilter = { selected ->
                currentFilter = selected
                showCategoriesSheet = false
            },
            onOpenTrash = {
                showCategoriesSheet = false
                showTrashSheet = true
            },
            onExportCsv = {
                showCategoriesSheet = false
                CortaExportImportHelper.exportCallsToCsv(context, classifiedCalls.map { it.log })
            },
            onDismiss = { showCategoriesSheet = false }
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
        shape = RoundedCornerShape(14.dp),
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
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
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
                            item.isOutgoing -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.secondary
                        }

                        Icon(
                            imageVector = directionIcon,
                            contentDescription = null,
                            tint = directionTint,
                            modifier = Modifier.size(13.dp)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = item.subtitleText,
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right Action Button (Info for blocked/spam; Quick Dial for normal, 48x48dp target)
                if (item.isBlocked || item.telecomMeta.type == ChileanNumberType.SUBTEL_TELEMARKETING_809) {
                    IconButton(
                        onClick = onActionClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = "Detalles y opciones",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    Surface(
                        onClick = onActionClick,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Call,
                                contentDescription = "Llamar",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
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
    val containerColor = when {
        item.isBlocked -> MaterialTheme.colorScheme.errorContainer
        item.telecomMeta.type == ChileanNumberType.SUBTEL_TELEMARKETING_809 -> MaterialTheme.colorScheme.tertiaryContainer
        item.telecomMeta.type == ChileanNumberType.EMERGENCY_NATIONAL -> MaterialTheme.colorScheme.secondaryContainer
        item.telecomMeta.type == ChileanNumberType.MUNICIPAL_SECURITY_14XX -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        item.telecomMeta.isVerifiedOfficialService -> MaterialTheme.colorScheme.primaryContainer
        item.isContact -> MaterialTheme.colorScheme.surfaceContainerHighest
        item.isMissed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
        item.isIncoming -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val iconTint = when {
        item.isBlocked -> MaterialTheme.colorScheme.onErrorContainer
        item.telecomMeta.type == ChileanNumberType.SUBTEL_TELEMARKETING_809 -> MaterialTheme.colorScheme.onTertiaryContainer
        item.telecomMeta.type == ChileanNumberType.EMERGENCY_NATIONAL -> MaterialTheme.colorScheme.onSecondaryContainer
        item.telecomMeta.type == ChileanNumberType.MUNICIPAL_SECURITY_14XX -> MaterialTheme.colorScheme.onSecondaryContainer
        item.telecomMeta.isVerifiedOfficialService -> MaterialTheme.colorScheme.onPrimaryContainer
        item.isMissed -> MaterialTheme.colorScheme.error
        item.isIncoming -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = CircleShape,
        color = containerColor,
        border = BorderStroke(1.dp, iconTint.copy(alpha = 0.35f)),
        modifier = modifier.size(42.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                item.isBlocked -> {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
                item.telecomMeta.type == ChileanNumberType.SUBTEL_TELEMARKETING_809 -> {
                    Icon(
                        imageVector = Icons.Rounded.CallEnd,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
                item.telecomMeta.type == ChileanNumberType.EMERGENCY_NATIONAL -> {
                    Icon(
                        imageVector = Icons.Rounded.LocalPolice,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
                item.telecomMeta.type == ChileanNumberType.MUNICIPAL_SECURITY_14XX -> {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
                item.telecomMeta.isVerifiedOfficialService -> {
                    Icon(
                        imageVector = Icons.Rounded.AccountBalance,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
                item.isContact -> {
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
                item.isMissed -> {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.CallMissed,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                item.isOutgoing -> {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.CallMade,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.CallReceived,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
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
    allCount: Int,
    blockedCount: Int,
    missedCount: Int,
    subtelCount: Int,
    incomingCount: Int,
    outgoingCount: Int,
    onSelectFilter: (CallLogTabFilter) -> Unit,
    onOpenTrash: () -> Unit = {},
    onExportCsv: () -> Unit = {},
    onDismiss: () -> Unit
) {
    data class CallCategoryOption(
        val filter: CallLogTabFilter,
        val title: String,
        val subtitle: String,
        val icon: ImageVector,
        val iconBg: Color,
        val iconTint: Color,
        val count: Int
    )

    val categories = listOf(
        CallCategoryOption(
            filter = CallLogTabFilter.ALL,
            title = "Todas las llamadas",
            subtitle = "Historial completo de tu teléfono",
            icon = Icons.Rounded.Phone,
            iconBg = MaterialTheme.colorScheme.surfaceContainerHighest,
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            count = allCount
        ),
        CallCategoryOption(
            filter = CallLogTabFilter.BLOCKED,
            title = "Spam bloqueado",
            subtitle = "Llamadas silenciadas o rechazadas automáticamente",
            icon = Icons.Rounded.Shield,
            iconBg = MaterialTheme.colorScheme.errorContainer,
            iconTint = MaterialTheme.colorScheme.onErrorContainer,
            count = blockedCount
        ),
        CallCategoryOption(
            filter = CallLogTabFilter.MISSED,
            title = "Perdidas",
            subtitle = "Llamadas no contestadas o pings fantasma",
            icon = Icons.AutoMirrored.Rounded.CallMissed,
            iconBg = MaterialTheme.colorScheme.errorContainer,
            iconTint = MaterialTheme.colorScheme.onErrorContainer,
            count = missedCount
        ),
        CallCategoryOption(
            filter = CallLogTabFilter.SUBTEL_809,
            title = "Chao 809 (Telemarketing)",
            subtitle = "Campañas comerciales masivas reguladas por SUBTEL",
            icon = Icons.Rounded.CallEnd,
            iconBg = MaterialTheme.colorScheme.tertiaryContainer,
            iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
            count = subtelCount
        ),
        CallCategoryOption(
            filter = CallLogTabFilter.INCOMING,
            title = "Entrantes",
            subtitle = "Llamadas que recibiste y contestaste",
            icon = Icons.AutoMirrored.Rounded.CallReceived,
            iconBg = MaterialTheme.colorScheme.secondaryContainer,
            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
            count = incomingCount
        ),
        CallCategoryOption(
            filter = CallLogTabFilter.OUTGOING,
            title = "Salientes",
            subtitle = "Llamadas realizadas desde este equipo",
            icon = Icons.AutoMirrored.Rounded.CallMade,
            iconBg = MaterialTheme.colorScheme.primaryContainer,
            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
            count = outgoingCount
        )
    )

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
                .padding(horizontal = 20.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 20.dp)
        ) {
            Text(
                text = "Categorías de llamadas",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Filtra el registro según la naturaleza de la llamada",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))

            val context = LocalContext.current
            val prefs = remember(context) { context.getSharedPreferences("corta_prefs", Context.MODE_PRIVATE) }
            val defaultTabName = prefs.getString("default_call_tab", CallLogTabFilter.INCOMING.name)

            categories.forEach { option ->
                val isSelected = currentFilter == option.filter
                val isDefault = option.filter.name == defaultTabName

                Surface(
                    onClick = { onSelectFilter(option.filter) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = option.iconBg,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = option.icon,
                                    contentDescription = null,
                                    tint = option.iconTint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = option.title,
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                if (option.count > 0) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = "${option.count}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                if (isDefault) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                                    ) {
                                        Text(
                                            text = "Inicial",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = option.subtitle,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Seleccionado",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    prefs.edit().putString("default_call_tab", currentFilter.name).apply()
                    Toast.makeText(context, "Pestaña predeterminada actualizada", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PushPin,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Usar como pestaña predeterminada al abrir",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onOpenTrash,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Papelera de reciclaje de llamadas",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onExportCsv,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Exportar historial a CSV",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

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

            // Action: Denuncia SERNAC No Molestar
            ListItem(
                headlineContent = { Text("Copiar para SERNAC No Molestar", fontWeight = FontWeight.SemiBold) },
                supportingContent = {
                    Text(
                        "Copia fono y fecha con formato oficial para denunciar en sernac.cl/nomolestar",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                leadingContent = { Icon(Icons.AutoMirrored.Rounded.Assignment, null) },
                colors = listItemColors,
                modifier = Modifier.clickable {
                    onDismiss()
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val reportText = "Número denunciado: ${item.formattedNumber} | Fecha: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(item.log.date))} | Motivo: Telemarketing o spam no autorizado"
                    clipboard.setPrimaryClip(ClipData.newPlainText("Denuncia SERNAC", reportText))
                    Toast.makeText(context, "Datos copiados para portal SERNAC No Molestar", Toast.LENGTH_LONG).show()
                }
            )

            // Action: Agregar a Contactos
            ListItem(
                headlineContent = { Text(strings.calls.addToContacts, fontWeight = FontWeight.SemiBold) },
                leadingContent = { Icon(Icons.Rounded.PersonAdd, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                colors = listItemColors,
                modifier = Modifier.clickable {
                    onDismiss()
                    val intent = Intent(Intent.ACTION_INSERT).apply {
                        type = ContactsContract.Contacts.CONTENT_TYPE
                        putExtra(ContactsContract.Intents.Insert.PHONE, item.log.number)
                    }
                    context.startActivity(intent)
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
