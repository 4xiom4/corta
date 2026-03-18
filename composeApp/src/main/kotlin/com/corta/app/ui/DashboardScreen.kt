package com.corta.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corta.app.ui.i18n.Strings
import com.corta.app.ui.rules.RulesScreen
import com.corta.app.ui.stats.StatisticsScreen
import com.corta.app.ui.diagnostics.SystemDiagnosticsScreen
import com.corta.domain.CortaRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

enum class AppScreen { DASHBOARD, SETTINGS, RULES, STATS, DIAGNOSTICS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    val cortaRepository: CortaRepository = koinInject()
    
    // Estados para las pantallas secundarias
    var currentScreen by remember { mutableStateOf(AppScreen.DASHBOARD) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (currentScreen) {
            AppScreen.SETTINGS -> {
                androidx.activity.compose.BackHandler {
                    currentScreen = AppScreen.DASHBOARD
                }
                com.corta.app.ui.settings.SettingsScreen(
                    currentLanguage = "es",
                    onLanguageChange = { language ->
                        val prefs = context.getSharedPreferences("corta_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putString("language", language).apply()
                        // Podríamos agregar recreación de actividad si fuera necesario
                    },
                    onBack = { currentScreen = AppScreen.DASHBOARD }
                )
            }
            AppScreen.RULES -> {
                androidx.activity.compose.BackHandler {
                    currentScreen = AppScreen.DASHBOARD
                }
                RulesScreen(onBack = { currentScreen = AppScreen.DASHBOARD })
            }
            AppScreen.STATS -> {
                androidx.activity.compose.BackHandler {
                    currentScreen = AppScreen.DASHBOARD
                }
                StatisticsScreen(onBack = { currentScreen = AppScreen.DASHBOARD })
            }
            AppScreen.DIAGNOSTICS -> {
                androidx.activity.compose.BackHandler {
                    currentScreen = AppScreen.DASHBOARD
                }
                SystemDiagnosticsScreen(
    onBack = { currentScreen = AppScreen.DASHBOARD },
    onContinue = { 
        // Al continuar, volver al dashboard principal
        currentScreen = AppScreen.DASHBOARD 
    }
)
            }
            AppScreen.DASHBOARD -> {
                // En el dashboard, el BackHandler debería salir de la app o ir al fondo
                androidx.activity.compose.BackHandler(enabled = true) {
                    // Comportamiento por defecto: salir de la app
                    // O podemos minimizar la app si preferimos
                    (context as? android.app.Activity)?.moveTaskToBack(true)
                }
                DashboardMainContent(
                    onNavigateToSettings = { currentScreen = AppScreen.SETTINGS },
                    onNavigateToRules = { currentScreen = AppScreen.RULES },
                    onNavigateToStats = { currentScreen = AppScreen.STATS },
                    onNavigateToDiagnostics = { currentScreen = AppScreen.DIAGNOSTICS }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardMainContent(
    onNavigateToSettings: () -> Unit,
    onNavigateToRules: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToDiagnostics: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cortaRepository: CortaRepository = koinInject()
    
    // Optimización: usar remember para evitar recreación del repositorio
    val smsRepository = remember(context, cortaRepository) { 
        com.corta.app.ui.sms.AndroidSmsRepository(context, cortaRepository) 
    }

    // Tabs principales: 0=SMS, 1=Llamadas, 2=Contactos
    val pagerState = rememberPagerState(initialPage = 1) { 3 }
    val coroutineScope = rememberCoroutineScope()
    
    // Optimización: evitar recomposiciones innecesarias
    val currentPage by remember { derivedStateOf { pagerState.currentPage } }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Rounded.Email, contentDescription = Strings.sms) },
                    label = { Text(Strings.sms, style = MaterialTheme.typography.labelMedium) },
                    selected = pagerState.currentPage == 0,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Rounded.Phone, contentDescription = Strings.calls) },
                    label = { Text(Strings.calls, style = MaterialTheme.typography.labelMedium) },
                    selected = pagerState.currentPage == 1,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Rounded.Person, contentDescription = Strings.contacts) },
                    label = { Text(Strings.contacts, style = MaterialTheme.typography.labelMedium) },
                    selected = pagerState.currentPage == 2,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } }
                )
            }
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            beyondBoundsPageCount = 0, // Optimización: no precargar páginas
            userScrollEnabled = false, // Navegación solo por navbar para las pestañas principales
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) { page ->
            // Optimización: cargar contenido solo cuando la página está visible
            if (page == currentPage) {
                when (page) {
                    0 -> SmsTabScreen(
                        smsRepository = smsRepository,
                        onNavigateToSettings = onNavigateToSettings,
                        onNavigateToRules = onNavigateToRules,
                        onNavigateToStats = onNavigateToStats
                    )
                    1 -> DialpadTabScreen(onNavigateToSettings, onNavigateToRules, onNavigateToStats)
                    2 -> ContactsScreenContent(
                        onNavigateToSettings = onNavigateToSettings,
                        onNavigateToRules = onNavigateToRules,
                        onNavigateToStats = onNavigateToStats
                    )
                }
            } else {
                // Placeholder para páginas no visibles
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun SmsTabScreen(
    smsRepository: com.corta.app.ui.sms.AndroidSmsRepository,
    onNavigateToSettings: () -> Unit,
    onNavigateToRules: () -> Unit,
    onNavigateToStats: () -> Unit
) {
    var selectedThreadId by remember { mutableStateOf<String?>(null) }
    var isComposing by remember { mutableStateOf(false) }

    if (isComposing) {
        androidx.activity.compose.BackHandler { isComposing = false }
        com.corta.app.ui.sms.ComposeSmsScreen(onBack = { isComposing = false })
    } else if (selectedThreadId == null) {
        com.corta.app.ui.sms.SmsScreen(
            repository = smsRepository,
            onThreadClick = { threadId -> selectedThreadId = threadId },
            onComposeNew = { isComposing = true },
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToRules = onNavigateToRules,
            onNavigateToStats = onNavigateToStats
        )
    } else {
        androidx.activity.compose.BackHandler { selectedThreadId = null }
        com.corta.app.ui.sms.SmsThreadScreen(
            threadId = selectedThreadId!!,
            repository = smsRepository,
            onBack = { selectedThreadId = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DialpadTabScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToRules: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToDiagnostics: () -> Unit = {}
) {
    val pagerState = rememberPagerState { 2 }
    val coroutineScope = rememberCoroutineScope()
    var showMenuSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.calls, style = MaterialTheme.typography.titleLarge) },
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                actions = {
                    IconButton(onClick = { showMenuSheet = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = Strings.menu)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
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
                    selected = pagerState.currentPage == 0,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text(Strings.dialpad, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text(Strings.logs, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                )
            }
            
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { page ->
                if (page == 0) {
                    DialpadScreenContent()
                } else {
                    com.corta.app.ui.CallLogScreen()
                }
            }
        }
    }

    if (showMenuSheet) {
        com.corta.app.ui.components.NavigationMenuSheet(
            onDismiss = { showMenuSheet = false },
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToRules = onNavigateToRules,
            onNavigateToStats = onNavigateToStats,
            onNavigateToDiagnostics = onNavigateToDiagnostics
        )
    }
}

@Composable
fun ContactsScreenContent(
    onNavigateToSettings: () -> Unit,
    onNavigateToRules: () -> Unit,
    onNavigateToStats: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { com.corta.app.ui.contacts.AndroidContactRepository(context) }
    var selectedContact by remember { mutableStateOf<com.corta.app.ui.contacts.ContactInfo?>(null) }
    var contactForCall by remember { mutableStateOf<com.corta.app.ui.contacts.ContactInfo?>(null) }
    var contactForMessage by remember { mutableStateOf<com.corta.app.ui.contacts.ContactInfo?>(null) }

    if (selectedContact == null) {
        com.corta.app.ui.contacts.ContactsScreen(
            repository = repository,
            onContactClick = { contact -> selectedContact = contact },
            onCallClick = { contact ->
                if (contact.phoneNumbers.size > 1) {
                    contactForCall = contact
                } else {
                    makeCall(context, contact.phoneNumber)
                }
            },
            onMessageClick = { contact ->
                if (contact.phoneNumbers.size > 1) {
                    contactForMessage = contact
                } else {
                    val uri = Uri.fromParts("smsto", contact.phoneNumber, null)
                    val intent = Intent(Intent.ACTION_SENDTO, uri)
                    context.startActivity(intent)
                }
            },
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToRules = onNavigateToRules,
            onNavigateToStats = onNavigateToStats
        )
    } else {
        androidx.activity.compose.BackHandler { selectedContact = null }
        com.corta.app.ui.contacts.ContactDetailScreen(
            contact = selectedContact!!,
            onBack = { selectedContact = null },
            onCallClick = { number -> makeCall(context, number) },
            onMessageClick = { number ->
                val uri = Uri.fromParts("smsto", number, null)
                val intent = Intent(Intent.ACTION_SENDTO, uri)
                context.startActivity(intent)
            }
        )
    }

    contactForCall?.let { contact ->
        AlertDialog(
            onDismissRequest = { contactForCall = null },
            title = { Text("Seleccionar número", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    contact.phoneNumbers.forEach { number ->
                        TextButton(
                            onClick = {
                                contactForCall = null
                                makeCall(context, number)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(number, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { contactForCall = null }) {
                    Text("Cancelar")
                }
            },
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    contactForMessage?.let { contact ->
        AlertDialog(
            onDismissRequest = { contactForMessage = null },
            title = { Text("Seleccionar número", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    contact.phoneNumbers.forEach { number ->
                        TextButton(
                            onClick = {
                                contactForMessage = null
                                val uri = Uri.fromParts("smsto", number, null)
                                val intent = Intent(Intent.ACTION_SENDTO, uri)
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(number, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { contactForMessage = null }) {
                    Text("Cancelar")
                }
            },
            shape = MaterialTheme.shapes.extraLarge
        )
    }
}

@Composable
fun DialpadScreenContent() {
    val context = androidx.compose.ui.platform.LocalContext.current
    com.corta.app.ui.dialer.DialpadScreen(
        onDial = { number -> makeCall(context, number) }
    )
}

private fun makeCall(context: Context, number: String) {
    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    val uri = Uri.fromParts("tel", number, null)
    
    val extras = android.os.Bundle().apply {
        putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false)
        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                val accounts = telecomManager.callCapablePhoneAccounts
                if (accounts.isNotEmpty()) {
                    putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accounts[0])
                }
            }
        } catch (e: Exception) {}
    }

    try {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            telecomManager.placeCall(uri, extras)
        } else {
            throw SecurityException("CALL_PHONE permission not granted")
        }
    } catch (e: SecurityException) {
        val intent = Intent(Intent.ACTION_CALL, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            context.startActivity(intent)
        } else {
            val dialIntent = Intent(Intent.ACTION_DIAL, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dialIntent)
        }
    }
}
