package com.corta.app.ui.profile

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.corta.app.ui.i18n.Strings
import com.corta.db.FilterRule
import com.corta.domain.CortaRepository
import com.corta.domain.FilterAction
import org.json.JSONArray
import org.json.JSONObject
import org.koin.compose.koinInject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonorProfileScreen(
    onBack: () -> Unit,
    onNavigateToSettings: (() -> Unit)? = null,
    onNavigateToRules: (() -> Unit)? = null,
    onNavigateToStats: (() -> Unit)? = null,
    onNavigateToDiagnostics: (() -> Unit)? = null,
    cortaRepository: CortaRepository = koinInject()
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("corta_prefs", Context.MODE_PRIVATE) }
    val strings = Strings

    val rules by cortaRepository.observeRules().collectAsState(initial = emptyList())

    // Dual SIM real detection
    val simCards = remember(context) {
        val list = mutableListOf<SimSlotData>()
        val hasPhonePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPhonePermission) {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            try {
                @Suppress("DEPRECATION")
                val activeSubs = sm?.activeSubscriptionInfoList
                if (!activeSubs.isNullOrEmpty()) {
                    activeSubs.sortedBy { it.simSlotIndex }.forEach { sub ->
                        val carrier = sub.carrierName?.toString()?.takeIf { it.isNotBlank() }
                            ?: sub.displayName?.toString()?.takeIf { it.isNotBlank() }
                            ?: "SIM ${sub.simSlotIndex + 1}"
                        val rawNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            try { sm?.getPhoneNumber(sub.subscriptionId) } catch (e: Exception) { @Suppress("DEPRECATION") sub.number }
                        } else {
                            @Suppress("DEPRECATION")
                            sub.number
                        }
                        list.add(
                            SimSlotData(
                                slotIndex = sub.simSlotIndex,
                                carrierName = carrier,
                                number = rawNumber?.takeIf { it.isNotBlank() },
                                countryIso = sub.countryIso?.uppercase() ?: "CL",
                                subId = sub.subscriptionId,
                                isActive = true
                            )
                        )
                    }
                    val maxSlots = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        sm?.activeSubscriptionInfoCountMax ?: 2
                    } else 2

                    for (slot in 0 until maxSlots.coerceAtMost(2)) {
                        if (list.none { it.slotIndex == slot }) {
                            list.add(
                                SimSlotData(
                                    slotIndex = slot,
                                    carrierName = "Ranura ${slot + 1} (Sin SIM)",
                                    number = null,
                                    countryIso = null,
                                    subId = -1,
                                    isActive = false
                                )
                            )
                        }
                    }
                    list.sortBy { it.slotIndex }
                } else {
                    list.add(SimSlotData(0, "Sin tarjeta SIM detectada", null, null, -1, false))
                }
            } catch (e: Exception) {
                list.add(SimSlotData(0, "Línea móvil activa", null, null, -1, true))
            }
        } else {
            list.add(SimSlotData(0, "Permiso de lectura telefónica pendiente", null, null, -1, false))
        }
        list
    }

    val initialDetectedPhone = remember(simCards) {
        simCards.firstOrNull { !it.number.isNullOrBlank() }?.number
    }

    var isDonor by remember { mutableStateOf(prefs.getBoolean("is_donor", false)) }
    var userName by remember { mutableStateOf(prefs.getString("user_name", "Mi Dispositivo") ?: "Mi Dispositivo") }
    var userPhone by remember { mutableStateOf(prefs.getString("user_phone", null) ?: initialDetectedPhone ?: "") }

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showFaqSheet by remember { mutableStateOf(false) }
    var showBackupSheet by remember { mutableStateOf(false) }
    var showSupportSheet by remember { mutableStateOf(false) }
    var selectedSimForDetails by remember { mutableStateOf<SimSlotData?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Mi Perfil Corta",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = strings.login.donorProfileBackCd
                        )
                    }
                },
                actions = {
                    if (onNavigateToSettings != null) {
                        IconButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Ajustes",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
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
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. User Profile Header Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(54.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = userName.take(1).uppercase(),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = userName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isDonor) {
                                        Icon(
                                            imageVector = Icons.Rounded.Verified,
                                            contentDescription = "Donante",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (userPhone.isNotBlank()) userPhone else "Sin número configurado (Toca para editar)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (userPhone.isNotBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { showEditProfileDialog = true }
                                )
                            }

                            IconButton(
                                onClick = { showEditProfileDialog = true },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Editar Perfil",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Supporter Badge Pill if active
                        if (isDonor) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.WorkspacePremium,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Medalla de Colaborador Activo de Corta Chile",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Dual SIM Support Card (Doble SIM)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LÍNEAS MÓVILES (DOBLE SIM)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "Toca para detalles",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        simCards.forEachIndexed { index, sim ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedSimForDetails = sim }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (sim.isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.SimCard,
                                            contentDescription = null,
                                            tint = if (sim.isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Ranura ${sim.slotIndex + 1}: ${sim.carrierName}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (sim.isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            Text(
                                                text = if (sim.isActive) "Activa" else "Inactiva",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (sim.isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = sim.number ?: if (sim.isActive) "Protección <2ms en memoria activa" else "Sin servicio en esta ranura",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = "Detalles SIM",
                                    tint = MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            if (index < simCards.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    thickness = 0.8.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Accesos y Secciones Principales
            item {
                Text(
                    text = "ACCESOS RÁPIDOS Y GESTIÓN",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        if (onNavigateToSettings != null) {
                            ProfileNavRow(
                                icon = Icons.Rounded.Settings,
                                title = "Ajustes y Configuración",
                                subtitle = "Reglas avanzadas, apariencia, temas y telecomunicaciones",
                                onClick = onNavigateToSettings
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                thickness = 0.8.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                        }

                        if (onNavigateToStats != null) {
                            ProfileNavRow(
                                icon = Icons.Rounded.BarChart,
                                title = "Estadísticas de Protección",
                                subtitle = "Llamadas bloqueadas, tiempo ahorrado y métricas de spam",
                                onClick = onNavigateToStats
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                thickness = 0.8.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                        }

                        if (onNavigateToRules != null) {
                            ProfileNavRow(
                                icon = Icons.Rounded.Shield,
                                title = "Reglas y Lista de Bloqueo",
                                subtitle = "Normativa SUBTEL 809/600, listas negras y excepciones",
                                onClick = onNavigateToRules
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                thickness = 0.8.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                        }

                        if (onNavigateToDiagnostics != null) {
                            ProfileNavRow(
                                icon = Icons.Rounded.HealthAndSafety,
                                title = "Diagnóstico del Sistema",
                                subtitle = "Verificación de permisos, latencia SQLite y CallScreening",
                                onClick = onNavigateToDiagnostics
                            )
                        }
                    }
                }
            }

            // 4. Herramientas de Respaldo, Soporte y Comunidad
            item {
                Text(
                    text = "HERRAMIENTAS Y COMUNIDAD",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        // Respaldo y Restauración Real
                        ProfileNavRow(
                            icon = Icons.Rounded.SaveAlt,
                            title = "Respaldo y Exportación",
                            subtitle = "Guarda o restaura tus reglas (${rules.size} registradas) y ajustes en JSON",
                            onClick = { showBackupSheet = true }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.8.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )

                        // Invitar amigos / Compartir Corta
                        ProfileNavRow(
                            icon = Icons.Rounded.Share,
                            title = "Invitar amigos / Compartir Corta",
                            subtitle = "Recomienda Corta a tus familiares para protegerlos del spam",
                            onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Te recomiendo Corta App para Android:\n" +
                                        "Bloquea llamadas spam de telemarketing (norma SUBTEL 809), fraudes bancarios y smishing SMS en Chile de forma automática en < 2ms y 100% privada, sin subir tus contactos a internet.\n\n" +
                                        "Descárgala gratis y de código abierto en: https://github.com/axioma-dev/corta"
                                    )
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Compartir Corta con amigos"))
                            }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.8.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )

                        // Soporte y Contacto
                        ProfileNavRow(
                            icon = Icons.Rounded.MailOutline,
                            title = "Soporte y Contacto",
                            subtitle = "Escríbenos, copia el correo de asistencia o reporta en GitHub",
                            onClick = { showSupportSheet = true }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.8.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )

                        // Centro de Ayuda / Preguntas Frecuentes
                        ProfileNavRow(
                            icon = Icons.AutoMirrored.Rounded.HelpOutline,
                            title = "Centro de Ayuda y Preguntas Frecuentes",
                            subtitle = "Conoce cómo funciona la protección offline y la ley SUBTEL",
                            onClick = { showFaqSheet = true }
                        )
                    }
                }
            }

            // 5. Insignia de Donante / Apoyo
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDonor) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isDonor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.VolunteerActivism,
                                    contentDescription = null,
                                    tint = if (isDonor) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Distintivo de Donante / Apoyo",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Muestra la medalla en tu perfil si apoyas el software libre y la privacidad en Chile.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Switch(
                            checked = isDonor,
                            onCheckedChange = { checked ->
                                isDonor = checked
                                prefs.edit().putBoolean("is_donor", checked).apply()
                                val msg = if (checked) "¡Medalla de Donante activada!" else "Medalla desactivada"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }

    // Modal: Editar Perfil
    if (showEditProfileDialog) {
        var editName by remember { mutableStateOf(userName) }
        var editPhone by remember { mutableStateOf(userPhone) }

        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = {
                Text(
                    text = "Editar Perfil",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Nombre de usuario") },
                        placeholder = { Text("Ej: Mi Dispositivo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = { Text("Teléfono principal") },
                        placeholder = { Text("Ej: +56 9 1234 5678") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        userName = editName.ifBlank { "Mi Dispositivo" }
                        userPhone = editPhone.trim()
                        prefs.edit()
                            .putString("user_name", userName)
                            .putString("user_phone", userPhone)
                            .apply()
                        showEditProfileDialog = false
                        Toast.makeText(context, "Perfil actualizado", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Modal: Detalles de SIM
    selectedSimForDetails?.let { sim ->
        SimDetailsDialog(
            sim = sim,
            onDismiss = { selectedSimForDetails = null }
        )
    }

    // Modal: Respaldo y Restauración
    if (showBackupSheet) {
        BackupRestoreBottomSheet(
            rules = rules,
            cortaRepository = cortaRepository,
            userName = userName,
            userPhone = userPhone,
            onDismiss = { showBackupSheet = false }
        )
    }

    // Modal: Soporte y Contacto
    if (showSupportSheet) {
        SupportBottomSheet(onDismiss = { showSupportSheet = false })
    }

    // Modal: Centro de Ayuda / Preguntas Frecuentes
    if (showFaqSheet) {
        HelpFaqBottomSheet(onDismiss = { showFaqSheet = false })
    }
}

private data class SimSlotData(
    val slotIndex: Int,
    val carrierName: String,
    val number: String?,
    val countryIso: String?,
    val subId: Int,
    val isActive: Boolean
)

@Composable
private fun ProfileNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
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

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Diálogo de información técnica de la ranura SIM
 */
@Composable
private fun SimDetailsDialog(
    sim: SimSlotData,
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
                SimDetailRow(label = "Operador", value = sim.carrierName)
                SimDetailRow(label = "Estado", value = if (sim.isActive) "Activa y Protegida" else "Inactiva / Sin SIM")
                SimDetailRow(label = "Número", value = sim.number ?: "No publicado por operador")
                SimDetailRow(label = "País", value = sim.countryIso ?: "CL")
                if (sim.subId != -1) {
                    SimDetailRow(label = "ID Suscripción", value = "${sim.subId}")
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
private fun SimDetailRow(label: String, value: String) {
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

/**
 * BottomSheet de Respaldo y Restauración Real en JSON
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupRestoreBottomSheet(
    rules: List<FilterRule>,
    cortaRepository: CortaRepository,
    userName: String,
    userPhone: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("corta_prefs", Context.MODE_PRIVATE) }
    var restoreJsonText by remember { mutableStateOf("") }
    var showRestoreInput by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(
                    text = "Respaldo y Restauración",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Exporta tus reglas y configuraciones en formato JSON estándar o restaura un respaldo previo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Card 1: Exportar
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.UploadFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Exportar Respaldo Actual",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${rules.size} reglas locales y preferencias",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            try {
                                val json = JSONObject().apply {
                                    put("corta_version", "1.0.0")
                                    put("export_date", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
                                    put("timestamp", System.currentTimeMillis())
                                    put("user_name", userName)
                                    put("user_phone", userPhone)
                                    val rulesArray = JSONArray()
                                    rules.forEach { rule ->
                                        val r = JSONObject().apply {
                                            put("pattern", rule.pattern)
                                            put("action", rule.action)
                                            put("isRegex", rule.isRegex == true)
                                            put("alias", rule.alias ?: "")
                                            put("source", rule.source)
                                        }
                                        rulesArray.put(r)
                                    }
                                    put("rules", rulesArray)
                                    val prefsObj = JSONObject().apply {
                                        put("only_contacts_calls", prefs.getBoolean("only_contacts_calls", false))
                                        put("block_private_numbers", prefs.getBoolean("block_private_numbers", false))
                                        put("block_international_calls", prefs.getBoolean("block_international_calls", false))
                                        put("chao_809_aggressive", prefs.getBoolean("chao_809_aggressive", true))
                                        put("silence_warn_calls", prefs.getBoolean("silence_warn_calls", false))
                                        put("recording_enabled", prefs.getBoolean("recording_enabled", true))
                                        put("protect_sms_links", prefs.getBoolean("protect_sms_links", true))
                                        put("filter_alphanumeric_senders", prefs.getBoolean("filter_alphanumeric_senders", true))
                                        put("sms_spam_notifications_enabled", prefs.getBoolean("sms_spam_notifications_enabled", true))
                                        put("allow_contacts_always", prefs.getBoolean("allow_contacts_always", true))
                                    }
                                    put("preferences", prefsObj)
                                }.toString(2)

                                val backupFile = File(context.cacheDir, "corta_backup_${System.currentTimeMillis()}.json")
                                backupFile.writeText(json)

                                val fileUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    backupFile
                                )

                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_STREAM, fileUri)
                                    putExtra(Intent.EXTRA_TEXT, json)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Guardar o compartir respaldo Corta"))
                                Toast.makeText(context, "¡Respaldo JSON generado!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error al generar respaldo: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Exportar y Compartir JSON", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Card 2: Restaurar
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Restaurar Respaldo",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Importa reglas desde un texto JSON",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = { showRestoreInput = !showRestoreInput }) {
                            Icon(
                                imageVector = if (showRestoreInput) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = "Expandir"
                            )
                        }
                    }

                    if (showRestoreInput) {
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = restoreJsonText,
                            onValueChange = { restoreJsonText = it },
                            placeholder = { Text("Pega el JSON de respaldo aquí...") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 160.dp),
                            textStyle = MaterialTheme.typography.bodySmall
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clip = cm?.primaryClip
                                    if (clip != null && clip.itemCount > 0) {
                                        restoreJsonText = clip.getItemAt(0).text?.toString() ?: ""
                                        Toast.makeText(context, "Texto pegado", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Portapapeles vacío", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.ContentPaste, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pegar")
                            }

                            Button(
                                onClick = {
                                    if (restoreJsonText.isBlank()) {
                                        Toast.makeText(context, "Por favor pega el JSON", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    try {
                                        val obj = JSONObject(restoreJsonText)
                                        var restoredCount = 0
                                        if (obj.has("rules")) {
                                            val rulesArr = obj.getJSONArray("rules")
                                            for (i in 0 until rulesArr.length()) {
                                                val r = rulesArr.getJSONObject(i)
                                                val pattern = r.getString("pattern")
                                                val actionStr = r.optString("action", "BLOCK")
                                                val isRegex = r.optBoolean("isRegex", false)
                                                val alias = if (r.has("alias") && !r.isNull("alias")) r.optString("alias").takeIf { it.isNotBlank() } else null
                                                val source = r.optString("source", "USER")
                                                cortaRepository.addRule(
                                                    pattern = pattern,
                                                    action = FilterAction.fromString(actionStr),
                                                    isRegex = isRegex,
                                                    alias = alias,
                                                    source = source
                                                )
                                                restoredCount++
                                            }
                                        }
                                        if (obj.has("preferences")) {
                                            val p = obj.getJSONObject("preferences")
                                            val editor = prefs.edit()
                                            p.keys().forEach { key ->
                                                val value = p.get(key)
                                                if (value is Boolean) editor.putBoolean(key, value)
                                                else if (value is String) editor.putString(key, value)
                                                else if (value is Int) editor.putInt(key, value)
                                            }
                                            editor.apply()
                                        }
                                        Toast.makeText(context, "¡$restoredCount reglas restauradas con éxito!", Toast.LENGTH_LONG).show()
                                        onDismiss()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "JSON inválido: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Restaurar")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * BottomSheet de Soporte y Contacto
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupportBottomSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Soporte y Contacto",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Estamos para ayudarte a proteger tu privacidad y resolver cualquier duda sobre Corta App.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth().clickable {
                    val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:soporte@corta.app?subject=Soporte%20Corta%20App")
                    }
                    try {
                        context.startActivity(emailIntent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Escríbenos a: soporte@corta.app", Toast.LENGTH_LONG).show()
                    }
                }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Mail, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enviar Correo Directo", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        Text("soporte@corta.app", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth().clickable {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.setPrimaryClip(ClipData.newPlainText("Corta Soporte", "soporte@corta.app"))
                    Toast.makeText(context, "Correo copiado al portapapeles", Toast.LENGTH_SHORT).show()
                }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Copiar Dirección de Correo", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        Text("Guárdalo para consultarnos cuando gustes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth().clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/axioma-dev/corta/issues"))
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "github.com/axioma-dev/corta", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.BugReport, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Reportar en GitHub Issues", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        Text("Sugerencias, errores y mejoras técnicas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

/**
 * BottomSheet con Centro de Ayuda y Preguntas Frecuentes de Corta App
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpFaqBottomSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp)
        ) {
            Text(
                text = "Centro de Ayuda y Preguntas Frecuentes",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Todo lo que necesitas saber sobre la protección y privacidad de Corta.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            val faqs = listOf(
                Pair(
                    "¿Cómo bloquea Corta las llamadas en < 2 milisegundos?",
                    "Corta evalúa cada llamada 100% en la memoria de tu dispositivo utilizando estructuras Trie y SQLCipher local. Nunca hace peticiones a internet cuando entra una llamada."
                ),
                Pair(
                    "¿Funciona la protección en equipos con Doble SIM?",
                    "Sí. Corta monitorea y protege de manera simultánea todas las ranuras SIM activas de tu dispositivo (físicas o eSIM), aplicando las mismas reglas de bloqueo."
                ),
                Pair(
                    "¿Por qué Corta no sube mis contactos a la nube?",
                    "A diferencia de otras apps que comercializan tu agenda, Corta opera bajo una estricta política de soberanía de datos chilena. Tu libreta jamás sale de tu teléfono."
                ),
                Pair(
                    "¿Qué es la normativa SUBTEL 809 de telemarketing?",
                    "En Chile, el prefijo 809 está reservado por ley para llamadas de ventas masivas no solicitadas. Corta lo bloquea automáticamente en cuanto suena el teléfono."
                ),
                Pair(
                    "¿Se pueden bloquear las líneas de emergencia por error?",
                    "No. Corta tiene una lista blanca nacional inmutable protegida por código (131, 132, 133, 134, seguridad comunal 14xx y Salud Responde 600) que jamás puede ser bloqueada."
                )
            )

            faqs.forEach { (q, a) ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = q,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = a,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
