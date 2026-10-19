package com.corta.app.ui

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corta.app.ui.contacts.ContactsScreen
import com.corta.app.ui.diagnostics.SystemDiagnosticsScreen
import com.corta.app.ui.i18n.Strings
import com.corta.app.ui.profile.DonorProfileScreen
import com.corta.app.ui.rules.RulesScreen
import com.corta.app.ui.settings.SettingsScreen
import com.corta.app.ui.sms.AndroidSmsRepository
import com.corta.app.ui.sms.ComposeSmsScreen
import com.corta.app.ui.sms.SmsScreen
import com.corta.app.ui.sms.SmsThreadScreen
import com.corta.app.ui.stats.StatisticsScreen
import com.corta.app.ui.theme.LocalDetailedMode
import com.corta.domain.ContactRepository
import com.corta.domain.CortaRepository
import org.koin.compose.koinInject

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

enum class AppScreen { DASHBOARD, SETTINGS, RULES, STATS, PROFILE, DIAGNOSTICS }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    initialTargetTab: Int? = null,
    initialSmsSender: String? = null,
    initialCallNumber: String? = null,
    donationApproved: Boolean = false,
    onClearDeepLink: () -> Unit = {},
    viewModel: CortaAppViewModel = org.koin.androidx.compose.koinViewModel()
) {
    val context = LocalContext.current
    val currentLanguage by viewModel.language.collectAsState()

    var screenStack by remember { mutableStateOf(listOf(AppScreen.DASHBOARD)) }
    val currentScreen = screenStack.lastOrNull() ?: AppScreen.DASHBOARD

    fun navigateTo(screen: AppScreen) {
        if (screenStack.lastOrNull() != screen) {
            screenStack = screenStack + screen
        }
    }

    fun navigateBack() {
        if (screenStack.size > 1) {
            screenStack = screenStack.dropLast(1)
        } else {
            (context as? android.app.Activity)?.moveTaskToBack(true)
        }
    }

    LaunchedEffect(donationApproved) {
        if (donationApproved) {
            navigateTo(AppScreen.PROFILE)
            onClearDeepLink()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (currentScreen) {
            AppScreen.SETTINGS -> {
                SettingsScreen(
                    currentLanguage = currentLanguage,
                    onLanguageChange = { language ->
                        viewModel.setLanguage(language)
                    },
                    onBack = { navigateBack() },
                    onNavigateToDiagnostics = { navigateTo(AppScreen.DIAGNOSTICS) },
                    onNavigateToStats = { navigateTo(AppScreen.STATS) },
                    onNavigateToRules = { navigateTo(AppScreen.RULES) }
                )
            }
            AppScreen.RULES -> {
                BackHandler { navigateBack() }
                RulesScreen(onBack = { navigateBack() })
            }
            AppScreen.STATS -> {
                BackHandler { navigateBack() }
                StatisticsScreen(onBack = { navigateBack() })
            }
            AppScreen.PROFILE -> {
                BackHandler { navigateBack() }
                DonorProfileScreen(
                    onBack = { navigateBack() },
                    onNavigateToSettings = { navigateTo(AppScreen.SETTINGS) },
                    onNavigateToRules = { navigateTo(AppScreen.RULES) },
                    onNavigateToStats = { navigateTo(AppScreen.STATS) },
                    onNavigateToDiagnostics = { navigateTo(AppScreen.DIAGNOSTICS) }
                )
            }
            AppScreen.DIAGNOSTICS -> {
                BackHandler { navigateBack() }
                SystemDiagnosticsScreen(onBack = { navigateBack() })
            }
            AppScreen.DASHBOARD -> {
                BackHandler(enabled = true) {
                    (context as? android.app.Activity)?.moveTaskToBack(true)
                }
                DashboardMainContent(
                    initialTargetTab = initialTargetTab,
                    initialSmsSender = initialSmsSender,
                    initialCallNumber = initialCallNumber,
                    onClearDeepLink = onClearDeepLink,
                    onNavigateToSettings = { navigateTo(AppScreen.SETTINGS) },
                    onNavigateToRules = { navigateTo(AppScreen.RULES) },
                    onNavigateToStats = { navigateTo(AppScreen.STATS) },
                    onNavigateToProfile = { navigateTo(AppScreen.PROFILE) },
                    onNavigateToDiagnostics = { navigateTo(AppScreen.DIAGNOSTICS) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardMainContent(
    initialTargetTab: Int? = null,
    initialSmsSender: String? = null,
    initialCallNumber: String? = null,
    onClearDeepLink: () -> Unit = {},
    onNavigateToSettings: () -> Unit,
    onNavigateToRules: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToDiagnostics: () -> Unit
) {
    val context = LocalContext.current
    val strings = Strings
    val cortaRepository: CortaRepository = koinInject()
    val contactRepository: ContactRepository = koinInject()

    val prefs = remember { context.getSharedPreferences("corta_prefs", Context.MODE_PRIVATE) }
    var userAvatar by remember { mutableStateOf(prefs.getString("user_avatar", null)) }
    var userName by remember { mutableStateOf(prefs.getString("user_name", "Usuario Corta")) }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "user_avatar") {
                userAvatar = prefs.getString("user_avatar", null)
            } else if (key == "user_name") {
                userName = prefs.getString("user_name", "Usuario Corta")
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // 0: Mensajes, 1: Llamadas, 2: Contactos
    val pagerState = rememberPagerState(initialPage = initialTargetTab ?: 1) { 3 }
    val coroutineScope = rememberCoroutineScope()

    // SMS internal navigation state
    val smsRepository = remember(context, cortaRepository) {
        AndroidSmsRepository(context, cortaRepository)
    }
    var selectedSmsThreadId by remember { mutableStateOf<String?>(null) }
    var isComposingSms by remember { mutableStateOf(false) }

    // Global search state
    var isSearchActive by remember { mutableStateOf(false) }
    var globalSearchQuery by remember { mutableStateOf("") }

    LaunchedEffect(initialTargetTab, initialSmsSender, initialCallNumber) {
        if (initialTargetTab != null) {
            pagerState.scrollToPage(initialTargetTab)
        }
        if (initialSmsSender != null) {
            val resolvedThreadId = smsRepository.getThreadIdForSender(initialSmsSender) ?: "BLOCKED_$initialSmsSender"
            selectedSmsThreadId = resolvedThreadId
            onClearDeepLink()
        }
        if (initialCallNumber != null) {
            globalSearchQuery = initialCallNumber
            onClearDeepLink()
        }
    }

    if (isSearchActive) {
        BackHandler {
            isSearchActive = false
            globalSearchQuery = ""
        }
    }

    val roleManager = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
        } else {
            null
        }
    }
    var isCallScreeningRoleHeld by remember { mutableStateOf(true) }

    fun checkRoleHealth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null) {
            isCallScreeningRoleHeld = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        }
    }

    val requestRoleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        checkRoleHealth()
    }

    LaunchedEffect(Unit) {
        checkRoleHealth()
    }

    val isDetailedMode = LocalDetailedMode.current

    val topBarTitle = when (pagerState.currentPage) {
        0 -> strings.common.sms
        1 -> strings.common.calls
        else -> strings.common.contacts
    }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                isSearchActive = false
                                globalSearchQuery = ""
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Atrás",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    title = {
                        TextField(
                            value = globalSearchQuery,
                            onValueChange = { globalSearchQuery = it },
                            placeholder = {
                                Text(
                                    when (pagerState.currentPage) {
                                        0 -> "Buscar mensajes..."
                                        1 -> "Buscar llamadas..."
                                        else -> "Buscar contactos o servicios..."
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    actions = {
                        if (globalSearchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { globalSearchQuery = "" },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Limpiar",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Icon(
                                painter = painterResource(com.corta.app.R.drawable.ic_corta_logo_tinted),
                                contentDescription = "Corta",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "Corta",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { isSearchActive = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = "Buscar",
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = onNavigateToProfile,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (!userAvatar.isNullOrBlank()) {
                                        Text(
                                            text = userAvatar ?: "",
                                            fontSize = 18.sp
                                        )
                                    } else {
                                        Text(
                                            text = (userName?.take(1)?.uppercase()?.ifBlank { "U" } ?: "U"),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },

        bottomBar = {
            val navItemColors = NavigationBarItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 0.dp
            ) {
                // 0: Mensajes
                NavigationBarItem(
                    colors = navItemColors,
                    icon = {
                        Icon(
                            if (pagerState.currentPage == 0) Icons.AutoMirrored.Rounded.Message else Icons.AutoMirrored.Outlined.Message,
                            contentDescription = strings.common.sms,
                            modifier = Modifier.size(26.dp)
                        )
                    },
                    label = {
                        Text(
                            strings.common.sms,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (pagerState.currentPage == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    selected = pagerState.currentPage == 0,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } }
                )
                // 1: Llamadas
                NavigationBarItem(
                    colors = navItemColors,
                    icon = {
                        Icon(
                            if (pagerState.currentPage == 1) Icons.Rounded.Call else Icons.Outlined.Call,
                            contentDescription = strings.common.calls,
                            modifier = Modifier.size(26.dp)
                        )
                    },
                    label = {
                        Text(
                            strings.common.calls,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (pagerState.currentPage == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    selected = pagerState.currentPage == 1,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } }
                )
                // 2: Contactos
                NavigationBarItem(
                    colors = navItemColors,
                    icon = {
                        Icon(
                            if (pagerState.currentPage == 2) Icons.Rounded.Contacts else Icons.Outlined.Contacts,
                            contentDescription = strings.common.contacts,
                            modifier = Modifier.size(26.dp)
                        )
                    },
                    label = {
                        Text(
                            strings.common.contacts,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (pagerState.currentPage == 2) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    selected = pagerState.currentPage == 2,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            if (!isCallScreeningRoleHeld) {
                RoleHealthBanner(
                    onFixClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null) {
                            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                            requestRoleLauncher.launch(intent)
                        }
                    }
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                HorizontalPager(
                    state = pagerState,
                    beyondBoundsPageCount = 1,
                    userScrollEnabled = selectedSmsThreadId == null && !isComposingSms,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> {
                            // Mensajes SMS
                            if (selectedSmsThreadId != null) {
                                BackHandler { selectedSmsThreadId = null }
                                SmsThreadScreen(
                                    threadId = selectedSmsThreadId!!,
                                    repository = smsRepository,
                                    onBack = { selectedSmsThreadId = null }
                                )
                            } else if (isComposingSms) {
                                BackHandler { isComposingSms = false }
                                ComposeSmsScreen(onBack = { isComposingSms = false })
                            } else {
                                SmsScreen(
                                    repository = smsRepository,
                                    onThreadClick = { threadId -> selectedSmsThreadId = threadId },
                                    onComposeNew = { isComposingSms = true },
                                    searchQuery = globalSearchQuery,
                                    isProtectionMode = false
                                )
                            }
                        }
                        1 -> {
                            // Registro de Llamadas
                            CallLogScreen(searchQuery = globalSearchQuery)
                        }
                        2 -> {
                            // Contactos
                            ContactsScreen(
                                repository = contactRepository,
                                onContactClick = { contact ->
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phoneNumber}"))
                                    context.startActivity(intent)
                                },
                                onCallClick = { contact ->
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phoneNumber}"))
                                    context.startActivity(intent)
                                },
                                onMessageClick = { contact ->
                                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${contact.phoneNumber}"))
                                    context.startActivity(intent)
                                },
                                searchQuery = globalSearchQuery,
                                onSearchQueryChange = { globalSearchQuery = it }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RoleHealthBanner(
    onFixClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Filtrado de llamadas inactivo",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Asigna Corta para bloquear llamadas spam automáticamente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            TextButton(onClick = onFixClick) {
                Text("Activar", fontWeight = FontWeight.Bold)
            }
        }
    }
}
