package com.corta.app.ui.contacts

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    contact: ContactInfo,
    onBack: () -> Unit,
    onCallClick: (String) -> Unit,
    onMessageClick: (String) -> Unit
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        // Edit Contact implementation
                        try {
                            // Intentar crear URI de lookup para edición
                            val contactUri = try {
                                val contactId = contact.id.toLongOrNull()
                                if (contactId != null) {
                                    Uri.withAppendedPath(
                                        ContactsContract.Contacts.CONTENT_URI,
                                        contact.id
                                    )
                                } else {
                                    // Si no podemos convertir el ID, usar búsqueda por teléfono
                                    val uri = Uri.withAppendedPath(
                                        ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                                        Uri.encode(contact.phoneNumber)
                                    )
                                    uri
                                }
                            } catch (e: Exception) {
                                // Último recurso: búsqueda por teléfono
                                Uri.withAppendedPath(
                                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                                    Uri.encode(contact.phoneNumber)
                                )
                            }
                            
                            val editIntent = Intent(Intent.ACTION_EDIT, contactUri).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(editIntent)
                        } catch (e: Exception) {
                            // Si todo falla, abrir la app de contactos
                            val contactsIntent = Intent(Intent.ACTION_VIEW).apply {
                                data = ContactsContract.Contacts.CONTENT_URI
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(contactsIntent)
                        }
                    }) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Large Avatar (almost half screen as requested, e.g., 200.dp to 240.dp)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .size(240.dp)
                    .padding(16.dp)
            ) {
                // In the future this can be an Image using Coil and the photoUri.
                // For now it defaults to a large icon as requested.
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    modifier = Modifier.padding(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = contact.name,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Actions Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ContactAction(
                    icon = Icons.Rounded.Call,
                    label = "Llamar",
                    onClick = { onCallClick(contact.phoneNumber) }
                )
                ContactAction(
                    icon = Icons.AutoMirrored.Filled.Message,
                    label = "Mensaje",
                    onClick = { onMessageClick(contact.phoneNumber) }
                )
                ContactAction(
                    icon = Icons.Rounded.Videocam,
                    label = "Video",
                    onClick = { /* Placeholder for future */ }
                )
                ContactAction(
                    icon = Icons.Rounded.Email,
                    label = "Correo",
                    onClick = { /* Placeholder for future */ }
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Detail Sheet (Card)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(text = "Información de contacto", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(text = contact.phoneNumber, style = MaterialTheme.typography.bodyLarge)
                            Text(text = "Móvil", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { onMessageClick(contact.phoneNumber) }) {
                            Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "Message", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContactAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Icon(imageVector = icon, contentDescription = label)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}
