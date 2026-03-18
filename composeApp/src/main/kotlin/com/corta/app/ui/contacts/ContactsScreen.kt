package com.corta.app.ui.contacts

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corta.app.ui.i18n.Strings
import com.corta.domain.CortaRepository
import com.corta.domain.FilterAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    repository: ContactRepository,
    onContactClick: (ContactInfo) -> Unit,
    onCallClick: (ContactInfo) -> Unit,
    onMessageClick: (ContactInfo) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToRules: () -> Unit,
    onNavigateToStats: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val cortaRepository: CortaRepository = koinInject()
    var contacts by remember { mutableStateOf<List<ContactInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedContactOptions by remember { mutableStateOf<ContactInfo?>(null) }
    var showFavoritesAtTop by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    // Debounce: solo filtra 300ms después de que el usuario deja de escribir
    var debouncedQuery by remember { mutableStateOf("") }
    var showMenuSheet by remember { mutableStateOf(false) }
    
    val context = LocalContext.current

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            if (!spokenText.isNullOrBlank()) { searchQuery = spokenText }
        }
    }

    LaunchedEffect(Unit) {
        isLoading = true
        contacts = repository.getContacts()
        isLoading = false
    }

    // Debounce: espera 300ms antes de actualizar la query de filtrado
    LaunchedEffect(searchQuery) {
        delay(300)
        debouncedQuery = searchQuery
    }

    var groupedContacts by remember { mutableStateOf<Map<String, List<ContactInfo>>>(emptyMap()) }
    LaunchedEffect(contacts, showFavoritesAtTop, debouncedQuery) {
        val grouped = withContext(Dispatchers.Default) {
            val query = debouncedQuery.trim().lowercase()
            val filtered = if (query.isEmpty()) contacts else {
                contacts.filter { 
                    it.name.lowercase().contains(query) || 
                    it.phoneNumber.contains(query) ||
                    it.phoneNumbers.any { num -> num.contains(query) }
                }
            }

            if (showFavoritesAtTop) {
                val favs = filtered.filter { it.isFavorite }
                val others = filtered.filter { !it.isFavorite }
                
                val res = mutableMapOf<String, List<ContactInfo>>()
                if (favs.isNotEmpty()) res["Favoritos"] = favs
                
                val othersGrouped = others.groupBy { 
                    it.name.firstOrNull()?.uppercaseChar()?.toString() ?: "#" 
                }.toSortedMap()
                
                res.putAll(othersGrouped)
                res
            } else {
                filtered.groupBy { 
                    it.name.firstOrNull()?.uppercaseChar()?.toString() ?: "#" 
                }.toSortedMap()
            }
        }
        groupedContacts = grouped
    }

    val listState = rememberLazyListState()
    val alphabet = remember(groupedContacts) { groupedContacts.keys.toList() }
    val precomputedOffsets = remember(groupedContacts, alphabet) {
        val offsets = mutableMapOf<String, Int>()
        var currentOffset = 0
        alphabet.forEach { letter ->
            offsets[letter] = currentOffset
            currentOffset += 1 + (groupedContacts[letter]?.size ?: 0)
        }
        offsets
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.contacts, style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { showMenuSheet = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = Strings.menu)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (contacts.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Rounded.PersonOff, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(16.dp))
                    Text(Strings.noContactsFound, style = MaterialTheme.typography.titleMedium)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(end = 40.dp, bottom = 80.dp)
                ) {
                    groupedContacts.forEach { (letter, contactsInLetter) ->
                        stickyHeader {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = letter,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        items(contactsInLetter, key = { it.id }) { contact ->
                            ContactItem(
                                contact = contact,
                                onClick = { onContactClick(contact) },
                                onLongClick = { selectedContactOptions = contact },
                                onCallClick = { onCallClick(contact) },
                                onMessageClick = { onMessageClick(contact) }
                            )
                        }
                    }
                }
                
                // Barra de scroll rápido alfabética
                if (debouncedQuery.isEmpty()) {
                    ContactsFastScrollBar(
                        alphabet = alphabet,
                        precomputedOffsets = precomputedOffsets,
                        listState = listState,
                        coroutineScope = coroutineScope
                    )
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

    selectedContactOptions?.let { contact ->
        ContactOptionsSheet(
            contact = contact,
            onDismiss = { selectedContactOptions = null },
            cortaRepository = cortaRepository,
            repository = repository,
            onFavoriteToggled = { updatedContacts -> contacts = updatedContacts }
        )
    }
}

@Composable
private fun BoxScope.ContactsFastScrollBar(
    alphabet: List<String>,
    precomputedOffsets: Map<String, Int>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    // Índice de la letra actualmente siendo "tocada"
    var activeIndex by remember { mutableStateOf<Int?>(null) }
    // Altura medida de la barra para calcular el porcentaje de posición
    var barHeightPx by remember { mutableStateOf(0f) }

    // Letra activa calculada del scroll actual (para highlight cuando NO se está arrastrando)
    val firstVisible by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val scrollActiveLetter by remember(firstVisible, precomputedOffsets) {
        derivedStateOf {
            precomputedOffsets.entries.lastOrNull { firstVisible >= it.value }?.key
        }
    }

    // Burbuja de indicador grande a la izquierda de la barra
    val activeLetter = activeIndex?.let { alphabet.getOrNull(it) }
    val bubbleVisible = activeIndex != null

    // Animación de entrada/salida de la burbuja
    val bubbleAlpha by animateFloatAsState(
        targetValue = if (bubbleVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "bubbleAlpha"
    )
    val bubbleScale by animateFloatAsState(
        targetValue = if (bubbleVisible) 1f else 0.7f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "bubbleScale"
    )

    // Burbuja indicadora flotante
    if (bubbleAlpha > 0f && activeLetter != null) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 52.dp) // a la izquierda de la barra
                .graphicsLayer(
                    alpha = bubbleAlpha,
                    scaleX = bubbleScale,
                    scaleY = bubbleScale
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 8.dp,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = activeLetter,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }

    // Barra alfabética propiamente
    Column(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .padding(vertical = 16.dp)
            .padding(end = 8.dp)
            .width(28.dp)
            .onGloballyPositioned { barHeightPx = it.size.height.toFloat() }
            .pointerInput(alphabet) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        val index = (offset.y / barHeightPx * alphabet.size).toInt().coerceIn(0, alphabet.size - 1)
                        activeIndex = index
                        coroutineScope.launch {
                            listState.scrollToItem(precomputedOffsets[alphabet[index]] ?: 0)
                        }
                    },
                    onDragEnd = { activeIndex = null },
                    onDragCancel = { activeIndex = null },
                    onVerticalDrag = { change, _ ->
                        val index = (change.position.y / barHeightPx * alphabet.size).toInt().coerceIn(0, alphabet.size - 1)
                        if (index != activeIndex) {
                            activeIndex = index
                            coroutineScope.launch {
                                listState.scrollToItem(precomputedOffsets[alphabet[index]] ?: 0)
                            }
                        }
                    }
                )
            }
            .pointerInput(alphabet) {
                detectTapGestures { offset ->
                    val index = (offset.y / barHeightPx * alphabet.size).toInt().coerceIn(0, alphabet.size - 1)
                    coroutineScope.launch {
                        listState.scrollToItem(precomputedOffsets[alphabet[index]] ?: 0)
                    }
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        alphabet.forEachIndexed { index, letter ->
            val isSelected = activeIndex == index || scrollActiveLetter == letter
            Text(
                text = if (letter == "Favoritos") "★" else letter,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = if (isSelected) 13.sp else 10.sp,
                modifier = Modifier.padding(vertical = 1.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactItem(
    contact: ContactInfo,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCallClick: () -> Unit,
    onMessageClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = contact.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (contact.isFavorite) {
                Text(
                    text = "Favorito",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        IconButton(onClick = onMessageClick) {
            Icon(Icons.AutoMirrored.Rounded.Message, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onCallClick) {
            Icon(Icons.Rounded.Phone, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactOptionsSheet(
    contact: ContactInfo,
    onDismiss: () -> Unit,
    cortaRepository: CortaRepository,
    repository: ContactRepository,
    onFavoriteToggled: (List<ContactInfo>) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Text(contact.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
            
            ActionListItem(
                if (contact.isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                if (contact.isFavorite) Icons.Rounded.StarOutline else Icons.Rounded.Star
            ) {
                coroutineScope.launch {
                    repository.toggleFavorite(contact.id, !contact.isFavorite)
                    val updated = repository.getContacts()
                    onFavoriteToggled(updated)
                    onDismiss()
                }
            }

            ActionListItem("Bloquear contacto", Icons.Rounded.Block, MaterialTheme.colorScheme.error) {
                onDismiss()
                coroutineScope.launch(Dispatchers.IO) {
                    cortaRepository.addRule(contact.phoneNumber, FilterAction.BLOCK, false, contact.name)
                    withContext(Dispatchers.Main) { 
                        Toast.makeText(context, "Número bloqueado", Toast.LENGTH_SHORT).show() 
                    }
                }
            }
            
            ActionListItem("Editar contacto", Icons.Rounded.Edit) {
                onDismiss()
                val intent = Intent(Intent.ACTION_EDIT).apply {
                    data = android.content.ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contact.id.toLong())
                }
                context.startActivity(intent)
            }

            ActionListItem("Copiar número", Icons.Rounded.ContentCopy) {
                onDismiss()
                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(ClipData.newPlainText("Phone", contact.phoneNumber))
                Toast.makeText(context, "Número copiado", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
private fun ActionListItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), color = Color.Transparent, shape = MaterialTheme.shapes.medium) {
        Row(modifier = Modifier.padding(vertical = 16.dp, horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color.copy(alpha = 0.8f), modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, color = color)
        }
    }
}
