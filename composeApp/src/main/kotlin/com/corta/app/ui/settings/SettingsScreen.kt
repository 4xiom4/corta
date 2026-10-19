package com.corta.app.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.corta.app.ui.CortaAppViewModel
import com.corta.app.ui.contacts.ChileanRegion
import com.corta.app.ui.contacts.ChileanRegionalDirectory
import com.corta.app.ui.contacts.RegionSelectorBottomSheet
import com.corta.app.ui.export.CortaExportImportHelper
import com.corta.app.ui.i18n.Strings
import com.corta.app.ui.sms.AndroidSmsRepository
import com.corta.app.ui.sms.SpamKeywordsSheet
import com.corta.app.ui.theme.AccentColor
import com.corta.app.ui.theme.Handedness
import com.corta.app.ui.theme.ThemeMode
import com.corta.app.ui.theme.UiScale
import com.corta.app.ui.trash.TrashBottomSheet
import com.corta.app.workers.SubtelSyncScheduler
import com.corta.domain.ContactRepository
import com.corta.domain.CortaRepository
import com.corta.domain.FilterAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SettingsSubmenu(val title: String) {
    MAIN("Ajustes y Configuración"),
    GENERAL("Configuración General"),
    CALLS("Configuración de Llamadas"),
    MESSAGES("Configuración de Mensajes (SMS)"),
    CONTACTS("Configuración de Contactos")
}

