package com.corta.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.corta.app.ui.i18n.Strings
import android.content.Context
import com.corta.app.ui.theme.ThemeMode
import com.corta.app.ui.theme.AccentColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentLanguage: String = "es",
    onLanguageChange: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("corta_prefs", Context.MODE_PRIVATE) }
    
    var currentThemeMode by remember { mutableStateOf(ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)) }
    var currentAccentColor by remember { mutableStateOf(AccentColor.valueOf(prefs.getString("accent_color", AccentColor.DYNAMIC.name) ?: AccentColor.DYNAMIC.name)) }
    var protectSmsLinks by remember { mutableStateOf(prefs.getBoolean("protect_sms_links", true)) }
    var smsSpamNotifications by remember { mutableStateOf(prefs.getBoolean("sms_spam_notifications_enabled", true)) }
    var showAboutDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(Strings.settings, style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item {
                SettingsSectionHeader("Personalización")
                
                SettingsSelectableItem(
                    title = "Tema Visual",
                    subtitle = when(currentThemeMode) { 
                        ThemeMode.LIGHT -> "Modo Claro"
                        ThemeMode.DARK -> "Modo Oscuro"
                        ThemeMode.SYSTEM -> "Seguir al sistema" 
                    },
                    icon = Icons.Rounded.Contrast,
                    options = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM),
                    optionLabel = { mode -> when(mode) { ThemeMode.LIGHT -> "Claro"; ThemeMode.DARK -> "Oscuro"; ThemeMode.SYSTEM -> "Sistema" } },
                    onOptionSelected = { mode ->
                        prefs.edit().putString("theme_mode", mode.name).apply()
                        currentThemeMode = mode
                    }
                )

                SettingsSelectableItem(
                    title = "Color de Acento",
                    subtitle = when (currentAccentColor) {
                        AccentColor.DYNAMIC -> "Dinámico (Material You)"
                        AccentColor.EXECUTIVE -> "Executive Class"
                        AccentColor.OCEAN -> "Ocean Blue"
                        AccentColor.FOREST -> "Forest Green"
                    },
                    icon = Icons.Rounded.Palette,
                    options = listOf(AccentColor.DYNAMIC, AccentColor.EXECUTIVE, AccentColor.OCEAN, AccentColor.FOREST),
                    optionLabel = { color -> 
                        when (color) {
                            AccentColor.DYNAMIC -> "Dinámico"
                            AccentColor.EXECUTIVE -> "Executive"
                            AccentColor.OCEAN -> "Ocean"
                            AccentColor.FOREST -> "Forest"
                        }
                    },
                    onOptionSelected = { color ->
                        prefs.edit().putString("accent_color", color.name).apply()
                        currentAccentColor = color
                    }
                )

                SettingsSelectableItem(
                    title = "Idioma",
                    subtitle = if (currentLanguage == "es") "Español" else "English",
                    icon = Icons.Rounded.Language,
                    options = listOf("es", "en"),
                    optionLabel = { if (it == "es") "Español" else "English" },
                    onOptionSelected = onLanguageChange
                )
            }

            item {
                SettingsSectionHeader("Seguridad")
                
                SettingsSwitchItem(
                    title = "Protección de Enlaces",
                    subtitle = "Confirmar antes de abrir enlaces sospechosos",
                    icon = Icons.Rounded.VerifiedUser,
                    checked = protectSmsLinks,
                    onCheckedChange = { 
                        protectSmsLinks = it
                        prefs.edit().putBoolean("protect_sms_links", it).apply()
                    }
                )

                SettingsSwitchItem(
                    title = "Filtrado en Tiempo Real",
                    subtitle = "Notificar sobre spam detectado",
                    icon = Icons.Rounded.Shield,
                    checked = smsSpamNotifications,
                    onCheckedChange = { 
                        smsSpamNotifications = it
                        prefs.edit().putBoolean("sms_spam_notifications_enabled", it).apply()
                    }
                )

                SettingsActionItem(
                    title = "Gestión de Permisos",
                    subtitle = "Configurar accesos del sistema",
                    icon = Icons.Rounded.AppSettingsAlt,
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                )
            }

            item {
                SettingsSectionHeader("Información")
                
                SettingsActionItem(
                    title = "Acerca de Corta!",
                    subtitle = "Versión 1.0.0 • Desarrollado por Axioma",
                    icon = Icons.Rounded.AutoAwesome,
                    onClick = { showAboutDialog = true }
                )
            }
        }
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodyMedium) },
        leadingContent = { 
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
        },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    )
}

@Composable
fun SettingsActionItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodyMedium) },
        leadingContent = { 
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                }
            }
        },
        trailingContent = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.outline) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SettingsSelectableItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    options: List<T>,
    optionLabel: (T) -> String,
    onOptionSelected: (T) -> Unit
) {
    var showSheet by remember { mutableStateOf(false) }
    
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodyMedium) },
        leadingContent = { 
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                }
            }
        },
        trailingContent = { 
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                Icon(Icons.Rounded.UnfoldMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp).size(18.dp))
            }
        },
        modifier = Modifier.clickable { showSheet = true }
    )

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            dragHandle = { BottomSheetDefaults.DragHandle() },
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 40.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
                options.forEach { option ->
                    val isSelected = subtitle.contains(optionLabel(option), ignoreCase = true)
                    Surface(
                        onClick = {
                            onOptionSelected(option)
                            showSheet = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = isSelected, onClick = null)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = optionLabel(option),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) },
        title = { Text("Corta!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Esta aplicación ha sido diseñada para proteger tu tranquilidad digital por Axioma. Tus datos nunca salen de este dispositivo.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://donations.url/corta")))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Icon(Icons.Rounded.VolunteerActivism, null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Apoyar al Desarrollador")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar", fontWeight = FontWeight.Bold) }
        },
        shape = MaterialTheme.shapes.extraLarge
    )
}
