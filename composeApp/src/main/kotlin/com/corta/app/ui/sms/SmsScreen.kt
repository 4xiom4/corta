package com.corta.app.ui.sms

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import com.corta.domain.CortaRepository
import com.corta.domain.SmsMessage
import com.corta.domain.SmsRepository
import com.corta.domain.FilterAction
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.corta.app.ui.i18n.Strings
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import org.koin.compose.koinInject

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SmsScreen(
    repository: SmsRepository,
    onThreadClick: (String) -> Unit,
    onComposeNew: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToRules: () -> Unit = {},
    onNavigateToStats: () -> Unit
) {
    val context = LocalContext.current
    val cortaRepository: CortaRepository = koinInject()
    val screenScope = rememberCoroutineScope()
    var refreshTrigger by remember { mutableIntStateOf(0) }

    val rules by cortaRepository.observeRules().collectAsState(initial = emptyList())
    
    val pagerState = rememberPagerState { 2 }

    // Listas separadas para evitar saltos visuales al hacer swipe
    var receivedConversations by remember { mutableStateOf<List<SmsMessage>>(emptyList()) }
    var blockedConversations by remember { mutableStateOf<List<SmsMessage>>(emptyList()) }
    
    var displayNameCache by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedSmsForMenu by remember { mutableStateOf<SmsMessage?>(null) }
    var showMenuSheet by remember { mutableStateOf(false) }

    // Cargar datos para ambas pestañas cuando cambien las reglas o el trigger
    LaunchedEffect(refreshTrigger, rules) {
        isLoading = true
        val received = withContext(Dispatchers.IO) { repository.getConversations(rules) }
        val blocked = withContext(Dispatchers.IO) { repository.getBlockedConversations(rules) }
        
        val allConvos = received + blocked
        val nameMap = withContext(Dispatchers.IO) {
            allConvos.associate { msg ->
                val rule = cortaRepository.getRuleForNumber(msg.address, rules)
                msg.address to (rule?.alias ?: msg.address)
            }
        }
        
        receivedConversations = received
        blockedConversations = blocked
        displayNameCache = nameMap
        isLoading = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(Strings.sms, style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                actions = {
                    IconButton(onClick = { showMenuSheet = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = Strings.menu)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onComposeNew,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Nuevo mensaje", style = MaterialTheme.typography.labelLarge) }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant) }
            ) {
                Tab(
                    selected = (pagerState.currentPage == 0),
                    onClick = { screenScope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("Recibidos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = (pagerState.currentPage == 1),
                    onClick = { screenScope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text("Bloqueados", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                )
            }
            
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) { page ->
                val currentList = if (page == 0) receivedConversations else blockedConversations
                
                Box(modifier = Modifier.fillMaxSize()) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(strokeWidth = 3.dp)
                        }
                    } else if (currentList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = if (page == 0) Icons.Rounded.Sms else Icons.Rounded.Block,
                                    contentDescription = null,
                                    modifier = Modifier.size(72.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    if (page == 0) Strings.noSmsFound else "Sin mensajes bloqueados",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 88.dp), 
                        ) {
                            items(
                                items = currentList,
                                key = { it.threadId }
                            ) { message ->
                                ConversationItem(
                                    message = message, 
                                    onClick = { onThreadClick(message.threadId) },
                                    onLongClick = { selectedSmsForMenu = message },
                                    displayName = displayNameCache[message.address] ?: message.address
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMenuSheet) {
        com.corta.app.ui.components.NavigationMenuSheet(
            onDismiss = { showMenuSheet = false },
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToRules = onNavigateToRules,
            onNavigateToStats = onNavigateToStats
        )
    }

    selectedSmsForMenu?.let { message ->
        SmsOptionsSheet(
            message = message,
            displayName = displayNameCache[message.address] ?: message.address,
            onDismiss = { selectedSmsForMenu = null },
            cortaRepository = cortaRepository,
            screenScope = screenScope
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsOptionsSheet(
    message: SmsMessage,
    displayName: String,
    onDismiss: () -> Unit,
    cortaRepository: CortaRepository,
    screenScope: kotlinx.coroutines.CoroutineScope
) {
    val context = LocalContext.current
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Text(displayName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
            
            ActionListItem("Bloquear remitente", Icons.Rounded.Block, MaterialTheme.colorScheme.error) {
                onDismiss()
                screenScope.launch(Dispatchers.IO) {
                    cortaRepository.addRule(message.address, FilterAction.BLOCK, false, "Bloqueado desde SMS")
                    withContext(Dispatchers.Main) { 
                        Toast.makeText(context, "Número bloqueado", Toast.LENGTH_SHORT).show() 
                    }
                }
            }
            
            ActionListItem("Añadir a contactos", Icons.Rounded.PersonAdd) {
                onDismiss()
                val intent = Intent(Intent.ACTION_INSERT).apply {
                    type = ContactsContract.Contacts.CONTENT_TYPE
                    putExtra(ContactsContract.Intents.Insert.PHONE, message.address)
                }
                context.startActivity(intent)
            }

            ActionListItem("Copiar número", Icons.Rounded.ContentCopy) {
                onDismiss()
                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(ClipData.newPlainText("Phone", message.address))
                Toast.makeText(context, "Número copiado", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun ActionListItem(text: String, icon: ImageVector, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), color = androidx.compose.ui.graphics.Color.Transparent, shape = MaterialTheme.shapes.medium) {
        Row(modifier = Modifier.padding(vertical = 16.dp, horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color.copy(alpha = 0.8f), modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, color = color)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationItem(
    message: SmsMessage,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    displayName: String = message.address
) {
    val dateStr = remember(message.date) { 
        SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(message.date)) 
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = displayName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message.body.trim(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
            )
        }
    }
}