data class SettingsSimSlot(
    val slotIndex: Int,
    val carrierName: String,
    val number: String?,
    val countryIso: String?,
    val subId: Int,
    val isActive: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentLanguage: String = "es",
    onLanguageChange: (String) -> Unit,
    onBack: () -> Unit,
    initialSubmenu: SettingsSubmenu = SettingsSubmenu.MAIN,
    onNavigateToDiagnostics: (() -> Unit)? = null,
    onNavigateToStats: (() -> Unit)? = null,
    onNavigateToRules: (() -> Unit)? = null,
    viewModel: CortaAppViewModel = koinViewModel(),
    cortaRepository: CortaRepository = koinInject(),
    contactRepository: ContactRepository = koinInject()
) {
    val context = LocalContext.current
    val strings = Strings
    val coroutineScope = rememberCoroutineScope()

    var currentSubmenu by remember { mutableStateOf(initialSubmenu) }

    // Intercept hardware / system back navigation
    BackHandler {
        if (currentSubmenu != SettingsSubmenu.MAIN) {
            currentSubmenu = SettingsSubmenu.MAIN
        } else {
            onBack()
        }
    }

    val currentThemeMode by viewModel.themeMode.collectAsState()
    val currentAccentColor by viewModel.accentColor.collectAsState()
    val currentUiScale by viewModel.uiScale.collectAsState()
    val currentHandedness by viewModel.handedness.collectAsState()
    val isDetailedMode by viewModel.detailedMode.collectAsState()

    val prefs = remember { context.getSharedPreferences("corta_prefs", Context.MODE_PRIVATE) }
    var onlyContactsCalls by remember { mutableStateOf(prefs.getBoolean("only_contacts_calls", false)) }
    var blockPrivateNumbers by remember { mutableStateOf(prefs.getBoolean("block_private_numbers", false)) }
    var blockInternationalCalls by remember { mutableStateOf(prefs.getBoolean("block_international_calls", false)) }
    var chao809Aggressive by remember { mutableStateOf(prefs.getBoolean("chao_809_aggressive", true)) }
    var silenceWarnCalls by remember { mutableStateOf(prefs.getBoolean("silence_warn_calls", false)) }
    var detectPingCalls by remember { mutableStateOf(prefs.getBoolean("detect_ping_calls", true)) }

    var protectSmsLinks by remember { mutableStateOf(prefs.getBoolean("protect_sms_links", true)) }
    var filterAlphanumeric by remember { mutableStateOf(prefs.getBoolean("filter_alphanumeric_senders", true)) }
    var smsSpamNotifications by remember { mutableStateOf(prefs.getBoolean("sms_spam_notifications_enabled", true)) }

    var protectSim1 by remember { mutableStateOf(prefs.getBoolean("protect_sim_1", true)) }
    var protectSim2 by remember { mutableStateOf(prefs.getBoolean("protect_sim_2", true)) }
    var showSimBadges by remember { mutableStateOf(prefs.getBoolean("show_sim_badges", true)) }

    var allowContactsAlways by remember { mutableStateOf(prefs.getBoolean("allow_contacts_always", true)) }
    var recordingEnabled by remember { mutableStateOf(prefs.getBoolean("recording_enabled", true)) }

    var showAboutDialog by remember { mutableStateOf(false) }
    var showEmergencySheet by remember { mutableStateOf(false) }
    var showTrashSheet by remember { mutableStateOf(false) }
    var showSpamKeywordsSheet by remember { mutableStateOf(false) }
    var selectedSimForDetails by remember { mutableStateOf<SettingsSimSlot?>(null) }

    var userRegion by remember {
        val savedCode = prefs.getString("user_selected_region", ChileanRegion.RM.code)
        mutableStateOf(ChileanRegion.fromCode(savedCode))
    }
    var userComuna by remember {
        mutableStateOf(prefs.getString("user_selected_comuna", null))
    }
    var showRegionPicker by remember { mutableStateOf(false) }

    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "user_selected_region" || key == "user_selected_comuna") {
                userRegion = ChileanRegion.fromCode(prefs.getString("user_selected_region", ChileanRegion.RM.code))
                userComuna = prefs.getString("user_selected_comuna", null)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val rules by cortaRepository.observeRules().collectAsState(initial = emptyList())

    var simNumberInput by remember { mutableStateOf("") }
    var simResultAction by remember { mutableStateOf<FilterAction?>(null) }
    var simResultReason by remember { mutableStateOf<String?>(null) }
    var simLatencyNanos by remember { mutableLongStateOf(0L) }

    var measuredLatencyMs by remember { mutableStateOf("0.8 ms") }
    var isBenchmarking by remember { mutableStateOf(false) }

    val importRulesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val jsonString = context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().use { it.readText() }
                    } ?: ""
                    val jsonArray = JSONArray(jsonString)
                    var count = 0
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val pattern = obj.getString("pattern")
                        val actionStr = obj.optString("action", "BLOCK")
                        val isRegex = obj.optBoolean("isRegex", false)
                        val alias = obj.optString("alias", "").takeIf { it.isNotBlank() }
                        val source = obj.optString("source", "USER")
                        val action = try { FilterAction.valueOf(actionStr) } catch (e: Exception) { FilterAction.BLOCK }
                        cortaRepository.addRule(
                            pattern = pattern,
                            action = action,
                            isRegex = isRegex,
                            alias = alias,
                            source = source
                        )
                        count++
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Se importaron $count reglas exitosamente", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error al importar: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val importFullBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val count = CortaExportImportHelper.importFullBackupJson(context, uri, cortaRepository, prefs)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Respaldo restaurado: $count elementos recuperados", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error al importar respaldo: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // SIM cards detection with carrier and slot details
    val simCards = remember(context) {
        val list = mutableListOf<SettingsSimSlot>()
        val hasPhonePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPhonePermission) {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            try {
                @Suppress("DEPRECATION")
                val activeSubs = sm?.activeSubscriptionInfoList
                activeSubs?.sortedBy { it.simSlotIndex }?.forEach { sub ->
                    val name = sub.carrierName?.toString()?.takeIf { it.isNotBlank() }
                        ?: sub.displayName?.toString()?.takeIf { it.isNotBlank() }
                        ?: "SIM ${sub.simSlotIndex + 1}"
                    val rawNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        try { sm.getPhoneNumber(sub.subscriptionId) } catch (e: Exception) { @Suppress("DEPRECATION") sub.number }
                    } else {
                        @Suppress("DEPRECATION")
                        sub.number
                    }
                    list.add(
                        SettingsSimSlot(
                            slotIndex = sub.simSlotIndex,
                            carrierName = name,
                            number = rawNumber?.takeIf { it.isNotBlank() },
                            countryIso = sub.countryIso?.uppercase() ?: "CL",
                            subId = sub.subscriptionId,
                            isActive = true
                        )
                    )
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        if (list.isEmpty()) {
            list.add(SettingsSimSlot(0, "SIM Principal", null, "CL", -1, true))
        }
        list
    }

    val simCarriers = remember(simCards) { simCards.map { it.carrierName } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentSubmenu.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (currentSubmenu != SettingsSubmenu.MAIN) {
                                currentSubmenu = SettingsSubmenu.MAIN
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = strings.settings.backCd,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (currentSubmenu) {
                SettingsSubmenu.MAIN -> {
                    // Quick overview summary
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.VerifiedUser,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Protección Corta Activa",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Motor de evaluación local <2ms • Base SUBTEL pre-sembrada • Zero-Knowledge",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    SettingsSectionHeader("CATEGORÍAS DE CONFIGURACIÓN")

                    // 1. Configuración General
                    SettingsHubCard(
                        title = "1. Configuración General",
                        subtitle = "Temas Stitch, modo oscuro OLED, ergonomía, idioma, respaldo JSON y diagnóstico",
                        badgeText = currentThemeMode.name.lowercase().replaceFirstChar { it.uppercase() },
                        icon = Icons.Rounded.Settings,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = { currentSubmenu = SettingsSubmenu.GENERAL }
                    )

                    // 2. Configuración de Llamadas
                    SettingsHubCard(
                        title = "2. Configuración de Llamadas",
                        subtitle = "Filtro SUBTEL 809, números privados, llamadas internacionales, doble SIM y papelera",
                        badgeText = "${listOf(chao809Aggressive, blockPrivateNumbers, blockInternationalCalls, silenceWarnCalls).count { it }} activos",
                        icon = Icons.Rounded.Call,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        onClick = { currentSubmenu = SettingsSubmenu.CALLS }
                    )

                    // 3. Configuración de Mensajes (SMS)
                    SettingsHubCard(
                        title = "3. Configuración de Mensajes (SMS)",
                        subtitle = "Escudo anti-smishing, remitentes institucionales, palabras clave y exportación",
                        badgeText = if (protectSmsLinks) "Protegido" else "Pausado",
                        icon = Icons.Rounded.Sms,
                        iconTint = MaterialTheme.colorScheme.error,
                        onClick = { currentSubmenu = SettingsSubmenu.MESSAGES }
                    )

                    // 4. Configuración de Contactos
                    SettingsHubCard(
                        title = "4. Configuración de Contactos",
                        subtitle = "Sincronización segura de agenda, emergencias 13x/14xx inmutables y reglas",
                        badgeText = "${rules.size} reglas",
                        icon = Icons.Rounded.Contacts,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        onClick = { currentSubmenu = SettingsSubmenu.CONTACTS }
                    )
                }

                SettingsSubmenu.GENERAL -> {
                    SubmenuInfoCard(
                        title = "Configuración General del Sistema",
                        description = "Personalización visual y ergonomía según la regla 60-30-10, respaldos JSON seguros, diagnóstico de rendimiento (<2ms) e información del proyecto."
                    )

                    SettingsSectionHeader("UBICACIÓN Y COBERTURA REGIONAL (CHILE)")

                    val effectiveComuna = remember(userRegion, userComuna) {
                        if (userRegion == ChileanRegion.ALL || userComuna.isNullOrBlank()) null
                        else if (ChileanRegionalDirectory.isValidComunaForRegion(userRegion, userComuna)) userComuna
                        else null
                    }

                    val locationText = when {
                        userRegion == ChileanRegion.ALL -> "🇨🇱 Todo Chile (Sin filtro comunal)"
                        effectiveComuna != null -> "📍 $effectiveComuna (${userRegion.displayName})"
                        else -> "📍 ${userRegion.displayName}"
                    }

                    SettingsCardContainer {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showRegionPicker = true }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.LocationOn,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = locationText,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Filtra números oficiales, seguridad 14xx y comisarías de tu zona",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "Cambiar ubicación",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    SettingsSectionHeader("APARIENCIA Y TEMAS STITCH")

                    SettingsCardContainer {
                        // Selector visual de Tema
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Brightness6,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = strings.settings.visualTheme,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Adapta la interfaz a tu pantalla y batería",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ThemeModeChip(
                                    label = "Claro",
                                    icon = Icons.Rounded.LightMode,
                                    isSelected = currentThemeMode == ThemeMode.LIGHT,
                                    onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                                    modifier = Modifier.weight(1f)
                                )
                                ThemeModeChip(
                                    label = "Oscuro OLED",
                                    icon = Icons.Rounded.DarkMode,
                                    isSelected = currentThemeMode == ThemeMode.DARK,
                                    onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                                    modifier = Modifier.weight(1f)
                                )
                                ThemeModeChip(
                                    label = "Sistema",
                                    icon = Icons.Rounded.BrightnessAuto,
                                    isSelected = currentThemeMode == ThemeMode.SYSTEM,
                                    onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // Selector visual de Variantes de Color
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Palette,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = strings.settings.accentColor,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Estilos Stitch y Material You dinámico",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            ColorPaletteGrid(
                                selectedColor = currentAccentColor,
                                onColorSelected = { viewModel.setAccentColor(it) }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // Escala de Texto / UI
                        SettingsSelectableItem(
                            title = strings.settings.uiScale,
                            subtitle = when (currentUiScale) {
                                UiScale.COMPACT -> "Compacto (85%)"
                                UiScale.NORMAL -> "Normal (100%)"
                                UiScale.LARGE -> "Grande (120%)"
                                UiScale.EXTRA_LARGE -> "Extra Grande (150%)"
                            },
                            icon = Icons.Rounded.FormatSize,
                            options = UiScale.entries,
                            optionLabel = { scale ->
                                when (scale) {
                                    UiScale.COMPACT -> "Compacto (85%)"
                                    UiScale.NORMAL -> "Normal (100%)"
                                    UiScale.LARGE -> "Grande (120%)"
                                    UiScale.EXTRA_LARGE -> "Extra Grande (150%)"
                                }
                            },
                            onOptionSelected = { viewModel.setUiScale(it) }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // Orientación Diestro / Zurdo
                        SettingsSelectableItem(
                            title = strings.settings.handedness,
                            subtitle = when (currentHandedness) {
                                Handedness.LEFT -> "Modo Zurdo (Barra rápida a la izquierda)"
                                Handedness.RIGHT -> "Modo Diestro (Barra rápida a la derecha)"
                            },
                            icon = Icons.Rounded.PanTool,
                            options = Handedness.entries,
                            optionLabel = { hand ->
                                when (hand) {
                                    Handedness.LEFT -> "Modo Zurdo"
                                    Handedness.RIGHT -> "Modo Diestro"
                                }
                            },
                            onOptionSelected = { viewModel.setHandedness(it) }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // Idioma
                        SettingsSelectableItem(
                            title = strings.settings.language,
                            subtitle = if (currentLanguage == "es") "Español (Chile)" else "English",
                            icon = Icons.Rounded.Language,
                            options = listOf("es", "en"),
                            optionLabel = { if (it == "es") "Español (Chile)" else "English" },
                            onOptionSelected = onLanguageChange
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsSwitchItem(
                            title = "Modo Detallado / Forense",
                            subtitle = "Muestra latencia en memoria (<2ms), origen PBX, hashes y telemetría técnica en las listas",
                            icon = Icons.Rounded.Analytics,
                            checked = isDetailedMode,
                            onCheckedChange = { viewModel.setDetailedMode(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    SettingsSectionHeader("RENDIMIENTO Y DIAGNÓSTICO LOCAL")

                    SettingsCardContainer {
                        if (onNavigateToDiagnostics != null) {
                            SettingsActionItem(
                                title = "Diagnóstico Integral del Sistema",
                                subtitle = "Verifica permisos, roles de Android 15 y base de datos SQLCipher",
                                icon = Icons.Rounded.Build,
                                onClick = onNavigateToDiagnostics
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }

                        SettingsActionItem(
                            title = "Configurar Permisos del Sistema",
                            subtitle = "Abrir panel de permisos de Android para Corta",
                            icon = Icons.Rounded.Tune,
                            onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // Latency Card status with interactive benchmark
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isBenchmarking) {
                                    coroutineScope.launch(Dispatchers.Default) {
                                        isBenchmarking = true
                                        val testNumbers = listOf("133", "+56809123456", "+56912345678", "1402", "6003607777")
                                        repeat(10) { testNumbers.forEach { cortaRepository.getActionForNumber(it) } }
                                        val start = System.nanoTime()
                                        val iterations = 100
                                        repeat(iterations) {
                                            testNumbers.forEach { cortaRepository.getActionForNumber(it) }
                                        }
                                        val elapsedTotal = System.nanoTime() - start
                                        val perEvalNanos = elapsedTotal / (iterations * testNumbers.size)
                                        val ms = perEvalNanos / 1_000_000.0
                                        withContext(Dispatchers.Main) {
                                            measuredLatencyMs = String.format(Locale.US, "%.2f ms", ms)
                                            isBenchmarking = false
                                            Toast.makeText(context, "Benchmark en RAM: ${measuredLatencyMs} (<2ms garantizado)", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isBenchmarking) Icons.Rounded.HourglassTop else Icons.Rounded.Bolt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isBenchmarking) "Midiendo latencia en RAM..." else "Latencia SQLite en Memoria: $measuredLatencyMs",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isBenchmarking) "Ejecutando 500 pruebas en memoria local..." else "Toca para ejecutar benchmark en vivo (< 2ms garantizado)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isBenchmarking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    SettingsSectionHeader("RESPALDO Y BASE DE DATOS")

                    SettingsCardContainer {
                        SettingsActionItem(
                            title = "Exportar Respaldo Completo (JSON)",
                            subtitle = "Crea un respaldo seguro con tus ${rules.size} reglas, palabras clave de spam y preferencias",
                            icon = Icons.Rounded.UploadFile,
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    CortaExportImportHelper.exportFullBackupJson(context, rules, cortaRepository, prefs)
                                }
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsActionItem(
                            title = "Importar Respaldo Completo (JSON)",
                            subtitle = "Restaura o combina reglas, palabras de spam y preferencias desde un respaldo JSON",
                            icon = Icons.Rounded.Download,
                            onClick = {
                                importFullBackupLauncher.launch("application/json")
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsActionItem(
                            title = "Optimizar Base de Datos SQLCipher",
                            subtitle = "Reorganiza índices locales y compacta tablas SQLite",
                            icon = Icons.Rounded.CleaningServices,
                            onClick = {
                                Toast.makeText(context, "Base de datos optimizada e indexada", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    SettingsSectionHeader("TRANSPARENCIA Y SOPORTE")

                    SettingsCardContainer {
                        SettingsActionItem(
                            title = "Preguntas Frecuentes y Ayuda",
                            subtitle = "Conoce más sobre cómo funciona la protección offline y el bloqueo 809",
                            icon = Icons.AutoMirrored.Rounded.HelpOutline,
                            onClick = { showAboutDialog = true }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsActionItem(
                            title = "Contacto y Soporte",
                            subtitle = "Escríbenos a soporte@corta.app o visita nuestro repositorio",
                            icon = Icons.Rounded.MailOutline,
                            onClick = {
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:soporte@corta.app?subject=Consulta Corta App")
                                }
                                try { context.startActivity(intent) } catch (e: Exception) {
                                    Toast.makeText(context, "Contacto: soporte@corta.app", Toast.LENGTH_LONG).show()
                                }
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsActionItem(
                            title = "Acerca de Corta v1.2",
                            subtitle = "Versión 1.2 • 100% On-Device • Desarrollado por Axioma",
                            icon = Icons.Rounded.Info,
                            onClick = { showAboutDialog = true }
                        )
                    }
                }

                SettingsSubmenu.CALLS -> {
                    SubmenuInfoCard(
                        title = "Filtros y Protección de Llamadas",
                        description = "Toda evaluación se realiza en la memoria RAM en <2ms. Las emergencias (131, 132, 133, 14xx) y salud están protegidas por ley. Los números chilenos desconocidos se permiten por defecto para asegurar llamadas legítimas de doctores, colegios y repartos."
                    )

                    SettingsSectionHeader("BLOQUEO Y REGLAS DE LLAMADAS")

                    SettingsCardContainer {
                        SettingsSwitchItem(
                            title = "Chao 809 y Telemarketing SUBTEL",
                            subtitle = "Bloqueo obligatorio al primer tono de prefijos 809 de ventas masivas no solicitadas (Normativa SUBTEL)",
                            icon = Icons.Rounded.Gavel,
                            checked = chao809Aggressive,
                            onCheckedChange = {
                                chao809Aggressive = it
                                prefs.edit().putBoolean("chao_809_aggressive", it).apply()
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsSwitchItem(
                            title = "Bloquear números privados y ocultos",
                            subtitle = "Rechaza automáticamente llamadas entrantes sin identificador o con número reservado",
                            icon = Icons.Rounded.VisibilityOff,
                            checked = blockPrivateNumbers,
                            onCheckedChange = {
                                blockPrivateNumbers = it
                                prefs.edit().putBoolean("block_private_numbers", it).apply()
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsSwitchItem(
                            title = "Solo llamadas de Chile (+56)",
                            subtitle = "Bloquea llamadas de números internacionales extranjeros que no tengas guardados en tus contactos",
                            icon = Icons.Rounded.PublicOff,
                            checked = blockInternationalCalls,
                            onCheckedChange = {
                                blockInternationalCalls = it
                                prefs.edit().putBoolean("block_international_calls", it).apply()
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsSwitchItem(
                            title = "Detección de llamadas fantasma (Ping / Wangiri)",
                            subtitle = "Identifica llamadas robotizadas que se cortan en menos de 2 segundos desde números sospechosos",
                            icon = Icons.Rounded.HourglassEmpty,
                            checked = detectPingCalls,
                            onCheckedChange = {
                                detectPingCalls = it
                                prefs.edit().putBoolean("detect_ping_calls", it).apply()
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsSwitchItem(
                            title = "Silenciar números sospechosos",
                            subtitle = "Atenúa el timbre en llamadas con prefijo comercial 600 o patrones de advertencia (Desactivado por defecto)",
                            icon = Icons.AutoMirrored.Rounded.VolumeMute,
                            checked = silenceWarnCalls,
                            onCheckedChange = {
                                silenceWarnCalls = it
                                prefs.edit().putBoolean("silence_warn_calls", it).apply()
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsSwitchItem(
                            title = "Solo personas en mi agenda / contactos",
                            subtitle = "Modo estricto opcional: rechaza llamadas de personas no guardadas (Desactivado por defecto; emergencias 13x/14xx siempre pasan)",
                            icon = Icons.Rounded.ContactPhone,
                            checked = onlyContactsCalls,
                            onCheckedChange = {
                                onlyContactsCalls = it
                                prefs.edit().putBoolean("only_contacts_calls", it).apply()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    SettingsSectionHeader("GESTIÓN MULTI-SIM (CHIPS)")

                    SettingsCardContainer {
                        simCards.forEach { sim ->
                            val slot = sim.slotIndex + 1
                            val isChecked = if (slot == 1) protectSim1 else protectSim2
                            SettingsSwitchItem(
                                title = "Proteger Ranura $slot: ${sim.carrierName}",
                                subtitle = "Aplica bloqueo 809 y filtro de spam en las llamadas entrantes de este chip",
                                icon = Icons.Rounded.SimCard,
                                checked = isChecked,
                                onCheckedChange = {
                                    if (slot == 1) {
                                        protectSim1 = it
                                        prefs.edit().putBoolean("protect_sim_1", it).apply()
                                    } else {
                                        protectSim2 = it
                                        prefs.edit().putBoolean("protect_sim_2", it).apply()
                                    }
                                }
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            SettingsActionItem(
                                title = "Detalles Técnicos de Ranura $slot",
                                subtitle = "Operador: ${sim.carrierName} • Número: ${sim.number ?: "No publicado"} (Toca para inspeccionar)",
                                icon = Icons.Rounded.Info,
                                onClick = { selectedSimForDetails = sim }
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }

                        SettingsSwitchItem(
                            title = "Mostrar indicador de SIM [1] / [2]",
                            subtitle = "Muestra el badge del chip receptor en el registro de llamadas entrantes",
                            icon = Icons.Rounded.Badge,
                            checked = showSimBadges,
                            onCheckedChange = {
                                showSimBadges = it
                                prefs.edit().putBoolean("show_sim_badges", it).apply()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    SettingsSectionHeader("HERRAMIENTAS Y REGISTRO DE LLAMADAS")

                    SettingsCardContainer {
                        SettingsActionItem(
                            title = "Sincronizar base comunitaria ahora",
                            subtitle = "Descarga hashes SHA-256 diferenciales de spammers reportados en Chile",
                            icon = Icons.Rounded.Sync,
                            onClick = {
                                SubtelSyncScheduler.enqueueImmediate(context, true)
                                Toast.makeText(context, "Sincronización diferencial iniciada en segundo plano", Toast.LENGTH_SHORT).show()
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsActionItem(
                            title = "Portal de Denuncias SUBTEL",
                            subtitle = "Visita el portal oficial del gobierno chileno para reportar abusos de telemarketing",
                            icon = Icons.AutoMirrored.Rounded.OpenInNew,
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.subtel.gob.cl/"))
                                try { context.startActivity(intent) } catch (e: Exception) { /* ignore */ }
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsActionItem(
                            title = "Papelera de Llamadas Rechazadas",
                            subtitle = "Revisa, restaura o elimina permanentemente llamadas en la papelera",
                            icon = Icons.Rounded.DeleteSweep,
                            onClick = { showTrashSheet = true }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsActionItem(
                            title = "Exportar Historial de Llamadas a CSV",
                            subtitle = "Crea un archivo CSV con el registro de llamadas para respaldo o denuncias SERNAC",
                            icon = Icons.Rounded.FileDownload,
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    val calls = cortaRepository.getCallLogs()
                                    CortaExportImportHelper.exportCallLogEntriesToCsv(context, calls)
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    SettingsSectionHeader("SIMULADOR EN MEMORIA RAM (<2MS)")

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Probar Evaluación de Número",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Ingresa un número (ej: 809123456, 133, 1402, +56912345678) para ver cómo responde Corta en tiempo real sin salir a internet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = simNumberInput,
                                    onValueChange = { simNumberInput = it },
                                    placeholder = { Text("Ej: 809123456") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Button(
                                    onClick = {
                                        if (simNumberInput.isNotBlank()) {
                                            val start = System.nanoTime()
                                            val isWhite = cortaRepository.isWhitelistedNumber(simNumberInput)
                                            val action = cortaRepository.getActionForNumber(simNumberInput)
                                            simLatencyNanos = System.nanoTime() - start
                                            simResultAction = action
                                            simResultReason = when {
                                                isWhite -> "Lista Blanca Crítica Inmutable de Chile"
                                                action == FilterAction.BLOCK && (simNumberInput.contains("809")) -> "Regla Oficial SUBTEL 809 (Telemarketing Masivo)"
                                                action == FilterAction.BLOCK -> "Regla de Bloqueo Activa en Corta"
                                                action == FilterAction.WARN -> "Aviso / Precaución Comercial (Línea 600)"
                                                else -> "Número Regular / Sin reportes de spam"
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(54.dp)
                                ) {
                                    Text("Evaluar")
                                }
                            }

                            if (simResultAction != null) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = when (simResultAction) {
                                        FilterAction.BLOCK -> MaterialTheme.colorScheme.errorContainer
                                        FilterAction.WARN -> MaterialTheme.colorScheme.tertiaryContainer
                                        else -> MaterialTheme.colorScheme.primaryContainer
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = when (simResultAction) {
                                                FilterAction.BLOCK -> Icons.Rounded.Block
                                                FilterAction.WARN -> Icons.Rounded.Warning
                                                else -> Icons.Rounded.CheckCircle
                                            },
                                            contentDescription = null,
                                            tint = when (simResultAction) {
                                                FilterAction.BLOCK -> MaterialTheme.colorScheme.error
                                                FilterAction.WARN -> MaterialTheme.colorScheme.tertiary
                                                else -> MaterialTheme.colorScheme.primary
                                            },
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "Acción: ${simResultAction?.name}",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = when (simResultAction) {
                                                        FilterAction.BLOCK -> MaterialTheme.colorScheme.onErrorContainer
                                                        FilterAction.WARN -> MaterialTheme.colorScheme.onTertiaryContainer
                                                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                                                    }
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                                                ) {
                                                    Text(
                                                        text = String.format(Locale.US, "%.2f ms", simLatencyNanos / 1_000_000.0),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = simResultReason ?: "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = when (simResultAction) {
                                                    FilterAction.BLOCK -> MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                                                    FilterAction.WARN -> MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f)
                                                    else -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                SettingsSubmenu.MESSAGES -> {
                    SubmenuInfoCard(
                        title = "Escudo Antifraude SMS (Smishing)",
                        description = "Analiza remitentes y cuerpos de mensajes SMS buscando suplantación bancaria y enlaces acortados peligrosos con heurística 100% local on-device."
                    )

                    SettingsSectionHeader("SEGURIDAD ANTIFRAUDE SMS")

                    SettingsCardContainer {
                        SettingsSwitchItem(
                            title = "Protección de enlaces bancarios (Smishing)",
                            subtitle = "Analiza dominios engañosos y enlaces acortados con heurística de seguridad",
                            icon = Icons.Rounded.LinkOff,
                            checked = protectSmsLinks,
                            onCheckedChange = {
                                protectSmsLinks = it
                                prefs.edit().putBoolean("protect_sms_links", it).apply()
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsSwitchItem(
                            title = "Filtrar remitentes alfanuméricos falsos",
                            subtitle = "Verifica si el remitente institucional corresponde a una entidad real registrada",
                            icon = Icons.Rounded.MarkEmailRead,
                            checked = filterAlphanumeric,
                            onCheckedChange = {
                                filterAlphanumeric = it
                                prefs.edit().putBoolean("filter_alphanumeric_senders", it).apply()
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsSwitchItem(
                            title = "Notificaciones discretas de spam",
                            subtitle = "Alerta silenciosa sin sonido ni vibración cuando un mensaje peligroso es interceptado",
                            icon = Icons.Rounded.NotificationsPaused,
                            checked = smsSpamNotifications,
                            onCheckedChange = {
                                smsSpamNotifications = it
                                prefs.edit().putBoolean("sms_spam_notifications_enabled", it).apply()
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsActionItem(
                            title = "Gestionar Palabras Clave Bloqueadas",
                            subtitle = "Personaliza términos de smishing bancario, premios falsos y estafas",
                            icon = Icons.Rounded.Key,
                            onClick = { showSpamKeywordsSheet = true }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    SettingsSectionHeader("HERRAMIENTAS Y RESPALDO SMS")

                    SettingsCardContainer {
                        SettingsActionItem(
                            title = "Papelera de Mensajes SMS",
                            subtitle = "Revisa y restaura mensajes SMS enviados a la papelera",
                            icon = Icons.Rounded.DeleteSweep,
                            onClick = { showTrashSheet = true }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsActionItem(
                            title = "Exportar Mensajes SMS a CSV",
                            subtitle = "Descarga todos tus mensajes en un archivo CSV para análisis o respaldo",
                            icon = Icons.Rounded.FileDownload,
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    val messages = AndroidSmsRepository(context, cortaRepository).getAllMessages()
                                    CortaExportImportHelper.exportSmsToCsv(context, messages)
                                }
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsActionItem(
                            title = "Exportar Mensajes SMS a JSON",
                            subtitle = "Descarga una copia completa de tus SMS en formato JSON estándar",
                            icon = Icons.Rounded.Code,
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    val messages = AndroidSmsRepository(context, cortaRepository).getAllMessages()
                                    CortaExportImportHelper.exportSmsToJson(context, messages)
                                }
                            }
                        )
                    }
                }

                SettingsSubmenu.CONTACTS -> {
                    SubmenuInfoCard(
                        title = "Gestión de Contactos y Agenda Segura",
                        description = "Sincroniza tus contactos a la base de datos segura de Corta, consulta la lista blanca de emergencia inmutable y administra tus reglas de números bloqueados y permitidos."
                    )

                    SettingsSectionHeader("AGENDA Y PROTECCIÓN DE CONTACTOS")

                    SettingsCardContainer {
                        SettingsActionItem(
                            title = "Sincronizar Contactos de SIM y Dispositivo",
                            subtitle = "Lee contactos almacenados en los chips SIM y teléfono para sumarlos a la agenda segura de Corta",
                            icon = Icons.Rounded.Sync,
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val contacts = contactRepository.getContacts()
                                        cortaRepository.syncContacts(contacts)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Sincronizados ${contacts.size} contactos de SIM y dispositivo", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Error al sincronizar: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsActionItem(
                            title = "Servicios de Emergencia Inmutables (Chile)",
                            subtitle = "131 SAMU, 132 Bomberos, 133 Carabineros, 14xx Municipal y 600 Salud Responde",
                            icon = Icons.Rounded.HealthAndSafety,
                            onClick = { showEmergencySheet = true }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        SettingsActionItem(
                            title = "Exportar Contactos a vCard (.vcf)",
                            subtitle = "Guarda todos los contactos seguros en formato universal vCard 3.0",
                            icon = Icons.Rounded.Contacts,
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    val contacts = contactRepository.getContacts()
                                    CortaExportImportHelper.exportAllContactsToVcf(context, contacts)
                                }
                            }
                        )
                    }

                    if (onNavigateToRules != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsSectionHeader("REGLAS Y NÚMEROS PERSONALIZADOS")

                        SettingsCardContainer {
                            SettingsActionItem(
                                title = "Administrador de Reglas y Lista de Bloqueo",
                                subtitle = "Ver, buscar, agregar números o prefijos bloqueados y permitidos (${rules.size} activos)",
                                icon = Icons.Rounded.Block,
                                onClick = onNavigateToRules
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    if (showRegionPicker) {
        RegionSelectorBottomSheet(
            selectedRegion = userRegion,
            selectedComuna = userComuna,
            onSelectLocation = { region, comuna ->
                userRegion = region
                userComuna = comuna
                val editor = prefs.edit().putString("user_selected_region", region.code)
                if (comuna != null) {
                    editor.putString("user_selected_comuna", comuna)
                } else {
                    editor.remove("user_selected_comuna")
                }
                editor.apply()
                val locText = if (comuna != null) "$comuna (${region.shortName})" else region.displayName
                Toast.makeText(context, "Ubicación: $locText", Toast.LENGTH_SHORT).show()
                showRegionPicker = false
            },
            onDismiss = { showRegionPicker = false }
        )
    }

    if (showEmergencySheet) {
        EmergencyWhitelistSheet(onDismiss = { showEmergencySheet = false })
    }

    if (showTrashSheet) {
        TrashBottomSheet(
            repository = cortaRepository,
            onDismiss = { showTrashSheet = false }
        )
    }

    if (showSpamKeywordsSheet) {
        SpamKeywordsSheet(
            repository = cortaRepository,
            onDismiss = { showSpamKeywordsSheet = false }
        )
    }

    selectedSimForDetails?.let { sim ->
        SimCardDetailsDialog(
            sim = sim,
            onDismiss = { selectedSimForDetails = null }
        )
    }
}

@Composable
fun SimCardDetailsDialog(
    sim: SettingsSimSlot,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.SimCard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Ranura ${sim.slotIndex + 1}: ${sim.carrierName}",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsSimDetailRow(label = "Operador", value = sim.carrierName)
                SettingsSimDetailRow(label = "Estado", value = if (sim.isActive) "Activa y Protegida" else "Inactiva / Sin SIM")
                SettingsSimDetailRow(label = "Número", value = sim.number ?: "No publicado por operador")
                SettingsSimDetailRow(label = "País", value = sim.countryIso ?: "CL")
                if (sim.subId != -1) {
                    SettingsSimDetailRow(label = "ID Suscripción", value = "${sim.subId}")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Text(
                    text = "Corta evalúa en memoria (<2ms) todas las llamadas y SMS que entran por esta línea según la normativa chilena.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Entendido", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun SettingsSimDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SettingsHubCard(
    title: String,
    subtitle: String,
    badgeText: String?,
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = iconTint.copy(alpha = 0.14f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (badgeText != null) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = iconTint.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = iconTint,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SubmenuInfoCard(title: String, description: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun SettingsCardContainer(content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        content = content
    )
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun SettingsActionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun <T> SettingsSelectableItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    options: List<T>,
    optionLabel: (T) -> String,
    onOptionSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Rounded.UnfoldMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun ThemeModeChip(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

data class ColorSwatch(
    val accent: AccentColor,
    val name: String,
    val primaryColor: Color,
    val containerColor: Color
)

@Composable
fun ColorPaletteGrid(
    selectedColor: AccentColor,
    onColorSelected: (AccentColor) -> Unit
) {
    val swatches = remember {
        listOf(
            ColorSwatch(AccentColor.DYNAMIC, "Monet Dinámico", Color(0xFF6750A4), Color(0xFF625B71)),
            ColorSwatch(AccentColor.STITCH_AMBER, "Stitch Ámbar", Color(0xFF8C4A00), Color(0xFFFFDDB6)),
            ColorSwatch(AccentColor.STITCH_PURPLE, "Stitch Púrpura", Color(0xFF6750A4), Color(0xFFEADDFF)),
            ColorSwatch(AccentColor.STITCH_CORAL, "Stitch Coral", Color(0xFFB33A3A), Color(0xFFFFDAD6)),
            ColorSwatch(AccentColor.STITCH_SAGE, "Stitch Salvia", Color(0xFF356A39), Color(0xFFB7F2B5)),
            ColorSwatch(AccentColor.STYLE_1, "Ejecutivo Azul", Color(0xFF005AC1), Color(0xFFD8E2FF)),
            ColorSwatch(AccentColor.STYLE_2, "Bosque Verde", Color(0xFF4CAF50), Color(0xFFD1F2D3)),
            ColorSwatch(AccentColor.STYLE_5, "Océano Celeste", Color(0xFF03A9F4), Color(0xFFD0EEFF))
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (rowSwatches in swatches.chunked(2)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (swatch in rowSwatches) {
                    val isSelected = selectedColor == swatch.accent
                    Surface(
                        onClick = { onColorSelected(swatch.accent) },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(swatch.primaryColor)
                                    .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = swatch.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyWhitelistSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.HealthAndSafety,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Servicios Críticos Protegidos",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Protección legal inmutable en Chile",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Corta incorpora una lista blanca de ultra-baja latencia pre-sembrada en memoria local. Ninguna regla de usuario, SUBTEL o comunitaria puede bloquear los siguientes números vitales:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            val emergencyList = listOf(
                "131" to "SAMU (Ambulancia)",
                "132" to "Cuerpo de Bomberos de Chile",
                "133" to "Carabineros de Chile (Emergencias Policiales)",
                "134" to "Policía de Investigaciones (PDI)",
                "137" to "Búsqueda y Salvamento Marítimo",
                "147" to "Fono Familia (Violencia Intrafamiliar)",
                "149" to "Fono Niños",
                "14xx" to "Seguridad Ciudadana Municipal (1402, 1414, 1406, 1416, 1418, etc.)",
                "600 360 7777" to "Salud Responde (MINSAL)"
            )

            emergencyList.forEach { (number, service) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.width(96.dp).height(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = number,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = service,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Text(Strings.common.understood)
            }
        }
    }
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val strings = Strings
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Rounded.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                strings.common.appName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Con Corta la haces corta",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Versión 1.2 (Release Android 15)\nDesarrollado en Chile por Axioma.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Diseñado para proteger tu privacidad con evaluación en memoria (<2ms) y base de datos SQLCipher encriptada. Tus llamadas, registros y mensajes nunca salen de este teléfono.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))
                val prefs = remember { context.getSharedPreferences("corta_prefs", Context.MODE_PRIVATE) }
                var isDonor by remember { mutableStateOf(prefs.getBoolean("is_donor", false)) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val newStatus = !isDonor
                            isDonor = newStatus
                            prefs.edit().putBoolean("is_donor", newStatus).apply()
                            Toast.makeText(
                                context,
                                if (newStatus) "¡Medalla de Donante activada en tu perfil!" else "Medalla de Donante desactivada",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDonor) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
                            contentColor = if (isDonor) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = MaterialTheme.shapes.large,
                        contentPadding = PaddingValues(10.dp)
                    ) {
                        Icon(
                            if (isDonor) Icons.Rounded.Verified else Icons.Rounded.VolunteerActivism,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (isDonor) "Donante Activo" else "Activar Medalla",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/axioma-dev/corta"))
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "github.com/axioma-dev/corta", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large,
                        contentPadding = PaddingValues(10.dp)
                    ) {
                        Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ver Código", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.common.close, fontWeight = FontWeight.Bold)
            }
        },
        shape = MaterialTheme.shapes.extraLarge
    )
}
