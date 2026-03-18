package com.corta.app.ui.diagnostics

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import android.util.Log
import com.corta.app.ui.i18n.Strings
import androidx.compose.material3.ExperimentalMaterial3Api

data class DiagnosticItem(
    val title: String,
    val description: String,
    val status: DiagnosticStatus,
    val action: (() -> Unit)? = null,
    val actionText: String? = null
)

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
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var diagnostics by remember { mutableStateOf<List<DiagnosticItem>>(emptyList()) }
    var isRunningDiagnostics by remember { mutableStateOf(false) }
    var diagnosticResults by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var isCheckingPermissions by remember { mutableStateOf(false) }
    
    fun runDiagnostics() {
        isRunningDiagnostics = true
        val results = mutableMapOf<String, Boolean>()
        val diagnosticItems = mutableListOf<DiagnosticItem>()
        
        // 1. Verificar permisos críticos
        val criticalPermissionsList = listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS
        )
        
        val criticalGranted = criticalPermissionsList.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
        results["critical_permissions"] = criticalGranted
        
        diagnosticItems.add(
            DiagnosticItem(
                title = "Permisos Críticos",
                description = "Funciones básicas de bloqueo y registro",
                status = if (criticalGranted) DiagnosticStatus.PASSED else DiagnosticStatus.FAILED,
                action = if (!criticalGranted) ({
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }) else null,
                actionText = if (!criticalGranted) "Configurar" else null
            )
        )
        
        // 2. Verificar permisos especiales
        val specialPermissions = listOf(
            Manifest.permission.SYSTEM_ALERT_WINDOW,
            Manifest.permission.DISABLE_KEYGUARD
        )
        
        val specialPermissionsGranted = specialPermissions.all { perm ->
            val granted = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            Log.d("CortaDiagnostics", "Permiso especial $perm: ${if (granted) "GRANTED" else "DENIED"}")
            granted
        }
        results["special_permissions"] = specialPermissionsGranted
        
        diagnosticItems.add(
            DiagnosticItem(
                title = "Permisos Especiales",
                description = "Permisos para ventanas del sistema y desbloqueo",
                status = if (specialPermissionsGranted) DiagnosticStatus.PASSED else DiagnosticStatus.FAILED,
                action = if (!specialPermissionsGranted) ({
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.SYSTEM_ALERT_WINDOW) != PackageManager.PERMISSION_GRANTED) {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = Uri.parse("package:" + context.packageName)
                        }
                        context.startActivity(intent)
                    } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.DISABLE_KEYGUARD) != PackageManager.PERMISSION_GRANTED) {
                        val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
                        context.startActivity(intent)
                    }
                }) else null,
                actionText = if (!specialPermissionsGranted) "Configurar" else null
            )
        )
        
        // 3. Verificar roles del sistema (simplificado)
        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? android.app.role.RoleManager
        val hasDialerRole = roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_DIALER) ?: false
        val hasCallScreeningRole = roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING) ?: false
        val hasSmsRole = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || (roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_SMS) ?: false)
        val allRolesGranted = hasDialerRole && hasCallScreeningRole && hasSmsRole
        
        results["system_roles"] = allRolesGranted
        
        diagnosticItems.add(
            DiagnosticItem(
                title = "Roles del Sistema",
                description = "App como telefónica por defecto, SMS y screening",
                status = if (allRolesGranted) DiagnosticStatus.PASSED else DiagnosticStatus.FAILED,
                action = if (!allRolesGranted && roleManager != null) ({
                    if (!hasDialerRole) {
                        val intent = Intent("android.telecom.action.CHANGE_DEFAULT_DIALER").apply {
                            putExtra("android.telecom.extra.CHANGE_DEFAULT_DIALER_PACKAGE_NAME", context.packageName)
                        }
                        context.startActivity(intent)
                    }
                }) else null,
                actionText = if (!allRolesGranted) "Configurar" else null
            )
        )
        
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
                title = "Funciones Mejoradas",
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
                title = "Acceso a Contactos",
                description = "Permiso para leer y modificar contactos",
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
                title = "Acceso a Almacenamiento",
                description = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    "Permiso para escribir en almacenamiento externo"
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
                title = "Notificaciones",
                description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    "Permiso para mostrar notificaciones (Android 13+)"
                } else {
                    "Notificaciones disponibles (Android <13)"
                },
                status = if (notificationPermission) DiagnosticStatus.PASSED else DiagnosticStatus.FAILED,
                action = null
            )
        )
        
        // 8. Verificar red
        var networkAvailable = false
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val activeNetwork = connectivityManager?.activeNetworkInfo
            networkAvailable = activeNetwork?.isConnected ?: false
        } catch (e: Exception) {
            networkAvailable = false
        }
        
        results["network"] = networkAvailable
        
        diagnosticItems.add(
            DiagnosticItem(
                title = "Conexión de Red",
                description = if (networkAvailable) {
                    "Conexión a internet disponible"
                } else {
                    "Sin conexión a internet"
                },
                status = if (networkAvailable) DiagnosticStatus.PASSED else DiagnosticStatus.WARNING,
                action = null
            )
        )
        
        // 9. Verificar configuración completa
        val allChecksPassed = results.values.all { it }
        
        Log.d("CortaDiagnostics", "Resultados: $results")
        Log.d("CortaDiagnostics", "¿Todos los checks pasaron?: $allChecksPassed")
        
        diagnosticItems.add(
            DiagnosticItem(
                title = "Estado General",
                description = if (allChecksPassed) {
                    "✅ Todos los sistemas funcionando correctamente"
                } else {
                    "⚠️ Hay problemas que requieren atención"
                },
                status = if (allChecksPassed) DiagnosticStatus.PASSED else DiagnosticStatus.WARNING,
                action = if (!allChecksPassed) ({
                    runDiagnostics()
                }) else null,
                actionText = if (!allChecksPassed) "Reintentar" else null
            )
        )
        
        diagnostics = diagnosticItems
        diagnosticResults = results
        isRunningDiagnostics = false
    }
    
    // Ejecutar diagnósticos automáticamente al entrar
    LaunchedEffect(Unit) {
        runDiagnostics()
    }
    
    // Verificar permisos periódicamente cuando la app está en primer plano
    LaunchedEffect(lifecycleOwner) {
        while (isActive) {
            delay(2000) // Verificar cada 2 segundos
            if (!isCheckingPermissions && !isRunningDiagnostics) {
                isCheckingPermissions = true
                runDiagnostics()
                delay(1000)
                isCheckingPermissions = false
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnóstico del Sistema") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Atrás")
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
                            "Ejecutando diagnósticos...",
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
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Progreso de Configuración",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { progressPercentage / 100f },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = if (progressPercentage == 100) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "$passedChecks de $totalChecks configurados ($progressPercentage%)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Botón de continuar solo cuando permisos críticos y roles están otorgados
                val essentialChecksPassed = (diagnosticResults["critical_permissions"] == true) &&
                                         (diagnosticResults["system_roles"] == true) &&
                                         (diagnosticResults["special_permissions"] == true)
                
                if (essentialChecksPassed) {
                    Button(
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50),
                            contentColor = Color.White
                        )
                    ) {
                        Text("✅ Continuar")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    // Botón de forzar continuar para depuración
                    OutlinedButton(
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("⚠️ Forzar Continuar (Depuración)")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Botón para reejecutar diagnósticos
                Button(
                    onClick = { runDiagnostics() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("🔍 Ejecutar Diagnóstico Completo")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Resultados de diagnósticos
                diagnostics.forEach { diagnostic ->
                    DiagnosticItemCard(diagnostic = diagnostic)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticItemCard(diagnostic: DiagnosticItem) {
    val statusColor = when (diagnostic.status) {
        DiagnosticStatus.PASSED -> Color.Green
        DiagnosticStatus.FAILED -> Color.Red
        DiagnosticStatus.WARNING -> Color(0xFFFF9800) // Naranja
        DiagnosticStatus.INFO -> Color.Blue
    }
    
    val statusIcon = when (diagnostic.status) {
        DiagnosticStatus.PASSED -> "✅"
        DiagnosticStatus.FAILED -> "❌"
        DiagnosticStatus.WARNING -> "⚠️"
        DiagnosticStatus.INFO -> "ℹ️"
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
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
                            // La verificación automática se maneja con el LaunchedEffect periódico
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (diagnostic.status) {
                                DiagnosticStatus.FAILED -> MaterialTheme.colorScheme.error
                                DiagnosticStatus.WARNING -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    ) {
                        Text(diagnostic.actionText ?: "Configurar")
                    }
                }
            }
        }
    }
}
