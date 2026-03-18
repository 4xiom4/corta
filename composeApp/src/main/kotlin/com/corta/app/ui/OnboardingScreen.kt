package com.corta.app.ui

import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.corta.app.ui.i18n.Strings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

@Composable
fun OnboardingScreen(
    roleManager: RoleManager,
    onPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Permisos críticos sin los cuales la app no puede funcionar
    val criticalPermissions = listOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.WRITE_CALL_LOG,
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS
    ).toTypedArray()

    // Permisos opcionales que mejoran la experiencia
    val optionalPermissions = mutableListOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.ANSWER_PHONE_CALLS,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.RECEIVE_MMS,
        Manifest.permission.RECEIVE_WAP_PUSH
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    val allStandardPermissions = (criticalPermissions.toList() + optionalPermissions.toList()).toTypedArray()

    var hasCriticalPermissions by remember {
        mutableStateOf(criticalPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }

    var hasAllStandardPermissions by remember {
        mutableStateOf(allStandardPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }

    var hasSpecialPermissions by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }

    var hasDialerRole by remember { mutableStateOf(!roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) || roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) }
    var hasCallScreeningRole by remember { mutableStateOf(!roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) || roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) }
    var hasSmsRole by remember { 
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !roleManager.isRoleAvailable(RoleManager.ROLE_SMS) || roleManager.isRoleHeld(RoleManager.ROLE_SMS)
        ) 
    }

    fun refreshState() {
        hasCriticalPermissions = criticalPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        hasAllStandardPermissions = allStandardPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        hasSpecialPermissions = Settings.canDrawOverlays(context)
        hasDialerRole = !roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) || roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
        hasCallScreeningRole = !roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) || roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        hasSmsRole = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !roleManager.isRoleAvailable(RoleManager.ROLE_SMS) || roleManager.isRoleHeld(RoleManager.ROLE_SMS)
        
        if (hasCriticalPermissions && hasSpecialPermissions && hasDialerRole && hasCallScreeningRole && hasSmsRole) {
            onPermissionsGranted()
        }
    }

    // Refrescar al volver del sistema
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var showExplanationDialog by remember { mutableStateOf(!hasCriticalPermissions || !hasSpecialPermissions) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        refreshState()
    }

    val dialerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        refreshState()
    }

    val screeningLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        refreshState()
    }

    val smsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        refreshState()
    }

    // Launcher especial para SYSTEM_ALERT_WINDOW
    val alertWindowLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        refreshState()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = Strings.onboardingTitle,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(Strings.onboardingDescBold)
                    }
                    append(Strings.onboardingDescRest)
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = { showExplanationDialog = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !hasAllStandardPermissions
            ) {
                Text(if (hasAllStandardPermissions) "✅ Permisos Concedidos" else "📋 Solicitar Permisos Esenciales")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mostrar estado de permisos individuales
            if (!hasCriticalPermissions) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "⚠️ Permisos Faltantes",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "La app necesita los siguientes permisos para funcionar correctamente:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        // Mostrar permisos críticos que faltan
                        criticalPermissions.filter { 
                            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED 
                        }.forEach { permission ->
                            val description = when (permission) {
                                Manifest.permission.READ_PHONE_STATE -> "• Leer estado del teléfono (para identificar llamadas)"
                                Manifest.permission.READ_CALL_LOG -> "• Leer registro de llamadas"
                                Manifest.permission.WRITE_CALL_LOG -> "• Modificar registro de llamadas"
                                Manifest.permission.RECEIVE_SMS -> "• Recibir SMS (para filtrar spam)"
                                Manifest.permission.SEND_SMS -> "• Enviar SMS (respuestas automáticas)"
                                Manifest.permission.READ_SMS -> "• Leer SMS (para analizar contenido)"
                                else -> "• $permission"
                            }
                            Text(
                                description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // Mostrar permisos especiales faltantes
                        if (!hasSpecialPermissions) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "⚠️ Permisos Especiales Requeridos:",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "• Ventanas del sistema (para mostrar alertas)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botones para permisos especiales si faltan
            val missingSpecialPermissions = if (!Settings.canDrawOverlays(context)) {
                listOf(Manifest.permission.SYSTEM_ALERT_WINDOW)
            } else emptyList()
            
            if (missingSpecialPermissions.contains(Manifest.permission.SYSTEM_ALERT_WINDOW)) {
                Button(
                    onClick = {
                        val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = Uri.parse("package:" + context.packageName)
                        }
                        alertWindowLauncher.launch(intent)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Text("🔓 Solicitar Permiso de Ventanas del Sistema", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && !hasCallScreeningRole) {
                        screeningLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !hasCallScreeningRole && hasCriticalPermissions
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (hasCallScreeningRole) "✅ Screening Disponible" else "🛡️ Activar Screening", style = MaterialTheme.typography.labelMedium)
                    Text(if (hasCallScreeningRole) "Llamadas serán filtradas automáticamente" else "Permite bloquear spam antes de que suene", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) && !hasDialerRole) {
                        dialerLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !hasDialerRole && hasCallScreeningRole
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (hasDialerRole) "✅ App Telefónica por Defecto" else "📞 Establecer como Telefónica por Defecto", style = MaterialTheme.typography.labelMedium)
                    Text(if (hasDialerRole) "Corta manejará todas las llamadas" else "Reemplaza tu app actual de llamadas", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS) && !hasSmsRole) {
                            smsLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = !hasSmsRole && hasDialerRole
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (hasSmsRole) "✅ App SMS por Defecto" else "📱 Establecer como App SMS por Defecto", style = MaterialTheme.typography.labelMedium)
                        Text(if (hasSmsRole) "Corta filtrará todos los mensajes" else "Reemplaza tu app actual de SMS", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (hasCriticalPermissions && hasDialerRole && hasCallScreeningRole && hasSmsRole) {
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onPermissionsGranted,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                ) {
                    Text("Continuar a la aplicación")
                }
            } else {
                // Opción de forzar continuar si está bloqueado pero tiene lo mínimo
                Spacer(modifier = Modifier.height(32.dp))
                OutlinedButton(
                    onClick = onPermissionsGranted,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("⚠️ Forzar entrada (Depuración/Atascado)")
                }
            }
        }
    }

    if (showExplanationDialog) {
        AlertDialog(
            onDismissRequest = { showExplanationDialog = false },
            title = { Text(Strings.permissionsReasoningTitle) },
            text = { 
                Column {
                    Text(
                        buildAnnotatedString {
                            append(Strings.permissionsReasoningDescPart1)
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(Strings.permissionsReasoningDescBold1)
                            }
                            append(Strings.permissionsReasoningDescPart2)
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(Strings.permissionsReasoningDescBold2)
                            }
                            append(". ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append("También necesitamos permiso de Micrófono y Almacenamiento para la grabación de llamadas locales.")
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = { showPrivacyDialog = true },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(Strings.privacyDetailsTitle, textDecoration = TextDecoration.Underline)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showExplanationDialog = false
                    if (!hasAllStandardPermissions) {
                        permissionsLauncher.launch(allStandardPermissions)
                    }
                }) {
                    Text(Strings.understood)
                }
            }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text(Strings.privacyDetailsTitle) },
            text = { Text(Strings.privacyDetailsDesc) },
            confirmButton = {
                Button(onClick = { showPrivacyDialog = false }) {
                    Text(Strings.understood)
                }
            }
        )
    }
}

private fun checkAllGranted(hasStandard: Boolean, hasSpecial: Boolean, hasDialer: Boolean, hasScreening: Boolean, hasSms: Boolean, onPermissionsGranted: () -> Unit) {
    // Ya no se usa checkAllGranted directo, se usa refreshState
}
