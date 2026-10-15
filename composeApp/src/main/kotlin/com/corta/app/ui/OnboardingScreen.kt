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
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.ColumnScope

@Composable
fun OnboardingScreen(
    roleManager: RoleManager,
    permissionsState: OnboardingPermissionsState,
    rolesState: OnboardingRolesState,
    onPermissionsGranted: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val strings = Strings
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = context.getSharedPreferences("corta_prefs", android.content.Context.MODE_PRIVATE)
    val isDebugMode = prefs.getBoolean("debug_mode", false)
    val isDebugBuild = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val isDebugBypassEnabled = isDebugBuild && isDebugMode

    val hasCriticalPermissions = permissionsState.hasCriticalPermissions
    val hasAllStandardPermissions = permissionsState.hasAllStandardPermissions
    val hasSpecialPermissions = permissionsState.hasSpecialPermissions

    val hasDialerRole = rolesState.hasDialerRole
    val hasCallScreeningRole = rolesState.hasCallScreeningRole
    val hasSmsRole = rolesState.hasSmsRole

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

    fun refreshState() {
        onRefresh()
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

    val grantedCount = listOf(
        hasCriticalPermissions,
        hasCallScreeningRole,
        hasDialerRole,
        hasSmsRole,
        hasSpecialPermissions
    ).count { it }
    val totalSteps = 5
    val progressFraction = grantedCount.toFloat() / totalSteps.toFloat()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        modifier = Modifier.size(38.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Activa tu Escudo Corta",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Configura los permisos para interceptar telemarketing 809 y proteger tus comunicaciones",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Progress Header
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Estado de protección",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$grantedCount de $totalSteps activadas",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Step cards list
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OnboardingStepCard(
                    title = "1. Permisos Esenciales de Telefonía",
                    description = "Consultar números entrantes y contrastarlos con la base local SUBTEL en <2ms.",
                    icon = Icons.Rounded.PhoneInTalk,
                    tag = if (hasCriticalPermissions) "Activo ✓" else "Requerido",
                    isGranted = hasCriticalPermissions,
                    enabled = true,
                    onClick = { showExplanationDialog = true }
                )

                OnboardingStepCard(
                    title = "2. Filtrado de Llamadas (Call Screening)",
                    description = "Motor nativo que rechaza llamadas 809 antes de que suenen en el teléfono.",
                    icon = Icons.Rounded.Shield,
                    tag = if (hasCallScreeningRole) "Activo ✓" else "Recomendado",
                    isGranted = hasCallScreeningRole,
                    enabled = hasCriticalPermissions,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                                screeningLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                            }
                        }
                    }
                )

                OnboardingStepCard(
                    title = "3. Teléfono Predeterminado (Dialer)",
                    description = "Gestiona llamadas entrantes, marcador DTMF y grabación local con Corta.",
                    icon = Icons.Rounded.Dialpad,
                    tag = if (hasDialerRole) "Activo ✓" else "Opcional",
                    isGranted = hasDialerRole,
                    enabled = hasCriticalPermissions,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                                dialerLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
                            }
                        }
                    }
                )

                OnboardingStepCard(
                    title = "4. Mensajes SMS y Smishing",
                    description = "Filtro contra suplantación bancaria y enlaces sospechosos en mensajes.",
                    icon = Icons.AutoMirrored.Rounded.Message,
                    tag = if (hasSmsRole) "Activo ✓" else "Opcional",
                    isGranted = hasSmsRole,
                    enabled = hasCriticalPermissions,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                                smsLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
                            }
                        }
                    }
                )

                OnboardingStepCard(
                    title = "5. Alertas Flotantes en Pantalla",
                    description = "Muestra la ficha del llamante y alertas de spam mientras usas otras apps.",
                    icon = Icons.Rounded.Layers,
                    tag = if (hasSpecialPermissions) "Activo ✓" else "Opcional",
                    isGranted = hasSpecialPermissions,
                    enabled = true,
                    onClick = {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = Uri.parse("package:" + context.packageName)
                        }
                        alertWindowLauncher.launch(intent)
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (hasCriticalPermissions) {
                Button(
                    onClick = onPermissionsGranted,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        text = if (hasCallScreeningRole) "Comenzar a Proteger" else "Continuar con Protección Básica",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = { showExplanationDialog = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        text = "Configurar Permisos Requeridos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    OnboardingDialogs(
        showExplanationDialog = showExplanationDialog,
        onShowExplanationDialogChange = { showExplanationDialog = it },
        showPrivacyDialog = showPrivacyDialog,
        onShowPrivacyDialogChange = { showPrivacyDialog = it },
        hasAllStandardPermissions = hasAllStandardPermissions,
        permissionsLauncher = permissionsLauncher,
        allStandardPermissions = allStandardPermissions
    )
}

@Composable
fun OnboardingStepCard(
    title: String,
    description: String,
    icon: ImageVector,
    tag: String,
    isGranted: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        onClick = if (!isGranted && enabled) onClick else ({}),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isGranted) MaterialTheme.colorScheme.surfaceContainerLowest
                else if (!enabled) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (isGranted) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        enabled = !isGranted && enabled
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (isGranted) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isGranted) Icons.Rounded.Check else icon,
                        contentDescription = null,
                        tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when {
                            isGranted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            tag == "Requerido" -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                        }
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isGranted -> MaterialTheme.colorScheme.primary
                                tag == "Requerido" -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                )
            }

            if (!isGranted && enabled) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun OnboardingDialogs(
    showExplanationDialog: Boolean,
    onShowExplanationDialogChange: (Boolean) -> Unit,
    showPrivacyDialog: Boolean,
    onShowPrivacyDialogChange: (Boolean) -> Unit,
    hasAllStandardPermissions: Boolean,
    permissionsLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    allStandardPermissions: Array<String>
) {
    val strings = Strings
    if (showExplanationDialog) {
        AlertDialog(
            onDismissRequest = { onShowExplanationDialogChange(false) },
            title = { Text(strings.onboarding.permissionsReasoningTitle) },
            text = {
                Column {
                    Text(
                        buildAnnotatedString {
                            append(strings.onboarding.permissionsReasoningDescPart1)
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(strings.onboarding.permissionsReasoningDescBold1)
                            }
                            append(strings.onboarding.permissionsReasoningDescPart2)
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(strings.onboarding.permissionsReasoningDescBold2)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = { onShowPrivacyDialogChange(true) },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(strings.onboarding.privacyDetailsTitle, textDecoration = TextDecoration.Underline)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onShowExplanationDialogChange(false)
                    if (!hasAllStandardPermissions) {
                        permissionsLauncher.launch(allStandardPermissions)
                    }
                }) {
                    Text(strings.common.understood)
                }
            }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { onShowPrivacyDialogChange(false) },
            title = { Text(strings.onboarding.privacyDetailsTitle) },
            text = { Text(strings.onboarding.privacyDetailsDesc) },
            confirmButton = {
                Button(onClick = { onShowPrivacyDialogChange(false) }) {
                    Text(strings.common.understood)
                }
            }
        )
    }
}

private fun checkAllGranted(hasStandard: Boolean, hasSpecial: Boolean, hasDialer: Boolean, hasScreening: Boolean, hasSms: Boolean, onPermissionsGranted: () -> Unit) {
    // Ya no se usa checkAllGranted directo, se usa refreshState
}
