package com.corta.app.ui.diagnostics

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import com.corta.app.ui.i18n.Strings
import androidx.compose.material3.ExperimentalMaterial3Api
import timber.log.Timber

private const val TAG = "Corta.Diagnostics"

data class DiagnosticItem(
    val title: String,
    val description: String,
    val status: DiagnosticStatus,
    val action: (() -> Unit)? = null,
    val actionText: String? = null
)

private data class RequirementItem(
    val title: String,
    val ok: Boolean,
    val onFix: (() -> Unit)? = null
)

private fun buildMissingRequirements(
    results: Map<String, Boolean>,
    criticalLabel: String,
    dialerLabel: String,
    screeningLabel: String,
    smsLabel: String,
    overlayLabel: String,
    notificationsLabel: String
): List<String> {
    val missing = mutableListOf<String>()
    if (results["critical_permissions"] != true) missing += criticalLabel
    if (results["role_dialer"] != true) missing += dialerLabel
    if (results["role_screening"] != true) missing += screeningLabel
    if (results["role_sms"] != true) missing += smsLabel
    if (results["overlay_permission"] != true) missing += overlayLabel
    if (results["notifications"] != true) missing += notificationsLabel
    return missing
}

enum class DiagnosticStatus {
    PASSED,
    FAILED,
    WARNING,
    INFO
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemDiagnosticsScreen(onBack: () -> Unit, onContinue: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("corta_prefs", Context.MODE_PRIVATE)
    val isDebugMode = prefs.getBoolean("debug_mode", false)
    val isDebugBuild = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val isDebugBypassEnabled = isDebugBuild && isDebugMode

    val strings = Strings
    var diagnostics by remember { mutableStateOf<List<DiagnosticItem>>(emptyList()) }
    var isRunningDiagnostics by remember { mutableStateOf(false) }
    var diagnosticResults by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    fun runDiagnostics() {
        Timber.tag(TAG).d( "Starting diagnostics execution...")
        isRunningDiagnostics = true
        val results = mutableMapOf<String, Boolean>()
        val diagnosticItems = mutableListOf<DiagnosticItem>()

        // 1. Verificar permisos críticos (individualizados)
        val criticalPermissions = mapOf(
            "READ_PHONE_STATE" to Manifest.permission.READ_PHONE_STATE,
            "READ_CALL_LOG" to Manifest.permission.READ_CALL_LOG,
            "WRITE_CALL_LOG" to Manifest.permission.WRITE_CALL_LOG,
            "RECEIVE_SMS" to Manifest.permission.RECEIVE_SMS,
            "SEND_SMS" to Manifest.permission.SEND_SMS,
            "READ_SMS" to Manifest.permission.READ_SMS
        )

        val criticalPermissionsResults = mutableMapOf<String, Boolean>()
        var allCriticalGranted = true

        criticalPermissions.forEach { (name, permission) ->
            val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            criticalPermissionsResults[name] = granted
            Timber.tag(TAG).d( "$name: ${if (granted) "GRANTED" else "DENIED"}")
            if (!granted) allCriticalGranted = false

            if (!granted) {
                diagnosticItems.add(
                    DiagnosticItem(
                        title = "Permiso: $name",
                        description = strings.diagnostics.miscCriticalDesc,
                        status = DiagnosticStatus.FAILED,
                        action = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        actionText = strings.common.configure
                    )
                )
            }
        }

        results["critical_permissions"] = allCriticalGranted

        if (allCriticalGranted) {
            diagnosticItems.add(
                DiagnosticItem(
                    title = strings.diagnostics.miscCritical,
                    description = strings.diagnostics.miscCriticalOk,
                    status = DiagnosticStatus.PASSED
                )
            )
        }

        // 2. Verificar permisos especiales
        val hasOverlayPermission = Settings.canDrawOverlays(context)
        val hasKeyguardPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.DISABLE_KEYGUARD) == PackageManager.PERMISSION_GRANTED
        val specialPermissionsGranted = hasOverlayPermission

        Timber.tag(TAG).d( "SYSTEM_ALERT_WINDOW: ${if (hasOverlayPermission) "GRANTED" else "DENIED"}")
        Timber.tag(TAG).d( "DISABLE_KEYGUARD: ${if (hasKeyguardPermission) "GRANTED" else "DENIED"}")
        results["special_permissions"] = specialPermissionsGranted
        results["overlay_permission"] = hasOverlayPermission

        // Separar en diagnósticos individuales para mejor control
        if (!hasOverlayPermission) {
            Timber.tag(TAG).d( "Adding overlay permission diagnostic item")
            diagnosticItems.add(
                DiagnosticItem(
                    title = strings.diagnostics.overlay,
                    description = strings.diagnostics.overlayDesc,
                    status = DiagnosticStatus.FAILED,
                    action = {
                        Timber.tag(TAG).d( "Overlay permission button clicked")
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = Uri.parse("package:" + context.packageName)
                        }
                        context.startActivity(intent)
                    },
                    actionText = strings.common.configure
                )
            )
        } else {
            Timber.tag(TAG).d( "Overlay permission already granted")
        }

