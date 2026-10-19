package com.corta.app.ui.sms

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.collectAsLazyPagingItems
import com.corta.app.ui.designsystem.CortaCategoryExploreButton
import com.corta.app.ui.designsystem.CortaFloatingActionButton
import com.corta.app.ui.designsystem.StitchFilterChip
import com.corta.app.ui.export.CortaExportImportHelper
import com.corta.app.ui.i18n.Strings
import com.corta.app.ui.theme.Handedness
import com.corta.app.ui.theme.LocalDetailedMode
import com.corta.app.ui.theme.LocalHandedness
import com.corta.app.ui.trash.TrashBottomSheet
import com.corta.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.*

enum class SmsTabFilter(
    val label: String,
    val subtitle: String,
    val isPrimary: Boolean
) {
    INBOX("Entrada", "Mensajes recibidos", true),
    OUTBOX("Salida", "Mensajes enviados", true),
    SPAM("Spam", "Estafas, cuentos del tío y telemarketing", true),
    ALL("Todos", "Todo el historial de mensajes", true),
    BANKS_OTP("Bancos y claves", "Códigos de verificación y bancos oficiales", false),
    PERSONAL("Personales", "Contactos guardados y conocidos", false),
    ARCHIVED("Archivados", "Conversaciones archivadas", false);
}

/**
 * Native Chilean telecom date formatter matching Stitch specification:
 * - If today: "13:45", "11:20"
 * - If yesterday: "Ayer"
 * - If this year: "18 Mar", "17 Mar"
 * - If older: "dd/MM/yy"
 */
private val smsTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private val smsDayMonthFormat = SimpleDateFormat("d MMM", Locale("es", "CL"))
private val smsYearFormat = SimpleDateFormat("dd/MM/yy", Locale.getDefault())

fun formatSmsDateChile(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val oneDayMs = 86_400_000L

    if (diff in 0..oneDayMs && android.text.format.DateUtils.isToday(timestamp)) {
        return synchronized(smsTimeFormat) { smsTimeFormat.format(Date(timestamp)) }
    }
    if (diff in 0..(2 * oneDayMs) && android.text.format.DateUtils.isToday(timestamp + oneDayMs)) {
        return "Ayer"
    }
    if (diff < 300L * oneDayMs) {
        return synchronized(smsDayMonthFormat) { smsDayMonthFormat.format(Date(timestamp)) }
    }
    return synchronized(smsYearFormat) { smsYearFormat.format(Date(timestamp)) }
}

fun formatSmsDateSectionChile(timestamp: Long): String {
    if (timestamp <= 0L) return "Anteriores"
    val now = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { timeInMillis = timestamp }

    val nowYear = now.get(Calendar.YEAR)
    val msgYear = msgCal.get(Calendar.YEAR)
    val nowDayOfYear = now.get(Calendar.DAY_OF_YEAR)
    val msgDayOfYear = msgCal.get(Calendar.DAY_OF_YEAR)

    return when {
        nowYear == msgYear && nowDayOfYear == msgDayOfYear -> "Hoy"
        nowYear == msgYear && nowDayOfYear - msgDayOfYear == 1 -> "Ayer"
        nowYear == msgYear && now.get(Calendar.WEEK_OF_YEAR) == msgCal.get(Calendar.WEEK_OF_YEAR) -> "Esta semana"
        nowYear == msgYear && now.get(Calendar.MONTH) == msgCal.get(Calendar.MONTH) -> "Este mes"
        nowYear == msgYear -> SimpleDateFormat("MMMM", Locale("es", "CL")).format(Date(timestamp)).replaceFirstChar { it.uppercase() }
        else -> SimpleDateFormat("MMMM yyyy", Locale("es", "CL")).format(Date(timestamp)).replaceFirstChar { it.uppercase() }
    }
}

