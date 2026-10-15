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
import com.corta.app.ui.i18n.Strings
import com.corta.app.ui.theme.LocalDetailedMode
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
    PERSONAL("Personales", "Contactos guardados y conocidos", false);
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
    val initialSmsFilter = remember {
        if (isProtectionMode) SmsTabFilter.SPAM
        else {
            val savedName = prefs.getString("default_sms_tab", SmsTabFilter.INBOX.name)
            SmsTabFilter.entries.firstOrNull { it.name == savedName } ?: SmsTabFilter.INBOX
        }
    }
    var currentFilter by remember {
        mutableStateOf(initialSmsFilter)
    }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showTagDictionarySheet by remember { mutableStateOf(false) }
    var highlightedTagForDictionary by remember { mutableStateOf<String?>(null) }

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

    // Dynamic counts for chips
    val entradaCount = remember(allInboxConversations) {
        allInboxConversations.count { it.first.message.type != 2 && it.second.category != ChileanSmsCategory.SPAM_SMISHING }
    }
    val salidaCount = remember(allInboxConversations) {
        allInboxConversations.count { it.first.message.type == 2 }
    }
    val spamCount = remember(allInboxConversations, allBlockedConversations) {
        allInboxConversations.count { it.second.category == ChileanSmsCategory.SPAM_SMISHING } + allBlockedConversations.size
    }
    val todosCount = allInboxConversations.size
    val bancosCount = remember(allInboxConversations) {
        allInboxConversations.count { it.second.category == ChileanSmsCategory.BANKS_OTP }
    }
    val personalesCount = remember(allInboxConversations) {
        allInboxConversations.count { it.second.category == ChileanSmsCategory.PERSONAL }
    }

    // Filtered items list for current active tab (Guarantees zero ghost items, zero spacing bugs, zero duplicate keys)
    val displayList = remember(allInboxConversations, allBlockedConversations, currentFilter, searchQuery) {
        val baseList = when (currentFilter) {
            SmsTabFilter.INBOX -> allInboxConversations.filter { it.first.message.type != 2 && it.second.category != ChileanSmsCategory.SPAM_SMISHING }
            SmsTabFilter.OUTBOX -> allInboxConversations.filter { it.first.message.type == 2 }
            SmsTabFilter.SPAM -> {
                (allInboxConversations.filter { it.second.category == ChileanSmsCategory.SPAM_SMISHING } + allBlockedConversations)
                    .distinctBy { it.first.message.threadId.ifBlank { it.first.message.id } }
            }
            SmsTabFilter.ALL -> allInboxConversations
            SmsTabFilter.BANKS_OTP -> allInboxConversations.filter { it.second.category == ChileanSmsCategory.BANKS_OTP }
            SmsTabFilter.PERSONAL -> allInboxConversations.filter { it.second.category == ChileanSmsCategory.PERSONAL }
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
                // 1. Entrada (Recibidos)
                StitchFilterChip(
                    selected = currentFilter == SmsTabFilter.INBOX,
                    onClick = { currentFilter = SmsTabFilter.INBOX },
                    label = "Entrada",
                    count = if (entradaCount > 0) entradaCount else null
                )

                // 2. Salida (Enviados)
                StitchFilterChip(
                    selected = currentFilter == SmsTabFilter.OUTBOX,
                    onClick = { currentFilter = SmsTabFilter.OUTBOX },
                    label = "Salida",
                    count = if (salidaCount > 0) salidaCount else null
                )

                // 3. Spam (Estafas, 809, bloqueados)
                StitchFilterChip(
                    selected = currentFilter == SmsTabFilter.SPAM,
                    onClick = { currentFilter = SmsTabFilter.SPAM },
                    label = if (spamCount > 0) "Spam ($spamCount)" else "Spam"
                )

                // 4. Todos
                StitchFilterChip(
                    selected = currentFilter == SmsTabFilter.ALL,
                    onClick = { currentFilter = SmsTabFilter.ALL },
                    label = "Todos",
                    count = if (todosCount > 0) todosCount else null
                )

                // 5. Chip de categoría secundaria visible si fue seleccionada desde (+)
                if (currentFilter == SmsTabFilter.BANKS_OTP) {
                    StitchFilterChip(
                        selected = true,
                        onClick = { currentFilter = SmsTabFilter.BANKS_OTP },
                        label = "Bancos y claves",
                        icon = Icons.Rounded.AccountBalance,
                        iconTint = MaterialTheme.colorScheme.primary,
                        count = if (bancosCount > 0) bancosCount else null
                    )
                } else if (currentFilter == SmsTabFilter.PERSONAL) {
                    StitchFilterChip(
                        selected = true,
                        onClick = { currentFilter = SmsTabFilter.PERSONAL },
                        label = "Personales",
                        icon = Icons.Rounded.Person,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        count = if (personalesCount > 0) personalesCount else null
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // 6. Botón (+) PINNED: SIEMPRE VISIBLE EN PANTALLA sin importar el scroll
            val isSecondarySmsActive = currentFilter == SmsTabFilter.BANKS_OTP || currentFilter == SmsTabFilter.PERSONAL
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
                                imageVector = if (currentFilter == SmsTabFilter.SPAM) Icons.Rounded.Shield else Icons.Rounded.MarkEmailRead,
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
                    }
                    Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                        key = { _, (item, _) -> item.message.threadId.ifBlank { item.message.id } },
                        contentType = { _, _ -> "sms_card" }
                    ) { index, (item, smsMeta) ->
                        if (index >= displayList.size - 5 && inboxSms.itemCount > 0) {
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

            // Compose New SMS Button (Unified Material 3 Corta FAB)
            CortaFloatingActionButton(
                onClick = onComposeNew,
                icon = Icons.Rounded.Edit,
                contentDescription = Strings.sms.newMessageCd,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )

            if (showCategorySheet) {
                CategoriesBottomSheet(
                    currentFilter = currentFilter,
                    inboxCount = entradaCount,
                    outboxCount = salidaCount,
                    spamCount = spamCount,
                    allCount = todosCount,
                    banksCount = bancosCount,
                    personalCount = personalesCount,
                    onSelectFilter = { selected ->
                        currentFilter = selected
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

            if (selectedSmsForMenu != null) {
                SmsOptionsSheet(
                    message = selectedSmsForMenu!!,
                    displayName = selectedDisplayNameForMenu,
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
        isSmishing -> MaterialTheme.colorScheme.errorContainer
        isCommercial -> MaterialTheme.colorScheme.tertiaryContainer
        kind == ChileanSenderKind.CARRIER -> MaterialTheme.colorScheme.secondaryContainer
        kind == ChileanSenderKind.BANK -> MaterialTheme.colorScheme.primaryContainer
        kind == ChileanSenderKind.DELIVERY -> MaterialTheme.colorScheme.tertiaryContainer
        kind == ChileanSenderKind.GOVERNMENT -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        kind == ChileanSenderKind.CONTACT -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val iconTint = when {
        isSmishing -> MaterialTheme.colorScheme.onErrorContainer
        isCommercial -> MaterialTheme.colorScheme.onTertiaryContainer
        kind == ChileanSenderKind.CARRIER -> MaterialTheme.colorScheme.onSecondaryContainer
        kind == ChileanSenderKind.BANK -> MaterialTheme.colorScheme.onPrimaryContainer
        kind == ChileanSenderKind.DELIVERY -> MaterialTheme.colorScheme.onTertiaryContainer
        kind == ChileanSenderKind.GOVERNMENT -> MaterialTheme.colorScheme.onPrimaryContainer
        kind == ChileanSenderKind.CONTACT -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = CircleShape,
        color = containerColor,
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
            tagTextColor = MaterialTheme.colorScheme.error
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
            tagTextColor = MaterialTheme.colorScheme.primary
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
            tagBg = MaterialTheme.colorScheme.surfaceContainerHigh
            tagTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            tagIcon = Icons.Rounded.PhoneAndroid
        }
    }

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(tagBg)
            .pointerInput(tagLabel) {
                detectTapGestures(
                    onTap = {
                        onClick(tagLabel)
                    }
                )
            }
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Row(
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
            maxLines = 2,
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier.fillMaxWidth(),
                lineHeight = 18.sp
            )
        } else {
            Text(
                text = body,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
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
            maxLines = 2,
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
        shape = RoundedCornerShape(14.dp),
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

                // 2. Text Column: 2 Balanced Rows
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // Row 1: Sender Name + Tag Badge + Date
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val formattedName = PhoneNumberUtils.formatDisplayNumber(displayName)
                            Text(
                                text = formattedName,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            SmsTagBadge(
                                smsMeta = smsMeta,
                                onClick = onTagClick
                            )
                        }

                        Text(
                            text = formatSmsDateChile(message.date),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }

                    // Row 2: Message preview (BELOW the tag & sender across full width)
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
    inboxCount: Int,
    outboxCount: Int,
    spamCount: Int,
    allCount: Int,
    banksCount: Int,
    personalCount: Int,
    onSelectFilter: (SmsTabFilter) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("corta_prefs", Context.MODE_PRIVATE) }
    val defaultTabName = prefs.getString("default_sms_tab", SmsTabFilter.INBOX.name)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp)
        ) {
            Text(
                text = "Categorías de mensajes",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Filtra tus SMS de forma rápida y segura en tu equipo",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))

            val categories = listOf(
                CategoryOption(
                    filter = SmsTabFilter.INBOX,
                    title = "Entrada",
                    subtitle = "Mensajes recibidos en tu bandeja",
                    icon = Icons.AutoMirrored.Rounded.Chat,
                    iconBg = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    count = inboxCount
                ),
                CategoryOption(
                    filter = SmsTabFilter.OUTBOX,
                    title = "Salida",
                    subtitle = "Mensajes que enviaste desde tu teléfono",
                    icon = Icons.AutoMirrored.Rounded.Send,
                    iconBg = MaterialTheme.colorScheme.secondaryContainer,
                    iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                    count = outboxCount
                ),
                CategoryOption(
                    filter = SmsTabFilter.SPAM,
                    title = "Spam y estafas",
                    subtitle = "Bloqueados, telemarketing 809 y cuentos del tío",
                    icon = Icons.Rounded.Shield,
                    iconBg = MaterialTheme.colorScheme.errorContainer,
                    iconTint = MaterialTheme.colorScheme.onErrorContainer,
                    count = spamCount
                ),
                CategoryOption(
                    filter = SmsTabFilter.ALL,
                    title = "Todos",
                    subtitle = "Historial completo de conversaciones",
                    icon = Icons.Rounded.AllInclusive,
                    iconBg = MaterialTheme.colorScheme.surfaceContainerHighest,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    count = allCount
                ),
                CategoryOption(
                    filter = SmsTabFilter.BANKS_OTP,
                    title = "Bancos y claves",
                    subtitle = "Códigos de verificación (OTP) y bancos oficiales",
                    icon = Icons.Rounded.AccountBalance,
                    iconBg = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.primary,
                    count = banksCount
                ),
                CategoryOption(
                    filter = SmsTabFilter.PERSONAL,
                    title = "Personales",
                    subtitle = "Contactos guardados en tu agenda y particulares",
                    icon = Icons.Rounded.Person,
                    iconBg = MaterialTheme.colorScheme.tertiaryContainer,
                    iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                    count = personalCount
                )
            )

            categories.forEach { option ->
                val isSelected = currentFilter == option.filter
                val isDefault = option.filter.name == defaultTabName

                Surface(
                    onClick = {
                        onSelectFilter(option.filter)
                        onDismiss()
                    },
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
                    prefs.edit().putString("default_sms_tab", currentFilter.name).apply()
                    Toast.makeText(context, "Categoría inicial de Mensajes guardada", Toast.LENGTH_SHORT).show()
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
                    text = "Fijar categoría actual como inicial al abrir Mensajes",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

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


