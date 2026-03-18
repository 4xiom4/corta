package com.corta.app.ui

import android.provider.CallLog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallReceived
import androidx.compose.material.icons.rounded.CallMade
import androidx.compose.material.icons.rounded.CallMissed
import androidx.compose.material.icons.rounded.Block
import androidx.core.content.ContextCompat
import com.corta.domain.CortaRepository
import org.koin.compose.koinInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import com.corta.domain.FilterAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.Immutable

@Immutable
data class SystemCallLog(
    val id: String,
    val number: String,
    val name: String?,
    val date: Long,
    val type: Int,
    val duration: Long
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CallLogScreen(repository: CortaRepository = koinInject()) {
    val context = LocalContext.current
    var logs by remember { mutableStateOf<List<SystemCallLog>>(emptyList()) }
    var selectedCallLog by remember { mutableStateOf<SystemCallLog?>(null) }
    var showAliasDialog by remember { mutableStateOf<SystemCallLog?>(null) }
    var aliasText by remember { mutableStateOf("") }
    
    var hasPermission by remember { 
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val fallbackLogs by repository.observeCallLogs().collectAsState(initial = emptyList())
    val rules by repository.observeRules().collectAsState(initial = emptyList())

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            withContext(Dispatchers.IO) {
                val sysLogs = mutableListOf<SystemCallLog>()
                val projection = arrayOf(
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.DATE,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DURATION
                )
                val cursor = context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    projection, null, null, CallLog.Calls.DEFAULT_SORT_ORDER
                )
                cursor?.use {
                    val idIdx = it.getColumnIndex(CallLog.Calls._ID)
                    val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                    val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                    val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                    val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                    val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)
                    
                    while (it.moveToNext()) {
                        sysLogs.add(
                            SystemCallLog(
                                id = it.getString(idIdx),
                                number = it.getString(numIdx) ?: "Unknown",
                                name = it.getString(nameIdx),
                                date = it.getLong(dateIdx),
                                type = it.getInt(typeIdx),
                                duration = it.getLong(durIdx)
                            )
                        )
                    }
                }
                
                fallbackLogs.forEach { internalLog ->
                    sysLogs.add(
                        SystemCallLog(
                            id = internalLog.id.toString(),
                            number = internalLog.phoneNumber,
                            name = null,
                            date = internalLog.timestamp,
                            type = CallLog.Calls.BLOCKED_TYPE,
                            duration = internalLog.duration
                        )
                    )
                }

                logs = sysLogs.sortedByDescending { it.date }
            }
        }
    }

    if (!hasPermission) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Se necesita permiso para leer el registro de llamadas.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
        }
    } else if (logs.isEmpty() && fallbackLogs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No hay registro de llamadas reciente.", style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        val ruleMatchCache = remember(rules, logs) {
            logs.associate { log ->
                log.id to repository.getRuleForNumber(log.number)
            }
        }
        val dateFormatter = remember { SimpleDateFormat("dd/MM/yy hh:mm a", Locale.getDefault()) }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = logs,
                key = { it.id },
                contentType = { "call_log" }
            ) { log ->
                val dateStr = remember(log.date) { dateFormatter.format(Date(log.date)) }
                
                val callTypeStr = when(log.type) {
                    CallLog.Calls.INCOMING_TYPE -> "Entrante"
                    CallLog.Calls.OUTGOING_TYPE -> "Saliente"
                    CallLog.Calls.MISSED_TYPE -> "Perdida"
                    CallLog.Calls.REJECTED_TYPE, CallLog.Calls.BLOCKED_TYPE -> "Bloqueado"
                    else -> "Llamada"
                }

                val isBlocked = log.type == CallLog.Calls.REJECTED_TYPE || log.type == CallLog.Calls.BLOCKED_TYPE
                val matchingRule = ruleMatchCache[log.id]
                val displayName = matchingRule?.alias ?: log.name ?: log.number
                
                val iconVector = when(log.type) {
                    CallLog.Calls.INCOMING_TYPE -> Icons.Rounded.CallReceived
                    CallLog.Calls.OUTGOING_TYPE -> Icons.Rounded.CallMade
                    CallLog.Calls.MISSED_TYPE -> Icons.Rounded.CallMissed
                    CallLog.Calls.REJECTED_TYPE, CallLog.Calls.BLOCKED_TYPE -> Icons.Rounded.Block
                    else -> Icons.Rounded.CallReceived
                }
                
                val containerColor =
                    if (isBlocked || matchingRule != null) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.surface
                val contentColor =
                    if (isBlocked || matchingRule != null) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSurface
                val secondaryContentColor =
                    if (isBlocked || matchingRule != null) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .background(containerColor, RoundedCornerShape(16.dp))
                        .combinedClickable(
                            onClick = { /* Expand detail in future or call */ },
                            onLongClick = { selectedCallLog = log }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .fillMaxHeight()
                            .background(
                                if (isBlocked || matchingRule != null)
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                                else
                                    MaterialTheme.colorScheme.secondaryContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            tint = if (isBlocked || matchingRule != null)
                                MaterialTheme.colorScheme.onErrorContainer
                            else
                                MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = displayName,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = contentColor,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            Text(
                                text = callTypeStr,
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryContentColor
                            )
                        }
                        if (displayName != log.number) {
                            Text(
                                text = log.number,
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryContentColor,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                dateStr,
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryContentColor
                            )
                            if (log.duration > 0) {
                                Text(
                                    "${log.duration}s",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryContentColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAliasDialog != null) {
        AlertDialog(
            onDismissRequest = { showAliasDialog = null },
            title = { Text("Añadir apodo a Spam") },
            text = {
                Column {
                    Text("Ingresa un apodo para identificar este número de spam:")
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = aliasText,
                        onValueChange = { aliasText = it },
                        placeholder = { Text("Ej: Spam Cobranza") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                val callLogScope = rememberCoroutineScope()
                Button(onClick = {
                    val log = showAliasDialog!!
                    callLogScope.launch(Dispatchers.IO) {
                        repository.addRule(log.number, FilterAction.BLOCK, false, aliasText.ifBlank { "Bloqueado Llamada" })
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "${log.number} añadido a Spam con apodo: $aliasText", Toast.LENGTH_SHORT).show()
                            showAliasDialog = null
                            aliasText = ""
                        }
                    }
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showAliasDialog = null
                    aliasText = ""
                }) {
                    Text("Cancelar")
                }
            }
        )
    }

    selectedCallLog?.let { log ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedCallLog = null },
            sheetState = sheetState,
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Phone,
                        contentDescription = null,
                        modifier = Modifier.padding(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = log.name ?: log.number, style = MaterialTheme.typography.titleLarge)
                if (log.name != null) {
                    Text(text = log.number, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                ListItem(
                    headlineContent = { Text("Añadir a Contactos", style = MaterialTheme.typography.bodyLarge) },
                    leadingContent = { Icon(Icons.Rounded.PersonAdd, contentDescription = null) },
                    modifier = Modifier.clickable { 
                        selectedCallLog = null
                        val intent = Intent(Intent.ACTION_INSERT).apply {
                            type = ContactsContract.Contacts.CONTENT_TYPE
                            putExtra(ContactsContract.Intents.Insert.PHONE, log.number)
                        }
                        context.startActivity(intent)
                    }
                )

                ListItem(
                    headlineContent = { Text("Bloquear y añadir apodo Spam", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Rounded.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { 
                        showAliasDialog = log
                        aliasText = log.name ?: ""
                        selectedCallLog = null
                    }
                )

                ListItem(
                    headlineContent = { Text("Copiar Número", style = MaterialTheme.typography.bodyLarge) },
                    leadingContent = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                    modifier = Modifier.clickable { 
                        selectedCallLog = null
                        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Copied Phone Number", log.number)
                        clipboardManager.setPrimaryClip(clip)
                        Toast.makeText(context, "Número copiado", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}
