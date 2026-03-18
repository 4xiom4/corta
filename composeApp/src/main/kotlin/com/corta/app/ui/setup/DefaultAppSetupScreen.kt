package com.corta.app.ui.setup

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.core.content.ContextCompat
import com.corta.app.ui.i18n.Strings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultAppSetupScreen(
    onSetupComplete: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    var isDefaultDialer by remember { mutableStateOf(false) }
    var isDefaultSms by remember { mutableStateOf(false) }
    var hasCallScreening by remember { mutableStateOf(false) }
    
    // Verificar estado actual
    LaunchedEffect(Unit) {
        isDefaultDialer = isDefaultDialerApp(context)
        isDefaultSms = isDefaultSmsApp(context)
        hasCallScreening = hasCallScreeningPermission(context)
    }
    
    val allSetupComplete = isDefaultDialer && isDefaultSms && hasCallScreening
    
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Configuración Inicial", 
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Rounded.SettingsSuggest,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Configura Corta como tu app por defecto",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Para proteger completamente tus llamadas y mensajes, Corta necesita ser tu aplicación predeterminada.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            // Status Cards
            SetupStatusCard(
                title = "App de Llamadas por Defecto",
                description = "Permite a Corta manejar todas tus llamadas entrantes y salientes.",
                icon = Icons.Rounded.Phone,
                isConfigured = isDefaultDialer,
                onClick = { requestDefaultDialer(context) }
            )
            
            SetupStatusCard(
                title = "App de SMS por Defecto",
                description = "Permite a Corta filtrar tus mensajes antes de que lleguen a tu bandeja.",
                icon = Icons.Rounded.Sms,
                isConfigured = isDefaultSms,
                onClick = { requestDefaultSms(context) }
            )
            
            SetupStatusCard(
                title = "Screening de Llamadas",
                description = "Permite a Corta evaluar llamadas antes de que suenen.",
                icon = Icons.Rounded.Security,
                isConfigured = hasCallScreening,
                onClick = { requestCallScreeningPermission(context) }
            )
            
            // Action Buttons
            Spacer(modifier = Modifier.height(16.dp))
            
            if (allSetupComplete) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "¡Configuración Completa!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Corta está lista para protegerte",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = onSetupComplete,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Comenzar a Usar Corta", style = MaterialTheme.typography.titleMedium)
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Omitir")
                }
                
                Button(
                    onClick = { 
                        // Re-check status
                        isDefaultDialer = isDefaultDialerApp(context)
                        isDefaultSms = isDefaultSmsApp(context)
                        hasCallScreening = hasCallScreeningPermission(context)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Verificar Estado")
                }
            }
        }
    }
}

@Composable
fun SetupStatusCard(
    title: String,
    description: String,
    icon: ImageVector,
    isConfigured: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConfigured) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isConfigured) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (isConfigured) 
                            MaterialTheme.colorScheme.onPrimary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isConfigured) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = "Configurado",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = "Configurar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// Helper functions
private fun isDefaultDialerApp(context: Context): Boolean {
    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
    return telecomManager?.defaultDialerPackage == context.packageName
}

private fun isDefaultSmsApp(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? android.app.role.RoleManager
        roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_SMS) == true
    } else {
        @Suppress("DEPRECATION")
        android.provider.Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }
}

private fun hasCallScreeningPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        return false // callScreeningApps not available before API 29
    }
    
    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
    return try {
        // Use reflection to access callScreeningApps safely
        val callScreeningAppsMethod = TelecomManager::class.java.getMethod("getCallScreeningApps")
        val callScreeningApps = callScreeningAppsMethod.invoke(telecomManager) as? List<*>
        callScreeningApps?.any { app ->
            val packageNameField = app?.javaClass?.getDeclaredField("packageName")
            packageNameField?.isAccessible = true
            val packageName = packageNameField?.get(app) as? String
            packageName == context.packageName
        } == true
    } catch (e: Exception) {
        false // Method not available or other error
    }
}

private fun requestDefaultDialer(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? android.app.role.RoleManager
        roleManager?.createRequestRoleIntent(android.app.role.RoleManager.ROLE_DIALER)?.let { intent ->
            ContextCompat.startActivity(context, intent, null)
        }
    } else {
        @Suppress("DEPRECATION")
        Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
            putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
            context.startActivity(this)
        }
    }
}

private fun requestDefaultSms(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? android.app.role.RoleManager
        roleManager?.createRequestRoleIntent(android.app.role.RoleManager.ROLE_SMS)?.let { intent ->
            ContextCompat.startActivity(context, intent, null)
        }
    } else {
        @Suppress("DEPRECATION")
        Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
            putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
            context.startActivity(this)
        }
    }
}

private fun requestCallScreeningPermission(context: Context) {
    try {
        Intent("android.telecom.action.CALL_SCREENING_SETTINGS").apply {
            context.startActivity(this)
        }
    } catch (e: Exception) {
        Toast.makeText(context, "No se pudo abrir la configuración de screening", Toast.LENGTH_LONG).show()
    }
}
