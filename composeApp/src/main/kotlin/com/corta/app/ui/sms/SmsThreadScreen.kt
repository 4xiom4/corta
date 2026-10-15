package com.corta.app.ui.sms

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Patterns
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corta.app.ui.contacts.AndroidContactRepository
import com.corta.app.ui.i18n.Strings
import com.corta.domain.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsThreadScreen(
    threadId: String,
    repository: SmsRepository,
    onBack: () -> Unit
) {
    val strings = Strings
    val coroutineScope = rememberCoroutineScope()
    val cortaRepository: CortaRepository = koinInject()
    var messages by remember { mutableStateOf<List<SmsMessage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSharingContact by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("corta_prefs", Context.MODE_PRIVATE) }
    val protectLinks = remember { prefs.getBoolean("protect_sms_links", true) }

    val contactRepository = remember { AndroidContactRepository(context) }
    var contacts by remember { mutableStateOf<List<ContactInfo>>(emptyList()) }

    LaunchedEffect(threadId) {
        val msgs = repository.getMessagesForThread(threadId)
        messages = msgs.sortedBy { it.date }
        contacts = contactRepository.getContacts()
        isLoading = false
    }

    val threadAddress = messages.firstOrNull()?.address ?: "Thread"
    val isKnownContact = remember(threadAddress, contacts) {
        contacts.any { it.phoneNumber.contains(threadAddress) || threadAddress.contains(it.phoneNumber) }
    }
    val contactName = remember(threadAddress, contacts) {
        contacts.firstOrNull { it.phoneNumber.contains(threadAddress) || threadAddress.contains(it.phoneNumber) }?.name
    }
    val headerDisplayName = contactName ?: threadAddress

    val threadMeta = remember(threadAddress, messages) {
        val lastBody = messages.lastOrNull()?.body.orEmpty()
        ChileanTelecomClassifier.classifySms(
            address = threadAddress,
            body = lastBody,
            isKnownContact = isKnownContact
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = headerDisplayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            when {
                                threadMeta.isVerifiedBankOrService -> {
                                    Icon(
                                        imageVector = Icons.Rounded.Verified,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "Verificado SUBTEL",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                threadMeta.isCommercial809 -> {
                                    Text(
                                        text = "Spam comercial 809",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                threadMeta.isSmishing -> {
                                    Text(
                                        text = "Sospecha de smishing",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                isKnownContact -> {
                                    Text(
                                        text = "Contacto guardado",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                else -> {
                                    Text(
                                        text = threadAddress,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.diagnostics.dsBackCd)
                    }
                },
                actions = {
                    // Quick Call Action
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$threadAddress"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Call,
                            contentDescription = "Llamar",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            // Link Protection Banner
            if (protectLinks) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Protección Corta: Los enlaces en este chat se muestran como texto plano seguro para protegerte de fraudes y smishing.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (messages.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(strings.sms.noMessages)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        MessageBubble(
                            message = msg,
                            protectLinks = protectLinks,
                            isVerifiedSender = threadMeta.isVerifiedBankOrService,
                            context = context
                        )
                    }
                }
            }

            // Input field
            var replyText by remember { mutableStateOf("") }

            if (isSharingContact) {
                ModalBottomSheet(
                    onDismissRequest = { isSharingContact = false }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 400.dp)
                    ) {
                        Text(strings.sms.selectContact, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                        LazyColumn {
                            items(contacts) { contact ->
                                ListItem(
                                    headlineContent = { Text(contact.name) },
                                    supportingContent = { Text(contact.phoneNumber) },
                                    modifier = Modifier.clickable {
                                        replyText = if (replyText.isEmpty()) {
                                            "Contacto: ${contact.name}\nTeléfono: ${contact.phoneNumber}"
                                        } else {
                                            "$replyText\nContacto: ${contact.name}\nTeléfono: ${contact.phoneNumber}"
                                        }
                                        isSharingContact = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { isSharingContact = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AttachFile,
                        contentDescription = strings.sms.shareContactCd,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    placeholder = { Text(strings.sms.threadComposePlaceholder) },
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                IconButton(
                    onClick = {
                        if (replyText.isNotBlank()) {
                            try {
                                val smsManager = context.getSystemService(android.telephony.SmsManager::class.java)
                                smsManager.sendTextMessage(threadAddress, null, replyText, null, null)
                                cortaRepository.logSmsAction(threadAddress, System.currentTimeMillis(), FilterAction.ALLOW, replyText)
                                replyText = ""
                            } catch (e: Exception) {
                                Toast.makeText(context, strings.sms.sendError.format(e.message.orEmpty()), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = strings.sms.sendCd,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp).padding(start = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: SmsMessage,
    protectLinks: Boolean = true,
    isVerifiedSender: Boolean = false,
    context: Context? = null
) {
    val strings = Strings
    val isSent = message.type == 2 // Telephony.Sms.MESSAGE_TYPE_SENT
    val alignment = if (isSent) Alignment.CenterEnd else Alignment.CenterStart

    val bgColor = if (isSent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isSent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    val cornerShape = if (isSent) {
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    }

    var showLinkDialog by remember { mutableStateOf<String?>(null) }

    // Check for OTP code in incoming bank/service messages
    val otpRegex = remember { Regex("""\b\d{4,8}\b""") }
    val detectedOtp = remember(message.body, isVerifiedSender, isSent) {
        if (!isSent && isVerifiedSender) {
            val lower = message.body.lowercase()
            if (lower.contains("clave") || lower.contains("código") || lower.contains("codigo") || lower.contains("otp")) {
                otpRegex.find(message.body)?.value
            } else null
        } else null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = if (isSent) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = bgColor,
            shape = cornerShape,
            tonalElevation = if (isSent) 4.dp else 0.dp,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // 1-tap OTP copy box
                if (detectedOtp != null && context != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clickable {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("Código OTP", detectedOtp))
                                Toast.makeText(context, "Código $detectedOtp copiado", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Key,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Código: $detectedOtp",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                text = "Copiar",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                val annotatedString = buildAnnotatedString {
                    val baseStyle = SpanStyle(color = textColor, fontSize = 16.sp)
                    if (protectLinks) {
                        // Safe plain text rendering (Unclickable to neutralize smishing)
                        withStyle(style = baseStyle) {
                            append(message.body)
                        }
                    } else {
                        val matcher = Patterns.WEB_URL.matcher(message.body)
                        var lastIndex = 0
                        while (matcher.find()) {
                            val start = matcher.start()
                            val end = matcher.end()
                            val url = message.body.substring(start, end)

                            withStyle(style = baseStyle) {
                                append(message.body.substring(lastIndex, start))
                            }

                            pushStringAnnotation(tag = "URL", annotation = url)
                            withStyle(style = baseStyle.copy(
                                color = if (isSent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Bold
                            )) {
                                append(url)
                            }
                            pop()
                            lastIndex = end
                        }
                        withStyle(style = baseStyle) {
                            append(message.body.substring(lastIndex))
                        }
                    }
                }

                ClickableText(
                    text = annotatedString,
                    style = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                    onClick = { offset ->
                        if (!protectLinks) {
                            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                .firstOrNull()?.let { annotation ->
                                    val url = if (!annotation.item.startsWith("http://") && !annotation.item.startsWith("https://")) {
                                        "http://${annotation.item}"
                                    } else {
                                        annotation.item
                                    }
                                    showLinkDialog = url
                                }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = formatTime(message.date),
                    color = textColor.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }

    showLinkDialog?.let { url ->
        AlertDialog(
            onDismissRequest = { showLinkDialog = null },
            title = { Text(strings.sms.linkDialogTitle) },
            text = { Text(strings.sms.linkDialogBody.format(url)) },
            confirmButton = {
                TextButton(onClick = {
                    context?.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    showLinkDialog = null
                }) {
                    Text(strings.sms.linkConfirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLinkDialog = null }) {
                    Text(strings.sms.linkCancel)
                }
            }
        )
    }
}

private fun formatTime(timeInMillis: Long): String {
    val formatter = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return formatter.format(Date(timeInMillis))
}
