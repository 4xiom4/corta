package com.corta.app.ui.sms

import android.telephony.SmsManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corta.app.ui.contacts.AndroidContactRepository
import com.corta.app.ui.contacts.ContactInfo
import com.corta.app.ui.i18n.Strings
import com.corta.domain.CortaRepository
import com.corta.domain.FilterAction
import org.koin.compose.koinInject
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ComposeSmsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { AndroidContactRepository(context) }
    val cortaRepository: CortaRepository = koinInject()
    var contacts by remember { mutableStateOf<List<ContactInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    
    var selectedContacts by remember { mutableStateOf(setOf<ContactInfo>()) }
    var isComposing by remember { mutableStateOf(false) }
    var messageText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        contacts = repository.getContacts()
        isLoading = false
    }

    val filteredContacts = remember(contacts, searchQuery) {
        if (searchQuery.isBlank()) contacts
        else contacts.filter { it.name.contains(searchQuery, ignoreCase = true) || it.phoneNumber.contains(searchQuery) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (isComposing) {
                        Text("Redactar Mensaje")
                    } else if (selectedContacts.isNotEmpty()) {
                        Text("${selectedContacts.size} seleccionados")
                    } else {
                        Text("Nuevo Mensaje")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        if (isComposing) {
                            isComposing = false
                        } else if (selectedContacts.isNotEmpty()) {
                            selectedContacts = emptySet()
                        } else {
                            onBack() 
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (isComposing) {
                FloatingActionButton(
                    onClick = {
                        if (messageText.isNotBlank() && selectedContacts.isNotEmpty()) {
                            val smsManager = context.getSystemService(SmsManager::class.java)
                            selectedContacts.forEach { contact ->
                                try {
                                    smsManager.sendTextMessage(contact.phoneNumber, null, messageText, null, null)
                                    cortaRepository.logSmsAction(contact.phoneNumber, System.currentTimeMillis(), FilterAction.ALLOW, messageText)
                                } catch (e: Exception) {
                                    // Handle missing permission or dual sim error
                                }
                            }
                            onBack()
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
                }
            } else if (selectedContacts.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { isComposing = true },
                    icon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    text = { Text("Redactar") }
                )
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (isComposing) {
                // Composing UI
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Para: " + selectedContacts.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        placeholder = { Text("Escribe tu mensaje aquí...") },
                        maxLines = 10
                    )
                }
            } else {
                // Contact Selection UI
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(12.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text("Buscar contactos", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                innerTextField()
                            }
                        )
                    }
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        items(filteredContacts, key = { it.id }) { contact ->
                            val isSelected = selectedContacts.contains(contact)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.background)
                                    .combinedClickable(
                                        onClick = {
                                            if (selectedContacts.isNotEmpty()) {
                                                selectedContacts = if (isSelected) selectedContacts - contact else selectedContacts + contact
                                            } else {
                                                selectedContacts = setOf(contact)
                                                isComposing = true
                                            }
                                        },
                                        onLongClick = {
                                            selectedContacts = if (isSelected) selectedContacts - contact else selectedContacts + contact
                                        }
                                    )
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (isSelected) {
                                            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                                        } else {
                                            Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(text = contact.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(text = contact.phoneNumber, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