        if (!hasKeyguardPermission) {
            diagnosticItems.add(
                DiagnosticItem(
                    title = strings.diagnostics.unlock,
                    description = strings.diagnostics.unlockDesc,
                    status = DiagnosticStatus.WARNING,
                    action = {
                        val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
                        context.startActivity(intent)
                    },
                    actionText = strings.common.configure
                )
            )
        }

        if (hasOverlayPermission && hasKeyguardPermission) {
            diagnosticItems.add(
                DiagnosticItem(
                    title = strings.diagnostics.special,
                    description = strings.diagnostics.specialOk,
                    status = DiagnosticStatus.PASSED
                )
            )
        }

        // 3. Verificar roles del sistema (individualizados)
        val roleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(android.app.role.RoleManager::class.java)
        } else {
            null
        }
        val hasDialerRole = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_DIALER) ?: false
        } else {
            false
        }
        val hasCallScreeningRole = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING) ?: false
        } else {
            false
        }
        val hasSmsRole = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            (roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_SMS) ?: false)
        val allRolesGranted = hasDialerRole && hasCallScreeningRole && hasSmsRole

        Timber.tag(TAG).d( "ROLE_DIALER: ${if (hasDialerRole) "GRANTED" else "DENIED"}")
        Timber.tag(TAG).d( "ROLE_CALL_SCREENING: ${if (hasCallScreeningRole) "GRANTED" else "DENIED"}")
        Timber.tag(TAG).d( "ROLE_SMS: ${if (hasSmsRole) "GRANTED" else "DENIED"}")
        results["system_roles"] = allRolesGranted
        results["role_dialer"] = hasDialerRole
        results["role_screening"] = hasCallScreeningRole
        results["role_sms"] = hasSmsRole

        // Separar roles individuales para mejor control
        if (!hasDialerRole) {
            diagnosticItems.add(
                DiagnosticItem(
                    title = strings.diagnostics.roleDialer,
                    description = strings.diagnostics.roleDialerDesc,
                    status = DiagnosticStatus.FAILED,
                    action = {
                        val intent = Intent("android.telecom.action.CHANGE_DEFAULT_DIALER").apply {
                            putExtra("android.telecom.extra.CHANGE_DEFAULT_DIALER_PACKAGE_NAME", context.packageName)
                        }
                        context.startActivity(intent)
                    },
                    actionText = strings.common.configure
                )
            )
        }

        if (!hasCallScreeningRole) {
            diagnosticItems.add(
                DiagnosticItem(
                    title = strings.diagnostics.roleScreening,
                    description = strings.diagnostics.roleScreeningDesc,
                    status = DiagnosticStatus.FAILED,
                    action = {
                        val intent = Intent("android.telecom.action.CHANGE_DEFAULT_DIALER").apply {
                            putExtra("android.telecom.extra.CHANGE_DEFAULT_DIALER_PACKAGE_NAME", context.packageName)
                        }
                        context.startActivity(intent)
                    },
                    actionText = strings.common.configure
                )
            )
        }

        if (!hasSmsRole) {
            diagnosticItems.add(
                DiagnosticItem(
                    title = strings.diagnostics.roleSms,
                    description = strings.diagnostics.roleSmsDesc,
                    status = DiagnosticStatus.FAILED,
                    action = {
                        if (roleManager != null) {
                            val intent = Intent(android.provider.Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                                putExtra(android.provider.Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
                            }
                            context.startActivity(intent)
                        }
                    },
                    actionText = strings.common.configure
                )
            )
        }

        if (hasDialerRole && hasCallScreeningRole && hasSmsRole) {
            diagnosticItems.add(
                DiagnosticItem(
                    title = strings.diagnostics.roles,
                    description = strings.diagnostics.rolesOk,
                    status = DiagnosticStatus.PASSED
                )
            )
        }

        // 4. Verificar permisos mejorados (opcionales)
        val enhancedPermissionsList = listOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.VIBRATE
        )
        val enhancedGranted = enhancedPermissionsList.all { perm ->
            if (perm == Manifest.permission.POST_NOTIFICATIONS && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) true
            else ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }

        results["enhanced_features"] = enhancedGranted

        diagnosticItems.add(
            DiagnosticItem(
                title = strings.diagnostics.enhanced,
                description = "Grabación, notificaciones y llamadas directas",
                status = if (enhancedGranted) DiagnosticStatus.PASSED else DiagnosticStatus.WARNING,
                action = null
            )
        )

        // 5. Verificar acceso a contactos
        val contactsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        results["contacts_access"] = contactsPermission

        diagnosticItems.add(
            DiagnosticItem(
                title = strings.diagnostics.contacts,
                description = strings.diagnostics.contactsDesc,
                status = if (contactsPermission) DiagnosticStatus.PASSED else DiagnosticStatus.FAILED,
                action = null
            )
        )

        // 6. Verificar almacenamiento
        val storagePermission = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        } else {
            true // En Android Q+ no se necesita WRITE_EXTERNAL_STORAGE para apps específicas
        }

        results["storage_access"] = storagePermission

        diagnosticItems.add(
            DiagnosticItem(
                title = strings.diagnostics.storage,
                description = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    strings.diagnostics.storageDesc
                } else {
                    "Acceso a almacenamiento (Android Q+)"
                },
                status = if (storagePermission) DiagnosticStatus.PASSED else DiagnosticStatus.FAILED,
                action = null
            )
        )

        // 7. Verificar notificaciones
        val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        results["notifications"] = notificationPermission

        diagnosticItems.add(
            DiagnosticItem(
                title = strings.diagnostics.notifications,
                description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    "Permiso para mostrar notificaciones (Android 13+)"
                } else {
                    "Notificaciones disponibles (Android <13)"
                },
                status = if (notificationPermission) DiagnosticStatus.PASSED else DiagnosticStatus.FAILED,
                action = null
            )
        )

        // 8. Verificar red (usando API moderna para evitar deprecated)
        var networkAvailable = false
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            @Suppress("DEPRECATION")
            val activeNetwork = connectivityManager?.activeNetworkInfo
            networkAvailable = activeNetwork?.isConnected ?: false
        } catch (e: Exception) {
            networkAvailable = false
        }

        results["network"] = networkAvailable

        diagnosticItems.add(
            DiagnosticItem(
                title = strings.diagnostics.network,
                description = if (networkAvailable) {
                    strings.diagnostics.networkOk
                } else {
                    strings.diagnostics.networkOffline
                },
                status = if (networkAvailable) DiagnosticStatus.PASSED else DiagnosticStatus.WARNING,
                action = null
            )
        )

        // 9. Verificar configuración completa
        val allChecksPassed = results.values.all { it }

        Timber.tag(TAG).d( "Resultados: $results")
        Timber.tag(TAG).d( "¿Todos los checks pasaron?: $allChecksPassed")

        diagnosticItems.add(
            DiagnosticItem(
                title = strings.diagnostics.overall,
                description = if (allChecksPassed) {
                    "✅ Todos los sistemas funcionando correctamente"
                } else {
                    "⚠️ Hay problemas que requieren atención"
                },
                status = if (allChecksPassed) DiagnosticStatus.PASSED else DiagnosticStatus.WARNING,
                action = if (!allChecksPassed) ({
                    runDiagnostics()
                }) else null,
                actionText = if (!allChecksPassed) strings.common.retry else null
            )
        )

        diagnostics = diagnosticItems
        diagnosticResults = results
        isRunningDiagnostics = false
        Timber.tag(TAG).d( "Diagnostics completed. Results: $results")
        Timber.tag(TAG).d( "Diagnostic items count: ${diagnosticItems.size}")
    }

    // Ejecutar diagnósticos automáticamente al entrar
    LaunchedEffect(Unit) {
        runDiagnostics()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.diagnostics.title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = strings.diagnostics.backCd
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                if (isRunningDiagnostics) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            strings.diagnostics.progress,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    // Indicador de progreso de permisos
                    val totalChecks = diagnosticResults.size
                    val passedChecks = diagnosticResults.values.count { it }
                    val progressPercentage = if (totalChecks > 0) (passedChecks.toFloat() / totalChecks * 100).toInt() else 0

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = strings.diagnostics.progress,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { progressPercentage / 100f },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "$passedChecks de $totalChecks configurados ($progressPercentage%)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val roleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        context.getSystemService(android.app.role.RoleManager::class.java)
                    } else {
                        null
                    }
                    val requirementItems = listOf(
                        RequirementItem(
                            title = strings.diagnostics.roleDialer,
                            ok = diagnosticResults["role_dialer"] == true,
                            onFix = {
                                val intent = Intent("android.telecom.action.CHANGE_DEFAULT_DIALER").apply {
                                    putExtra("android.telecom.extra.CHANGE_DEFAULT_DIALER_PACKAGE_NAME", context.packageName)
                                }
                                context.startActivity(intent)
                            }
                        ),
                        RequirementItem(
                            title = strings.diagnostics.roleScreening,
                            ok = diagnosticResults["role_screening"] == true,
                            onFix = {
                                val intent = Intent("android.telecom.action.CHANGE_DEFAULT_DIALER").apply {
                                    putExtra("android.telecom.extra.CHANGE_DEFAULT_DIALER_PACKAGE_NAME", context.packageName)
                                }
                                context.startActivity(intent)
                            }
                        ),
                        RequirementItem(
                            title = strings.diagnostics.roleSms,
                            ok = diagnosticResults["role_sms"] == true,
                            onFix = {
                                if (roleManager != null) {
                                    val intent = Intent(android.provider.Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                                        putExtra(android.provider.Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
                                    }
                                    context.startActivity(intent)
                                }
                            }
                        ),
                        RequirementItem(
                            title = strings.diagnostics.overlay,
                            ok = diagnosticResults["overlay_permission"] == true,
                            onFix = {
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            }
                        ),
                        RequirementItem(
                            title = strings.diagnostics.notifications,
                            ok = diagnosticResults["notifications"] == true,
                            onFix = {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                context.startActivity(intent)
                            }
                        )
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = strings.diagnostics.criticalRequirements,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            requirementItems.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (item.ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                                            contentDescription = null,
                                            tint = if (item.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(item.title)
                                    }
                                    if (!item.ok && item.onFix != null) {
                                        TextButton(onClick = item.onFix) {
                                            Text(strings.common.fix)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val missingRequirements = buildMissingRequirements(
                    diagnosticResults,
                    strings.diagnostics.missingCritical,
                    strings.diagnostics.missingDialer,
                    strings.diagnostics.missingScreening,
                    strings.diagnostics.missingSms,
                    strings.diagnostics.missingOverlay,
                    strings.diagnostics.missingNotifications
                )
                if (missingRequirements.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = strings.diagnostics.blockedByMissing,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Falta: ${missingRequirements.joinToString(separator = ", ")}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Botón de continuar solo cuando permisos críticos y roles están otorgados
                val essentialChecksPassed = (diagnosticResults["critical_permissions"] == true) &&
                                         (diagnosticResults["system_roles"] == true) &&
                                         (diagnosticResults["special_permissions"] == true)

                if (essentialChecksPassed) {
                    Button(
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(strings.diagnostics.continueAction)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                } else if (isDebugBypassEnabled) {
                    // Botón de forzar continuar solo en modo debug
                    OutlinedButton(
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(strings.diagnostics.forceContinue)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Botón para reejecutar diagnósticos
                Button(
                    onClick = {
                        Timber.tag(TAG).d( "Manual diagnostic button clicked")
                        isRunningDiagnostics = true
                        runDiagnostics()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    enabled = !isRunningDiagnostics
                ) {
                    if (isRunningDiagnostics) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.diagnostics.running)
                        }
                    } else {
                        Text(strings.diagnostics.runFull)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Resultados de diagnósticos
                diagnostics.forEach { diagnostic ->
                    DiagnosticItemCard(diagnostic = diagnostic, onActionExecuted = {
                        // Después de ejecutar una acción, esperar y volver a ejecutar diagnósticos
                        isRunningDiagnostics = true
                        runDiagnostics()
                    })
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun DiagnosticItemCard(diagnostic: DiagnosticItem, onActionExecuted: () -> Unit = {}) {
    val strings = Strings
    val statusColor = when (diagnostic.status) {
        DiagnosticStatus.PASSED -> MaterialTheme.colorScheme.primary
        DiagnosticStatus.FAILED -> MaterialTheme.colorScheme.error
        DiagnosticStatus.WARNING -> MaterialTheme.colorScheme.tertiary
        DiagnosticStatus.INFO -> MaterialTheme.colorScheme.secondary
    }

    val statusIcon = when (diagnostic.status) {
        DiagnosticStatus.PASSED -> "✅"
        DiagnosticStatus.FAILED -> "❌"
        DiagnosticStatus.WARNING -> "⚠️"
        DiagnosticStatus.INFO -> "ℹ️"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusIcon,
                        style = MaterialTheme.typography.titleMedium,
                        color = statusColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = diagnostic.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = diagnostic.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (diagnostic.action != null) {
                    Button(
                        onClick = {
                            diagnostic.action!!()
                            // Notificar que se ejecutó una acción para volver a ejecutar diagnósticos
                            onActionExecuted()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (diagnostic.status) {
                                DiagnosticStatus.FAILED -> MaterialTheme.colorScheme.error
                                DiagnosticStatus.WARNING -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    ) {
                        Text(diagnostic.actionText ?: strings.common.configure)
                    }
                }
            }
        }
    }
}
