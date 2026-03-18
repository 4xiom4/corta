package com.corta.app.ui.sms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.corta.app.ui.contacts.AndroidContactRepository
import com.corta.app.ui.contacts.ContactInfo
import com.corta.domain.CortaRepository
import com.corta.domain.FilterAction
import com.corta.domain.SmsMessage
import com.corta.domain.SmsRepository
import org.koin.compose.koinInject
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.util.Patterns
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import android.content.Context
import android.content.Intent
import android.net.Uri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsThreadScreen(
    threadId: String,
    repository: SmsRepository,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val cortaRepository: CortaRepository = koinInject()
    var messages by remember { mutableStateOf<List<SmsMessage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSharingContact by remember { mutableStateOf(false) }
    
    val context = androidx.compose.ui.platform.LocalContext.current
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = threadAddress, 
                        maxLines = 1, 
                        overflow = TextOverflow.Ellipsis 
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (messages.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No messages found")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        MessageBubble(message = msg, protectLinks = protectLinks, context = context)
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
                        Text("Seleccionar contacto para enviar", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
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
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AttachFile,
                        contentDescription = "Share Contact",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    placeholder = { Text("Escribir mensaje...") },
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
                                android.widget.Toast.makeText(context, "Failed to send: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape).size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp).padding(start = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: SmsMessage, protectLinks: Boolean = true, context: Context? = null) {
    val isSent = message.type == 2 // Telephony.Sms.MESSAGE_TYPE_SENT
    val alignment = if (isSent) Alignment.CenterEnd else Alignment.CenterStart
    val bgColor = if (isSent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isSent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val cornerShape = if (isSent) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }

    var showLinkDialog by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(bgColor, cornerShape)
                .padding(12.dp)
        ) {
            val annotatedString = buildAnnotatedString {
                if (protectLinks) {
                    // Default rule: Render everything as plain text, links are inert
                    withStyle(style = SpanStyle(color = textColor)) {
                        append(message.body)
                    }
                } else {
                    val matcher = Patterns.WEB_URL.matcher(message.body)
                    var lastIndex = 0
                    while (matcher.find()) {
                        val start = matcher.start()
                        val end = matcher.end()
                        val url = message.body.substring(start, end)
                        
                        withStyle(style = SpanStyle(color = textColor)) {
                            append(message.body.substring(lastIndex, start))
                        }
                        
                        pushStringAnnotation(tag = "URL", annotation = url)
                        withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Bold)) {
                            append(url)
                        }
                        pop()
                        lastIndex = end
                    }
                    withStyle(style = SpanStyle(color = textColor)) {
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
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatTime(message.date),
                color = textColor.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }

    showLinkDialog?.let { url ->
        AlertDialog(
            onDismissRequest = { showLinkDialog = null },
            title = { Text("¿Abrir enlace externo?") },
            text = { Text("Estás a punto de abrir el siguiente enlace desde un SMS. ¿Estás seguro de que confías en el remitente?\n\nDestino:\n$url") },
            confirmButton = {
                TextButton(onClick = { 
                    context?.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    showLinkDialog = null 
                }) {
                    Text("Sí, abrir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLinkDialog = null }) {
                    Text("No, cancelar")
                }
            }
        )
    }
}

private fun formatTime(timeInMillis: Long): String {
    val formatter = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return formatter.format(Date(timeInMillis))
}