@Composable
private fun SmsSectionHeader(title: String) {
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SmsScreen(
    repository: SmsRepository,
    onThreadClick: (String) -> Unit,
    onComposeNew: () -> Unit = {},
    searchQuery: String = "",
    viewModel: SmsViewModel = koinViewModel(),
    isProtectionMode: Boolean = false
) {
    val context = LocalContext.current
    val cortaRepository: CortaRepository = koinInject()
    val screenScope = rememberCoroutineScope()
    val isDetailedMode = LocalDetailedMode.current

    val prefs = remember { context.getSharedPreferences("corta_prefs", Context.MODE_PRIVATE) }

    val defaultSmsCategoryOrder = remember {
        listOf(
            SmsTabFilter.INBOX,
            SmsTabFilter.OUTBOX,
            SmsTabFilter.SPAM,
            SmsTabFilter.ALL,
            SmsTabFilter.BANKS_OTP,
            SmsTabFilter.PERSONAL,
            SmsTabFilter.ARCHIVED
        )
    }
    val savedSmsOrderRaw = prefs.getString("sms_category_order", null)
    var categoryOrder by remember {
        mutableStateOf(
            if (savedSmsOrderRaw != null) {
                val names = savedSmsOrderRaw.split(",")
                val list = names.mapNotNull { name -> SmsTabFilter.entries.firstOrNull { it.name == name } }.toMutableList()
                SmsTabFilter.entries.forEach { if (!list.contains(it)) list.add(it) }
                list
            } else {
                defaultSmsCategoryOrder
            }
        )
    }
    val initialSmsFilter = remember {
        if (isProtectionMode) SmsTabFilter.SPAM
        else categoryOrder.firstOrNull() ?: SmsTabFilter.INBOX
    }
    var currentFilter by remember {
        mutableStateOf(initialSmsFilter)
    }

    var showCategorySheet by remember { mutableStateOf(false) }
    var showTagDictionarySheet by remember { mutableStateOf(false) }
    var showSpamKeywordsSheet by remember { mutableStateOf(false) }
    var showTrashSheet by remember { mutableStateOf(false) }
    var highlightedTagForDictionary by remember { mutableStateOf<String?>(null) }

    val archivedConversations by cortaRepository.observeArchivedConversations().collectAsState(initial = emptyList())
    val archivedThreadIds = remember(archivedConversations) {
        archivedConversations.map { it.threadId.toString() }.toSet()
    }

    val inboxSms = viewModel.inboxSms.collectAsLazyPagingItems()
    val blockedSms = viewModel.blockedSms.collectAsLazyPagingItems()

    var selectedSmsForMenu by remember { mutableStateOf<SmsMessage?>(null) }
    var selectedDisplayNameForMenu by remember { mutableStateOf("") }

    val loadedInbox = inboxSms.itemSnapshotList.items
    val loadedBlocked = blockedSms.itemSnapshotList.items

    // Off-thread pre-classified items from SmsPagingSource: zero regex on UI thread
    val classifiedInbox = remember(loadedInbox) {
        loadedInbox.map { it to it.telecomMeta }
    }

    val classifiedBlocked = remember(loadedBlocked) {
        loadedBlocked.map { it to it.telecomMeta }
    }

    // Deduplicated conversations per thread
    val allInboxConversations = remember(classifiedInbox) {
        classifiedInbox.distinctBy { it.first.message.threadId.ifBlank { it.first.message.id } }
    }
    val allBlockedConversations = remember(classifiedBlocked) {
        classifiedBlocked.distinctBy { it.first.message.threadId.ifBlank { it.first.message.id } }
    }

    // Dynamic counts for chips (excluding archived from active inbox)
    val activeInbox = remember(allInboxConversations, archivedThreadIds) {
        allInboxConversations.filter { it.first.message.threadId !in archivedThreadIds }
    }
    val activeBlocked = remember(allBlockedConversations, archivedThreadIds) {
        allBlockedConversations.filter { it.first.message.threadId !in archivedThreadIds }
    }

    val entradaCount = remember(activeInbox) {
        activeInbox.count { it.first.message.type != 2 && it.second.category != ChileanSmsCategory.SPAM_SMISHING }
    }
    val salidaCount = remember(activeInbox) {
        activeInbox.count { it.first.message.type == 2 }
    }
    val spamCount = remember(activeInbox, activeBlocked) {
        activeInbox.count { it.second.category == ChileanSmsCategory.SPAM_SMISHING } + activeBlocked.size
    }
    val todosCount = activeInbox.size
    val bancosCount = remember(activeInbox) {
        activeInbox.count { it.second.category == ChileanSmsCategory.BANKS_OTP }
    }
    val personalesCount = remember(activeInbox) {
        activeInbox.count { it.second.category == ChileanSmsCategory.PERSONAL }
    }
    val archivadosCount = remember(allInboxConversations, archivedThreadIds) {
        allInboxConversations.count { it.first.message.threadId in archivedThreadIds }
    }

    // Filtered items list for current active tab (Guarantees zero ghost items, zero spacing bugs, zero duplicate keys)
    val displayList = remember(activeInbox, activeBlocked, allInboxConversations, currentFilter, searchQuery, archivedThreadIds) {
        val baseList = when (currentFilter) {
            SmsTabFilter.INBOX -> activeInbox.filter { it.first.message.type != 2 && it.second.category != ChileanSmsCategory.SPAM_SMISHING }
            SmsTabFilter.OUTBOX -> activeInbox.filter { it.first.message.type == 2 }
            SmsTabFilter.SPAM -> {
                (activeInbox.filter { it.second.category == ChileanSmsCategory.SPAM_SMISHING } + activeBlocked)
                    .distinctBy { it.first.message.threadId.ifBlank { it.first.message.id } }
            }
            SmsTabFilter.ALL -> activeInbox
            SmsTabFilter.BANKS_OTP -> activeInbox.filter { it.second.category == ChileanSmsCategory.BANKS_OTP }
            SmsTabFilter.PERSONAL -> activeInbox.filter { it.second.category == ChileanSmsCategory.PERSONAL }
            SmsTabFilter.ARCHIVED -> allInboxConversations.filter { it.first.message.threadId in archivedThreadIds }
        }

        if (searchQuery.isBlank()) {
            baseList
        } else {
            baseList.filter { (item, _) ->
                item.message.body.contains(searchQuery, ignoreCase = true) ||
                item.message.address.contains(searchQuery, ignoreCase = true) ||
                item.displayName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val listState = rememberLazyListState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Quick Filters (Entrada, Salida, Spam, Todos on left, PINNED (+) on right)
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
                        SmsTabFilter.INBOX -> {
                            StitchFilterChip(
                                selected = isSelected,
                                onClick = { currentFilter = SmsTabFilter.INBOX },
                                label = "Entrada",
                                icon = Icons.AutoMirrored.Rounded.Chat,
                                count = if (entradaCount > 0) entradaCount else null
                            )
                        }
                        SmsTabFilter.OUTBOX -> {
                            StitchFilterChip(
                                selected = isSelected,
                                onClick = { currentFilter = SmsTabFilter.OUTBOX },
                                label = "Salida",
                                icon = Icons.AutoMirrored.Rounded.Send,
                                count = if (salidaCount > 0) salidaCount else null
                            )
                        }
                        SmsTabFilter.SPAM -> {
                            StitchFilterChip(
                                selected = isSelected,
                                onClick = { currentFilter = SmsTabFilter.SPAM },
                                label = if (spamCount > 0) "Spam ($spamCount)" else "Spam",
                                icon = Icons.Rounded.Shield,
                                iconTint = MaterialTheme.colorScheme.error
                            )
                        }
                        SmsTabFilter.ALL -> {
                            StitchFilterChip(
                                selected = isSelected,
                                onClick = { currentFilter = SmsTabFilter.ALL },
                                label = "Todos",
                                count = if (todosCount > 0) todosCount else null
                            )
                        }
                        SmsTabFilter.BANKS_OTP -> {
                            StitchFilterChip(
                                selected = isSelected,
                                onClick = { currentFilter = SmsTabFilter.BANKS_OTP },
                                label = "Bancos y claves",
                                icon = Icons.Rounded.AccountBalance,
                                iconTint = MaterialTheme.colorScheme.primary,
                                count = if (bancosCount > 0) bancosCount else null
                            )
                        }
                        SmsTabFilter.PERSONAL -> {
                            StitchFilterChip(
                                selected = isSelected,
                                onClick = { currentFilter = SmsTabFilter.PERSONAL },
                                label = "Personales",
                                icon = Icons.Rounded.Person,
                                iconTint = MaterialTheme.colorScheme.secondary,
                                count = if (personalesCount > 0) personalesCount else null
                            )
                        }
                        SmsTabFilter.ARCHIVED -> {
                            StitchFilterChip(
                                selected = isSelected,
                                onClick = { currentFilter = SmsTabFilter.ARCHIVED },
                                label = "Archivados",
                                icon = Icons.Rounded.Archive,
                                iconTint = MaterialTheme.colorScheme.tertiary,
                                count = if (archivadosCount > 0) archivadosCount else null
                            )
                        }
                    }
                }
            }


            Spacer(modifier = Modifier.width(6.dp))

            // 6. Botón (+) PINNED: SIEMPRE VISIBLE EN PANTALLA sin importar el scroll
            val isSecondarySmsActive = currentFilter == SmsTabFilter.BANKS_OTP || currentFilter == SmsTabFilter.PERSONAL || currentFilter == SmsTabFilter.ARCHIVED
            CortaCategoryExploreButton(
                onClick = { showCategorySheet = true },
                isActive = isSecondarySmsActive,
                contentDescription = "Ver todas las categorías"
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (displayList.isEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (currentFilter) {
                                    SmsTabFilter.SPAM -> Icons.Rounded.Shield
                                    SmsTabFilter.ARCHIVED -> Icons.Rounded.Archive
                                    else -> Icons.Rounded.MarkEmailRead
                                },
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    val emptyMessage = when (currentFilter) {
                        SmsTabFilter.INBOX -> "Bandeja de entrada limpia: No tienes mensajes nuevos"
                        SmsTabFilter.OUTBOX -> "No has enviado mensajes desde este teléfono"
                        SmsTabFilter.SPAM -> "¡Impeque! Cero estafas ni spam comercial"
                        SmsTabFilter.ALL -> "No hay mensajes en tu historial"
                        SmsTabFilter.BANKS_OTP -> "No tienes claves dinámicas ni avisos de bancos"
                        SmsTabFilter.PERSONAL -> "No tienes mensajes de tus contactos guardados"
                        SmsTabFilter.ARCHIVED -> "No tienes conversaciones archivadas"
                    }
                    Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val groupedSms = remember(displayList) {
                    displayList.groupBy { (item, _) ->
                        formatSmsDateSectionChile(item.message.date)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupedSms.forEach { (sectionTitle, itemsInSection) ->
                        stickyHeader(key = "header_sms_$sectionTitle") {
                            SmsSectionHeader(title = sectionTitle.uppercase(Locale("es", "CL")))
                        }
                        itemsIndexed(
                            items = itemsInSection,
                            key = { _, (item, _) -> item.message.threadId.ifBlank { item.message.id } },
                            contentType = { _, _ -> "sms_card" }
                        ) { index, (item, smsMeta) ->
                            if (index >= itemsInSection.size - 2 && inboxSms.itemCount > 0) {
                                SideEffect {
                                    inboxSms.get(inboxSms.itemCount - 1)
                                }
                            }
                            ConversationItem(
                                message = item.message,
                                displayName = item.displayName,
                                smsMeta = smsMeta,
                                isDetailedMode = isDetailedMode,
                                onClick = { onThreadClick(item.message.threadId) },
                                onLongClick = {
                                    selectedSmsForMenu = item.message
                                    selectedDisplayNameForMenu = item.displayName
                                },
                                onTagClick = { tag ->
                                    highlightedTagForDictionary = tag
                                    showTagDictionarySheet = true
                                }
                            )
                        }
                    }
                }
            }

            val handedness = LocalHandedness.current
            val fabAlignment = if (handedness == Handedness.LEFT) Alignment.BottomStart else Alignment.BottomEnd

            // Compose New SMS Button (Unified Material 3 Corta FAB)
            CortaFloatingActionButton(
                onClick = onComposeNew,
                icon = Icons.Rounded.Edit,
                contentDescription = Strings.sms.newMessageCd,
                modifier = Modifier
                    .align(fabAlignment)
                    .padding(16.dp)
            )

            if (showCategorySheet) {
                CategoriesBottomSheet(
                    currentFilter = currentFilter,
                    categoryOrder = categoryOrder,
                    inboxCount = entradaCount,
                    outboxCount = salidaCount,
                    spamCount = spamCount,
                    allCount = todosCount,
                    banksCount = bancosCount,
                    personalCount = personalesCount,
                    archivedCount = archivadosCount,
                    onSelectFilter = { selected ->
                        currentFilter = selected
                    },
                    onReorder = { newOrder ->
                        categoryOrder = newOrder
                        prefs.edit().putString("sms_category_order", newOrder.joinToString(",") { it.name }).apply()
                    },
                    onResetOrder = {
                        categoryOrder = defaultSmsCategoryOrder
                        prefs.edit().remove("sms_category_order").apply()
                    },
                    onDismiss = { showCategorySheet = false }
                )
            }


            if (showTagDictionarySheet) {
                TagDictionaryBottomSheet(
                    highlightedTag = highlightedTagForDictionary,
                    onDismiss = { showTagDictionarySheet = false }
                )
            }

            if (showSpamKeywordsSheet) {
                SpamKeywordsSheet(
                    repository = cortaRepository,
                    onDismiss = { showSpamKeywordsSheet = false }
                )
            }

            if (showTrashSheet) {
                TrashBottomSheet(
                    repository = cortaRepository,
                    onDismiss = { showTrashSheet = false }
                )
            }

            if (selectedSmsForMenu != null) {
                val isArchived = selectedSmsForMenu!!.threadId in archivedThreadIds
                SmsOptionsSheet(
                    message = selectedSmsForMenu!!,
                    displayName = selectedDisplayNameForMenu,
                    isArchived = isArchived,
                    onDismiss = { selectedSmsForMenu = null },
                    onOpenThread = {
                        val threadId = selectedSmsForMenu!!.threadId
                        selectedSmsForMenu = null
                        onThreadClick(threadId)
                    },
                    cortaRepository = cortaRepository,
                    screenScope = screenScope
                )
            }
        }
    }
}

/**
 * Filter Chip styled exactly as Native Telecom Clarity / One UI (screen.png)
 */


/**
 * Conversation Item matching screen.png down to the pixel:
 * - 48dp avatar aligned to top
 * - Sub-badge indicator (Heart for personal, checkmark for bank)
 * - Name on left, smart time ("13:45", "Ayer", "18 Mar") on right
 * - Category pill badge on second row
 * - Single-line body preview with bold OTP highlights or italics smishing warning
 * - 4dp red left accent bar for smishing cards
 */
/**
 * Contextual Avatar following Material 3 Native Telecom Clarity:
 * - Carriers (WOM, Entel, Claro, Movistar): Cell tower antenna icon
 * - Banks (BancoEstado, Santander, etc.): Account balance temple icon
 * - Couriers (Chilexpress, Correos, etc.): Delivery truck icon
 * - Government/Utilities (ClaveÚnica, Fonasa): Domain / public service icon
 * - Estafa (Smishing): Red shield icon
 * - Comercial (809): Orange campaign loudspeaker icon
 * - Known Contacts: Clean letter initial in bold (or Person icon if numeric)
 * - Mobile / Unknown: Person icon (never a '+' or raw digit)
 */
@Composable
fun SmsAvatar(
    displayName: String,
    address: String,
    smsMeta: ChileanSmsMeta,
    modifier: Modifier = Modifier
) {
    val kind = smsMeta.senderKind
    val isSmishing = smsMeta.isSmishing || kind == ChileanSenderKind.SMISHING
    val isCommercial = smsMeta.isCommercial809 || kind == ChileanSenderKind.COMMERCIAL

    val containerColor = when {
        isSmishing -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
        isCommercial -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
        kind == ChileanSenderKind.CARRIER -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
        kind == ChileanSenderKind.BANK -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
        kind == ChileanSenderKind.DELIVERY -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
        kind == ChileanSenderKind.GOVERNMENT -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
        kind == ChileanSenderKind.CONTACT -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val iconTint = when {
        isSmishing -> MaterialTheme.colorScheme.error
        isCommercial -> MaterialTheme.colorScheme.tertiary
        kind == ChileanSenderKind.CARRIER -> MaterialTheme.colorScheme.secondary
        kind == ChileanSenderKind.BANK -> MaterialTheme.colorScheme.primary
        kind == ChileanSenderKind.DELIVERY -> MaterialTheme.colorScheme.tertiary
        kind == ChileanSenderKind.GOVERNMENT -> MaterialTheme.colorScheme.primary
        kind == ChileanSenderKind.CONTACT -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = CircleShape,
        color = containerColor,
        border = BorderStroke(1.dp, iconTint.copy(alpha = 0.35f)),
        modifier = modifier.size(42.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                isSmishing -> {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
                isCommercial -> {
                    Icon(
                        imageVector = Icons.Rounded.Campaign,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
                kind == ChileanSenderKind.CARRIER -> {
                    Icon(
                        imageVector = Icons.Rounded.CellTower,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
                kind == ChileanSenderKind.BANK -> {
                    Icon(
                        imageVector = Icons.Rounded.AccountBalance,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
                kind == ChileanSenderKind.DELIVERY -> {
                    Icon(
                        imageVector = Icons.Rounded.LocalShipping,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
                kind == ChileanSenderKind.GOVERNMENT -> {
                    Icon(
                        imageVector = Icons.Rounded.Domain,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
                kind == ChileanSenderKind.CONTACT -> {
                    val firstLetter = displayName.firstOrNull { it.isLetter() }
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
                else -> {
                    // Unsaved numbers / standard Chilean mobile: Person icon (never '+')
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * Single-word clickable tag badge (Oficial, Estafa, Comercial, Contacto, Móvil)
 * Clicking opens the Tag Dictionary bottom sheet.
 */
@Composable
fun SmsTagBadge(
    smsMeta: ChileanSmsMeta,
    onClick: (String) -> Unit
) {
    val isSmishing = smsMeta.isSmishing || smsMeta.senderKind == ChileanSenderKind.SMISHING
    val isCommercial809 = smsMeta.isCommercial809 || smsMeta.senderKind == ChileanSenderKind.COMMERCIAL
    val isOfficial = smsMeta.category == ChileanSmsCategory.BANKS_OTP || smsMeta.isVerifiedBankOrService
    val isPersonal = smsMeta.senderKind == ChileanSenderKind.CONTACT || smsMeta.isPersonal

    val tagLabel: String
    val tagBg: Color
    val tagTextColor: Color
    val tagIcon: ImageVector

    when {
        isSmishing -> {
            tagLabel = "Estafa"
            tagBg = MaterialTheme.colorScheme.errorContainer
            tagTextColor = MaterialTheme.colorScheme.onErrorContainer
            tagIcon = Icons.Rounded.Block
        }
        isCommercial809 -> {
            tagLabel = "Comercial"
            tagBg = MaterialTheme.colorScheme.tertiaryContainer
            tagTextColor = MaterialTheme.colorScheme.onTertiaryContainer
            tagIcon = Icons.Rounded.Campaign
        }
        isOfficial -> {
            tagLabel = "Oficial"
            tagBg = MaterialTheme.colorScheme.primaryContainer
            tagTextColor = MaterialTheme.colorScheme.onPrimaryContainer
            tagIcon = Icons.Rounded.Verified
        }
        isPersonal -> {
            tagLabel = "Contacto"
            tagBg = MaterialTheme.colorScheme.secondaryContainer
            tagTextColor = MaterialTheme.colorScheme.onSecondaryContainer
            tagIcon = Icons.Rounded.Person
        }
        else -> {
            tagLabel = "Móvil"
            tagBg = MaterialTheme.colorScheme.surfaceContainerHighest
            tagTextColor = MaterialTheme.colorScheme.onSurface
            tagIcon = Icons.Rounded.PhoneAndroid
        }
    }

    Surface(
        shape = CircleShape,
        color = tagBg,
        border = BorderStroke(1.dp, tagTextColor.copy(alpha = 0.25f)),
        modifier = Modifier.clickable { onClick(tagLabel) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
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
                text = tagLabel,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = tagTextColor
            )
        }
    }
}

/**
 * Message preview text rendered cleanly below the header row.
 */
@Composable
fun SmsBodyPreview(
    message: SmsMessage,
    smsMeta: ChileanSmsMeta,
    modifier: Modifier = Modifier
) {
    val isSmishing = smsMeta.isSmishing || smsMeta.senderKind == ChileanSenderKind.SMISHING
    val isBankOrOtp = smsMeta.category == ChileanSmsCategory.BANKS_OTP

    if (isSmishing) {
        Text(
            text = "Enlace sospechoso bloqueado: \"${message.body.trim()}\"",
            fontStyle = FontStyle.Italic,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.error,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.fillMaxWidth(),
            lineHeight = 18.sp
        )
    } else if (isBankOrOtp && !smsMeta.detectedOtpCode.isNullOrBlank()) {
        val body = message.body.trim()
        val otp = smsMeta.detectedOtpCode!!
        val otpIndex = body.indexOf(otp)
        if (otpIndex != -1) {
            val annotated = buildAnnotatedString {
                append(body.substring(0, otpIndex))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
                    append(otp)
                }
                append(body.substring(otpIndex + otp.length))
            }
            Text(
                text = annotated,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier.fillMaxWidth(),
                lineHeight = 18.sp
            )
        } else {
            Text(
                text = body,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier.fillMaxWidth(),
                lineHeight = 18.sp
            )
        }
    } else {
        Text(
            text = message.body.trim(),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.fillMaxWidth(),
            lineHeight = 18.sp
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationItem(
    message: SmsMessage,
    displayName: String,
    smsMeta: ChileanSmsMeta,
    isDetailedMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTagClick: (String) -> Unit = {}
) {
    val isSmishing = smsMeta.isSmishing || smsMeta.senderKind == ChileanSenderKind.SMISHING
    val isCommercial809 = smsMeta.isCommercial809 || smsMeta.senderKind == ChileanSenderKind.COMMERCIAL

    val cardBg = when {
        isSmishing -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f)
        isCommercial809 -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surfaceContainerLowest
    }

    val cardBorder = when {
        isSmishing -> BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.20f))
        isCommercial809 -> BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.20f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = cardBorder,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Proportional Contextual Avatar (42dp)
                SmsAvatar(
                    displayName = displayName,
                    address = message.address,
                    smsMeta = smsMeta
                )

                // 2. Text Column: 3 Structured Lines (Line 1 Sender+Date, Line 2 Tag+Number, Line 3 Message Preview)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // Line 1: Sender Name / Address + Date
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val formattedName = PhoneNumberUtils.formatDisplayNumber(displayName)
                        Text(
                            text = formattedName,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        if (smsMeta.senderKind == ChileanSenderKind.CONTACT) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = "Contacto",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        } else if (smsMeta.isVerifiedBankOrService) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.Verified,
                                contentDescription = "Oficial",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        Text(
                            text = formatSmsDateChile(message.date),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }

                    // Line 2: Tag Badge + Phone number (if sender name was an alias/contact)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SmsTagBadge(
                            smsMeta = smsMeta,
                            onClick = onTagClick
                        )

                        if (displayName != message.address && message.address.isNotBlank()) {
                            Text(
                                text = PhoneNumberUtils.formatDisplayNumber(message.address),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Line 3: Message preview across full width
                    SmsBodyPreview(
                        message = message,
                        smsMeta = smsMeta
                    )
                }
            }

            // Forensic Telemetry in Detailed Mode (when activated by user)
            if (isDetailedMode) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isAlphanumeric = message.address.any { it.isLetter() }
                    val hasUrl = message.body.contains("http://", ignoreCase = true) ||
                            message.body.contains("https://", ignoreCase = true)

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Text(
                            text = if (isAlphanumeric) "Alfanumérico" else "Numérico E.164",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (hasUrl) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = "Enlace URL Detectado",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isSmishing) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = if (isSmishing) "Smishing Filtrado" else "Bandeja Segura",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSmishing) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsOptionsSheet(
    message: SmsMessage,
    displayName: String,
    isArchived: Boolean = false,
    onDismiss: () -> Unit,
    onOpenThread: () -> Unit,
    cortaRepository: CortaRepository,
    screenScope: kotlinx.coroutines.CoroutineScope
) {
    val context = LocalContext.current
    val strings = Strings
    val smsBlockedToast = strings.sms.blockedToast
    val smsCopiedToast = strings.sms.copiedToast
    val blockRuleAlias = strings.sms.blockRuleAlias
    val clipLabel = strings.contacts.clipLabel

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp)
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = message.address,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))

            ListItem(
                headlineContent = { Text("Abrir conversación", fontWeight = FontWeight.SemiBold) },
                leadingContent = { Icon(Icons.AutoMirrored.Rounded.Chat, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable {
                    onDismiss()
                    onOpenThread()
                }
            )

            ListItem(
                headlineContent = { Text("Llamar al remitente", fontWeight = FontWeight.SemiBold) },
                leadingContent = { Icon(Icons.Rounded.Call, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable {
                    onDismiss()
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${message.address}"))
                    context.startActivity(intent)
                }
            )

            ListItem(
                headlineContent = { Text(if (isArchived) "Desarchivar conversación" else "Archivar conversación", fontWeight = FontWeight.SemiBold) },
                leadingContent = { Icon(if (isArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable {
                    onDismiss()
                    screenScope.launch(Dispatchers.IO) {
                        val tid = message.threadId.toLongOrNull() ?: 0L
                        if (isArchived) {
                            cortaRepository.unarchiveConversation(tid)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Conversación desarchivada", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            cortaRepository.archiveConversation(tid, message.address)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Conversación archivada", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )

            ListItem(
                headlineContent = { Text("Mover mensaje a la papelera", fontWeight = FontWeight.SemiBold) },
                leadingContent = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable {
                    onDismiss()
                    screenScope.launch(Dispatchers.IO) {
                        cortaRepository.moveToTrash("SMS", message.id, message.address, message.body)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Mensaje movido a la papelera", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )

            ListItem(
                headlineContent = { Text(strings.sms.copyNumber, fontWeight = FontWeight.SemiBold) },
                leadingContent = { Icon(Icons.Rounded.ContentCopy, null) },
                modifier = Modifier.clickable {
                    onDismiss()
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(ClipData.newPlainText(clipLabel, message.address))
                    Toast.makeText(context, smsCopiedToast, Toast.LENGTH_SHORT).show()
                }
            )

            // 1-tap SERNAC complaint exporter
            ListItem(
                headlineContent = { Text("Copiar datos para SERNAC No Molestar", fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text("Formato oficial para reclamar llamadas/SMS comerciales no solicitados", style = MaterialTheme.typography.bodySmall) },
                leadingContent = { Icon(Icons.Rounded.ContentPaste, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable {
                    onDismiss()
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val dateFormatted = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(message.date))
                    val sernacData = """
                        Denuncia SERNAC No Molestar (Ley del Consumidor)
                        Número emisor: ${message.address}
                        Nombre / Alias: $displayName
                        Fecha de recepción: $dateFormatted
                        Mensaje recibido:
                        ${message.body.trim()}
                    """.trimIndent()
                    cb.setPrimaryClip(ClipData.newPlainText("Denuncia SERNAC No Molestar", sernacData))
                    Toast.makeText(context, "Datos copiados para SERNAC No Molestar", Toast.LENGTH_LONG).show()
                }
            )

            ListItem(
                headlineContent = {
                    Text(
                        text = strings.sms.blockSender,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                },
                leadingContent = { Icon(Icons.Rounded.Block, null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable {
                    onDismiss()
                    screenScope.launch(Dispatchers.IO) {
                        cortaRepository.addRule(message.address, FilterAction.BLOCK, false, blockRuleAlias)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, smsBlockedToast, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
    }
}

private data class CategoryOption(
    val filter: SmsTabFilter,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconBg: Color,
    val iconTint: Color,
    val count: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesBottomSheet(
    currentFilter: SmsTabFilter,
    categoryOrder: List<SmsTabFilter>,
    inboxCount: Int,
    outboxCount: Int,
    spamCount: Int,
    allCount: Int,
    banksCount: Int,
    personalCount: Int,
    archivedCount: Int = 0,
    onSelectFilter: (SmsTabFilter) -> Unit,
    onReorder: (List<SmsTabFilter>) -> Unit,
    onResetOrder: () -> Unit,
    onOpenKeywords: () -> Unit = {},
    onOpenTrash: () -> Unit = {},
    onExportCsv: () -> Unit = {},
    onExportJson: () -> Unit = {},
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Categorías de mensajes",
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(10.dp))

            categoryOrder.forEachIndexed { index, filter ->
                val isSelected = currentFilter == filter
                val isFirst = index == 0

                val (title, subtitle, icon, iconBg, iconTint, count) = when (filter) {
                    SmsTabFilter.INBOX -> Tuple6(
                        "Entrada",
                        "Mensajes recibidos",
                        Icons.AutoMirrored.Rounded.Chat,
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        inboxCount
                    )
                    SmsTabFilter.OUTBOX -> Tuple6(
                        "Salida",
                        "Mensajes enviados desde tu equipo",
                        Icons.AutoMirrored.Rounded.Send,
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.onSecondaryContainer,
                        outboxCount
                    )
                    SmsTabFilter.SPAM -> Tuple6(
                        "Spam y estafas",
                        "Bloqueados y sospechosos",
                        Icons.Rounded.Shield,
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.onErrorContainer,
                        spamCount
                    )
                    SmsTabFilter.ALL -> Tuple6(
                        "Todos",
                        "Historial completo de conversaciones",
                        Icons.Rounded.AllInclusive,
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        allCount
                    )
                    SmsTabFilter.BANKS_OTP -> Tuple6(
                        "Bancos y claves",
                        "Códigos de verificación y bancos",
                        Icons.Rounded.AccountBalance,
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.primary,
                        banksCount
                    )
                    SmsTabFilter.PERSONAL -> Tuple6(
                        "Personales",
                        "Contactos y conocidos",
                        Icons.Rounded.Person,
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer,
                        personalCount
                    )
                    SmsTabFilter.ARCHIVED -> Tuple6(
                        "Archivados",
                        "Conversaciones guardadas",
                        Icons.Rounded.Archive,
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        archivedCount
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
                                .clickable {
                                    onSelectFilter(filter)
                                    onDismiss()
                                }
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
                                .clickable {
                                    onSelectFilter(filter)
                                    onDismiss()
                                }
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

private data class Tuple6<A, B, C, D, E, F>(
    val a: A, val b: B, val c: C, val d: D, val e: E, val f: F
)

/**
 * Bottom Sheet that explains all tags, their meanings, and how Corta protects against them.
 * Opened when tapping any tag badge inside a message card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagDictionaryBottomSheet(
    highlightedTag: String? = null,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                        text = "Guía de Etiquetas",
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

            data class TagGuideItem(
                val tag: String,
                val icon: ImageVector,
                val chipColor: Color,
                val contentColor: Color,
                val description: String,
                val cortaAction: String
            )

            val tagList = listOf(
                TagGuideItem(
                    tag = "Estafa",
                    icon = Icons.Rounded.Block,
                    chipColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    description = "Enlaces fraudulentos (smishing), suplantación de identidad o engaños bancarios.",
                    cortaAction = "Acción: Bloqueado o silenciado automáticamente en memoria local (<2 ms)."
                ),
                TagGuideItem(
                    tag = "Comercial",
                    icon = Icons.Rounded.Campaign,
                    chipColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    description = "Telemarketing masivo, publicidad de ventas o mensajes bajo normativa SUBTEL 809.",
                    cortaAction = "Acción: Identificado al instante; puedes bloquear con un toque."
                ),
                TagGuideItem(
                    tag = "Oficial",
                    icon = Icons.Rounded.Verified,
                    chipColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    description = "Bancos oficiales (BancoEstado, Santander), empresas públicas y claves dinámicas (OTP).",
                    cortaAction = "Acción: Protegido en Lista Blanca prioritaria. Tus claves nunca se bloquean."
                ),
                TagGuideItem(
                    tag = "Contacto",
                    icon = Icons.Rounded.Person,
                    chipColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    description = "Personas que tienes guardadas en la agenda de contactos de tu teléfono.",
                    cortaAction = "Acción: Entrega directa y prioritaria a tu bandeja."
                ),
                TagGuideItem(
                    tag = "Móvil",
                    icon = Icons.Rounded.PhoneAndroid,
                    chipColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    description = "Celulares nacionales estándar (+56 9) aún no registrados en tus contactos.",
                    cortaAction = "Acción: Identificado como móvil válido para que decidas responder o guardar."
                ),
                TagGuideItem(
                    tag = "SMS",
                    icon = Icons.AutoMirrored.Rounded.Chat,
                    chipColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    description = "Mensajes de texto generales o servicios no clasificados.",
                    cortaAction = "Acción: Analizado localmente en tu equipo sin salir a internet."
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
                                    color = item.chipColor
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = null,
                                            tint = item.contentColor,
                                            modifier = Modifier.size(10.dp)
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
                                        text = "• Seleccionada",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = item.description,
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.cortaAction,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = "Entendido",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}


