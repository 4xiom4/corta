package com.corta.app.ui.contacts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corta.app.ui.export.CortaExportImportHelper
import com.corta.app.ui.telephony.ActiveSimCard
import com.corta.app.ui.telephony.DualSimHelper
import com.corta.db.ContactGroup
import com.corta.domain.ContactInfo
import com.corta.domain.CortaRepository
import com.corta.domain.FilterAction
import com.corta.domain.PhoneNumberUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailBottomSheet(
    contact: ClassifiedContact,
    cortaRepository: CortaRepository,
    onDismiss: () -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val allGroups by cortaRepository.observeContactGroups().collectAsState(initial = emptyList())
    var assignedGroups by remember { mutableStateOf<List<ContactGroup>>(emptyList()) }
    var showGroupDialog by remember { mutableStateOf(false) }

    // Dual SIM states
    val activeSims = remember { DualSimHelper.getActiveSimCards(context) }
    var showSimCallDialog by remember { mutableStateOf(false) }
    var showSimSmsDialog by remember { mutableStateOf(false) }
    var selectedActionNumber by remember { mutableStateOf(contact.phoneNumber) }

    // Recent Interactions (Calls & SMS)
    val allPhones = remember(contact) {
        val rawPhones = contact.rawContact?.phoneNumbers?.ifEmpty { listOf(contact.phoneNumber) }
            ?: listOf(contact.phoneNumber)
        (rawPhones + listOf(contact.phoneNumber)).filter { it.isNotBlank() }.distinct()
    }
    var recentInteractions by remember { mutableStateOf<List<ContactInteractionItem>>(emptyList()) }
    var isLoadingInteractions by remember { mutableStateOf(true) }

    LaunchedEffect(contact.id) {
        withContext(Dispatchers.IO) {
            assignedGroups = cortaRepository.getContactGroupsForContact(contact.id)
            recentInteractions = ContactInteractionsHelper.getRecentInteractions(
                context = context,
                cortaRepository = cortaRepository,
                phoneNumbers = allPhones
            )
            isLoadingInteractions = false
        }
    }

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
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp)
        ) {
            // Header: Avatar + Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ContactAvatar(
                    contact = contact,
                    modifier = Modifier.size(64.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = contact.displayPhone,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        onToggleFavorite(contact.id, !contact.isFavorite)
                    }
                ) {
                    Icon(
                        imageVector = if (contact.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                        contentDescription = "Favorito",
                        tint = if (contact.isFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Badges row: Fuente (Google, SIM, WhatsApp, Dispositivo) + Categoría + Grupos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Source badge
                val source = contact.rawContact?.source ?: "DEVICE"
                ContactSourceBadge(source = source)

                // Category badge
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = contact.tag,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                // Groups badges
                assignedGroups.forEach { group ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Dual SIM indicator badge
            if (activeSims.size > 1) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SimCard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Dual SIM activo: ${activeSims.joinToString(" · ") { "${it.displayName} (${it.carrierName})" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Fast Action Buttons Row (Llamar, SMS, WhatsApp, Compartir VCF)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickActionButton(
                    icon = Icons.Rounded.Call,
                    label = "Llamar",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = {
                        if (activeSims.size > 1) {
                            selectedActionNumber = contact.phoneNumber
                            showSimCallDialog = true
                        } else {
                            onDismiss()
                            DualSimHelper.makeCall(context, contact.phoneNumber, activeSims.firstOrNull())
                        }
                    }
                )

                QuickActionButton(
                    icon = Icons.AutoMirrored.Rounded.Message,
                    label = "SMS",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = {
                        if (activeSims.size > 1) {
                            selectedActionNumber = contact.phoneNumber
                            showSimSmsDialog = true
                        } else {
                            onDismiss()
                            DualSimHelper.sendSms(context, contact.phoneNumber, activeSims.firstOrNull())
                        }
                    }
                )

                QuickActionButton(
                    icon = Icons.AutoMirrored.Rounded.Chat,
                    label = "WhatsApp",
                    containerColor = Color(0xFF25D366).copy(alpha = 0.18f),
                    contentColor = Color(0xFF128C7E),
                    onClick = {
                        onDismiss()
                        val normalized = PhoneNumberUtils.normalize(contact.phoneNumber).removePrefix("+")
                        val waUrl = "https://api.whatsapp.com/send?phone=$normalized"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl))
                        context.startActivity(intent)
                    }
                )

                QuickActionButton(
                    icon = Icons.Rounded.Share,
                    label = "VCF",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    onClick = {
                        val contactInfo = contact.rawContact ?: ContactInfo(
                            id = contact.id,
                            name = contact.name,
                            phoneNumber = contact.phoneNumber,
                            phoneNumbers = listOf(contact.phoneNumber)
                        )
                        CortaExportImportHelper.exportContactToVcf(context, contactInfo)
                    }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(12.dp))

            // Contact details list
            val listItemColors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                headlineColor = MaterialTheme.colorScheme.onSurface,
                supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Multiple phone numbers if available
            val phoneNumbers = contact.rawContact?.phoneNumbers?.ifEmpty { listOf(contact.phoneNumber) }
                ?: listOf(contact.phoneNumber)

            phoneNumbers.forEach { number ->
                ListItem(
                    headlineContent = { Text(PhoneNumberUtils.formatDisplayNumber(number), fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Teléfono móvil · Toca para llamar o enviar SMS", style = MaterialTheme.typography.bodySmall) },
                    leadingContent = { Icon(Icons.Rounded.Phone, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (activeSims.size > 1) {
                                        selectedActionNumber = number
                                        showSimCallDialog = true
                                    } else {
                                        onDismiss()
                                        DualSimHelper.makeCall(context, number, activeSims.firstOrNull())
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Rounded.Call, contentDescription = "Llamar", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(
                                onClick = {
                                    if (activeSims.size > 1) {
                                        selectedActionNumber = number
                                        showSimSmsDialog = true
                                    } else {
                                        onDismiss()
                                        DualSimHelper.sendSms(context, number, activeSims.firstOrNull())
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Message, contentDescription = "SMS", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                            }
                            IconButton(
                                onClick = {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cb.setPrimaryClip(ClipData.newPlainText("Teléfono", number))
                                    Toast.makeText(context, "Número copiado", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copiar", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    colors = listItemColors
                )
            }

            // Historial Reciente (Interactions Section)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Historial Reciente",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (recentInteractions.isNotEmpty()) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = "${recentInteractions.size} eventos",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isLoadingInteractions) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else if (recentInteractions.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HistoryToggleOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Sin llamadas ni mensajes registrados recientemente con este contacto.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    recentInteractions.forEach { interaction ->
                        val (icon, tintColor, titleText) = when (interaction.channel) {
                            InteractionChannel.CALL_INCOMING -> Triple(
                                Icons.Rounded.CallReceived,
                                Color(0xFF2563EB),
                                if (interaction.durationSeconds > 0) "Llamada entrante (${formatCallDuration(interaction.durationSeconds)})" else "Llamada entrante"
                            )
                            InteractionChannel.CALL_OUTGOING -> Triple(
                                Icons.Rounded.CallMade,
                                Color(0xFF16A34A),
                                if (interaction.durationSeconds > 0) "Llamada saliente (${formatCallDuration(interaction.durationSeconds)})" else "Llamada saliente"
                            )
                            InteractionChannel.CALL_MISSED -> Triple(
                                Icons.Rounded.CallMissed,
                                MaterialTheme.colorScheme.error,
                                "Llamada perdida"
                            )
                            InteractionChannel.CALL_BLOCKED -> Triple(
                                Icons.Rounded.Block,
                                MaterialTheme.colorScheme.error,
                                "Bloqueada por Corta"
                            )
                            InteractionChannel.SMS_INCOMING -> Triple(
                                Icons.AutoMirrored.Rounded.Message,
                                Color(0xFF7C3AED),
                                "SMS recibido"
                            )
                            InteractionChannel.SMS_OUTGOING -> Triple(
                                Icons.AutoMirrored.Rounded.Send,
                                Color(0xFF0D9488),
                                "SMS enviado"
                            )
                        }

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = tintColor.copy(alpha = 0.12f),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = tintColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = titleText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (interaction.channel == InteractionChannel.CALL_BLOCKED || interaction.channel == InteractionChannel.CALL_MISSED)
                                                MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = formatInteractionDate(interaction.timestamp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    interaction.previewText?.let { snippet ->
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = snippet,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))

            // Asignar grupo
            ListItem(
                headlineContent = { Text("Administrar grupos de contacto", fontWeight = FontWeight.SemiBold) },
                supportingContent = {
                    val groupNames = if (assignedGroups.isEmpty()) "Ningún grupo asignado" else assignedGroups.joinToString { it.name }
                    Text(groupNames, style = MaterialTheme.typography.bodySmall)
                },
                leadingContent = { Icon(Icons.Rounded.Group, null, tint = MaterialTheme.colorScheme.primary) },
                trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
                colors = listItemColors,
                modifier = Modifier.clickable {
                    showGroupDialog = true
                }
            )

            // Bloquear número
            ListItem(
                headlineContent = {
                    Text("Bloquear llamadas y SMS de este contacto", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                },
                leadingContent = { Icon(Icons.Rounded.Block, null, tint = MaterialTheme.colorScheme.error) },
                colors = listItemColors,
                modifier = Modifier.clickable {
                    onDismiss()
                    coroutineScope.launch(Dispatchers.IO) {
                        cortaRepository.addRule(contact.phoneNumber, FilterAction.BLOCK, false, contact.name)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "${contact.name} bloqueado", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
    }

    if (showGroupDialog) {
        ManageGroupsDialog(
            allGroups = allGroups,
            assignedGroupIds = assignedGroups.map { it.id }.toSet(),
            onDismiss = { showGroupDialog = false },
            onToggleGroup = { group, assign ->
                coroutineScope.launch(Dispatchers.IO) {
                    if (assign) {
                        cortaRepository.addContactToGroup(contact.id, group.id)
                    } else {
                        cortaRepository.removeContactFromGroup(contact.id, group.id)
                    }
                    val updated = cortaRepository.getContactGroupsForContact(contact.id)
                    withContext(Dispatchers.Main) {
                        assignedGroups = updated
                    }
                }
            },
            onCreateNewGroup = { name ->
                coroutineScope.launch(Dispatchers.IO) {
                    cortaRepository.addContactGroup(name)
                }
            }
        )
    }

    if (showSimCallDialog) {
        SimChooserDialog(
            title = "Llamar a ${contact.name}",
            subtitle = "Selecciona la SIM para realizar la llamada:",
            sims = activeSims,
            onSelectSim = { sim ->
                showSimCallDialog = false
                onDismiss()
                DualSimHelper.makeCall(context, selectedActionNumber, sim)
            },
            onDismiss = { showSimCallDialog = false }
        )
    }

    if (showSimSmsDialog) {
        SimChooserDialog(
            title = "Enviar SMS a ${contact.name}",
            subtitle = "Selecciona la SIM remitente:",
            sims = activeSims,
            onSelectSim = { sim ->
                showSimSmsDialog = false
                onDismiss()
                DualSimHelper.sendSms(context, selectedActionNumber, sim)
            },
            onDismiss = { showSimSmsDialog = false }
        )
    }
}

@Composable
private fun ContactSourceBadge(source: String) {
    val (label, icon, color) = when (source.uppercase()) {
        "GOOGLE" -> Triple("Google", Icons.Rounded.CloudSync, Color(0xFF4285F4))
        "WHATSAPP" -> Triple("WhatsApp", Icons.AutoMirrored.Rounded.Chat, Color(0xFF25D366))
        "SIM" -> Triple("SIM", Icons.Rounded.SimCard, Color(0xFFF59E0B))
        else -> Triple("Teléfono", Icons.Rounded.Smartphone, MaterialTheme.colorScheme.primary)
    }

    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Surface(
            shape = CircleShape,
            color = containerColor,
            modifier = Modifier.size(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ManageGroupsDialog(
    allGroups: List<ContactGroup>,
    assignedGroupIds: Set<Long>,
    onDismiss: () -> Unit,
    onToggleGroup: (ContactGroup, Boolean) -> Unit,
    onCreateNewGroup: (String) -> Unit
) {
    var newGroupName by remember { mutableStateOf("") }
    var isAddingGroup by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Asignar a Grupos") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                allGroups.forEach { group ->
                    val isChecked = assignedGroupIds.contains(group.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleGroup(group, !isChecked) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { onToggleGroup(group, it) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (isAddingGroup) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        label = { Text("Nombre del grupo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (isAddingGroup) {
                Button(
                    onClick = {
                        if (newGroupName.isNotBlank()) {
                            onCreateNewGroup(newGroupName.trim())
                            newGroupName = ""
                            isAddingGroup = false
                        }
                    },
                    enabled = newGroupName.isNotBlank()
                ) {
                    Text("Crear grupo")
                }
            } else {
                Button(onClick = { isAddingGroup = true }) {
                    Text("+ Nuevo grupo")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Listo")
            }
        }
    )
}

@Composable
private fun SimChooserDialog(
    title: String,
    subtitle: String,
    sims: List<ActiveSimCard>,
    onSelectSim: (ActiveSimCard) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sims.forEach { sim ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSim(sim) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (sim.slotIndex == 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.SimCard,
                                contentDescription = null,
                                tint = if (sim.slotIndex == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "SIM ${sim.slotIndex + 1}: ${sim.carrierName}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (sim.number.isNotBlank()) {
                                    Text(
                                        text = sim.number,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

private fun formatInteractionDate(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diffDays = (now - timestamp) / (24 * 60 * 60 * 1000L)
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
    val yearFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    return when {
        diffDays == 0L -> "Hoy, ${timeFormat.format(Date(timestamp))}"
        diffDays == 1L -> "Ayer, ${timeFormat.format(Date(timestamp))}"
        diffDays < 365L -> "${dateFormat.format(Date(timestamp))}, ${timeFormat.format(Date(timestamp))}"
        else -> yearFormat.format(Date(timestamp))
    }
}

private fun formatCallDuration(seconds: Long): String {
    if (seconds <= 0) return ""
    val mins = seconds / 60
    val secs = seconds % 60
    return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
}

