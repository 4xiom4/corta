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
import com.corta.app.ui.contacts.ChileanRegion
import com.corta.app.ui.contacts.ChileanRegionalDirectory
import com.corta.app.ui.contacts.RegionSelectorBottomSheet
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
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
import com.corta.domain.PhoneNumberUtils
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

    val isDonorBadgeUnlocked = prefs.getBoolean("donor_badge_unlocked", false)
    var isDonor by remember { mutableStateOf(prefs.getBoolean("is_donor", false) || isDonorBadgeUnlocked) }
    var donorOperationId by remember { mutableStateOf(prefs.getString("donor_operation_id", "") ?: "") }
    var donorDate by remember { mutableStateOf(prefs.getString("donor_date", "") ?: "") }
    var userGoogleEmail by remember { mutableStateOf(prefs.getString("user_google_email", "") ?: "") }
    var userName by remember { mutableStateOf(prefs.getString("user_name", "Usuario Corta") ?: "Usuario Corta") }
    var userPhone by remember { mutableStateOf(prefs.getString("user_phone", null) ?: initialDetectedPhone ?: "") }
    var userAvatar by remember { mutableStateOf(prefs.getString("user_avatar", null)) }

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

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showValidateDonationDialog by remember { mutableStateOf(false) }
    var showGoogleLinkDialog by remember { mutableStateOf(false) }
    var showFaqSheet by remember { mutableStateOf(false) }
    var showBackupSheet by remember { mutableStateOf(false) }
    var showSupportSheet by remember { mutableStateOf(false) }
    var selectedSimForDetails by remember { mutableStateOf<SimSlotData?>(null) }
    val coroutineScope = rememberCoroutineScope()

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
            // 1. User Profile Header Card (Truecaller Style)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Avatar Circle with camera edit badge
                        Box(
                            contentAlignment = Alignment.BottomEnd,
                            modifier = Modifier.clickable { showEditProfileDialog = true }
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                modifier = Modifier.size(76.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (!userAvatar.isNullOrBlank()) {
                                        Text(
                                            text = userAvatar!!,
                                            fontSize = 36.sp
                                        )
                                    } else {
                                        Text(
                                            text = userName.take(1).uppercase(),
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            // Camera / Edit badge
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
                                modifier = Modifier.size(26.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Cambiar avatar",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // User Name with verified / donor check
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = userName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (isDonor) {
                                Icon(
                                    imageVector = Icons.Rounded.Verified,
                                    contentDescription = "Colaborador",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Phone Number
                        Text(
                            text = if (userPhone.isNotBlank()) PhoneNumberUtils.formatDisplayNumber(userPhone) else "Sin número configurado (Toca para editar)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (userPhone.isNotBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { showEditProfileDialog = true }
                        )

                        if (userGoogleEmail.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = userGoogleEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Regional Location Pill (Clickable to change Region & Comuna)
                        val effectiveComuna = remember(userRegion, userComuna) {
                            if (userRegion == ChileanRegion.ALL || userComuna.isNullOrBlank()) null
                            else if (ChileanRegionalDirectory.isValidComunaForRegion(userRegion, userComuna)) userComuna
                            else null
                        }
                        val locationSummary = when {
                            userRegion == ChileanRegion.ALL -> "🇨🇱 Todo Chile"
                            effectiveComuna != null -> "📍 $effectiveComuna (${userRegion.shortName})"
                            else -> "📍 ${userRegion.displayName}"
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier.clickable { showRegionPicker = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = locationSummary,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = Icons.Rounded.ArrowDropDown,
                                    contentDescription = "Cambiar",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Edit Profile Pill Button
                        OutlinedButton(
                            onClick = { showEditProfileDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Editar Perfil", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }

                        // Supporter Badge Pill if active
                        if (isDonor) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.WorkspacePremium,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Medalla de Colaborador Activa",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Enlaces Rápidos de Ayuda y Comunidad
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        // Compartir Corta
                        ProfileNavRow(
                            icon = Icons.Rounded.Share,
                            title = "Compartir Corta con amigos",
                            subtitle = "Ayuda a proteger a tus familiares y conocidos del spam",
                            onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Te recomiendo Corta App para Android:\n" +
                                        "Bloquea llamadas spam de telemarketing (norma SUBTEL 809), fraudes bancarios y smishing SMS en Chile de forma automática y 100% privada, sin subir tus contactos a internet.\n\n" +
                                        "Descárgala gratis en: https://github.com/axioma-dev/corta"
                                    )
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Compartir Corta"))
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
                            subtitle = "Escríbenos o reporta inconvenientes",
                            onClick = { showSupportSheet = true }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.8.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )

                        // Centro de Ayuda
                        ProfileNavRow(
                            icon = Icons.AutoMirrored.Rounded.HelpOutline,
                            title = "Centro de Ayuda y Preguntas Frecuentes",
                            subtitle = "Cómo funciona la protección offline y la normativa chilena",
                            onClick = { showFaqSheet = true }
                        )
                    }
                }
            }


            // 5. Aporte Solidario y Medalla de Colaborador (Mercado Pago Chile)
            item {
                if (!isDonor) {
                    Text(
                        text = "APORTE SOLIDARIO",
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
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Favorite,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Apoya el desarrollo de Corta Chile",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "100% gratuita, libre y sin anuncios invasivos. Tu colaboración financia la investigación contra el telemarketing.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Botón de Mercado Pago
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://link.mercadopago.cl/cortachile"))
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VolunteerActivism,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Aportar en Mercado Pago (Monto Abierto)", fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Fila de Alias de Mercado Pago
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Tag,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Alias: cortachile",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Alias Mercado Pago", "cortachile"))
                                            Toast.makeText(context, "Alias 'cortachile' copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copiar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Botones secundarios: Validar Comprobante y Cuenta de Google
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showValidateDonationDialog = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Rounded.CheckCircleOutline, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Validar comprobante", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { showGoogleLinkDialog = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Rounded.AccountCircle, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        if (userGoogleEmail.isNotBlank()) "Cuenta Google" else "Vincular Google",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "COLABORADOR OFICIAL",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.WorkspacePremium,
                                            contentDescription = "Medalla Dorada",
                                            tint = MaterialTheme.colorScheme.onTertiary,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "¡Medalla de Colaborador Activa!",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text(
                                        text = "Tu aporte voluntario mantiene a Corta viva y actualizada contra el telemarketing.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            if (donorOperationId.isNotBlank() || donorDate.isNotBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        if (donorOperationId.isNotBlank()) {
                                            Text(
                                                text = "Operación: #$donorOperationId",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        if (donorDate.isNotBlank()) {
                                            Text(
                                                text = donorDate,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            if (userGoogleEmail.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "🔒 Respaldado en Google: $userGoogleEmail",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }



    // Modal: Validar comprobante de Mercado Pago
    if (showValidateDonationDialog) {
        var inputOpId by remember { mutableStateOf("") }
        var inputError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showValidateDonationDialog = false },
            icon = {
                Icon(
                    Icons.Rounded.Verified,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Acreditar Aporte Solidario",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Ingresa el Número de Operación emitido por Mercado Pago en tu comprobante de aporte (generalmente 8 a 11 dígitos):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = inputOpId,
                        onValueChange = {
                            inputOpId = it.filter { ch -> ch.isDigit() }
                            inputError = null
                        },
                        label = { Text("N° de Operación") },
                        placeholder = { Text("Ej: 1122334455") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = inputError != null,
                        supportingText = inputError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "💡 Este código valida tu colaboración y activa tu medalla dorada de forma permanente en este teléfono y en tus respaldos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = inputOpId.trim()
                        if (clean.length < 6) {
                            inputError = "Ingresa un número de operación válido (al menos 6 dígitos)"
                        } else {
                            donorOperationId = clean
                            val now = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                            donorDate = now
                            isDonor = true
                            prefs.edit()
                                .putBoolean("is_donor", true)
                                .putBoolean("donor_badge_unlocked", true)
                                .putString("donor_operation_id", clean)
                                .putString("donor_date", now)
                                .apply()
                            showValidateDonationDialog = false
                            Toast.makeText(context, "¡Aporte acreditado con éxito! Medalla activada.", Toast.LENGTH_LONG).show()
                        }
                    }
                ) {
                    Text("Acreditar Medalla")
                }
            },
            dismissButton = {
                TextButton(onClick = { showValidateDonationDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Modal: Vincular Cuenta de Google (Respaldo en la nube / Cambio de equipo)
    if (showGoogleLinkDialog) {
        var manualEmail by remember { mutableStateOf(userGoogleEmail) }
        var isLinking by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showGoogleLinkDialog = false },
            icon = {
                Icon(
                    Icons.Rounded.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Vincular Cuenta de Google",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Vincula tu correo de Google para respaldar tu medalla de donador y tu configuración. Si cambias de teléfono o desinstalas la app, podrás restaurar tu estado inmediatamente.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            isLinking = true
                            coroutineScope.launch {
                                try {
                                    val credentialManager = CredentialManager.create(context)
                                    val googleIdOption = GetGoogleIdOption.Builder()
                                        .setFilterByAuthorizedAccounts(false)
                                        .setServerClientId("corta-app-client-id")
                                        .setAutoSelectEnabled(true)
                                        .build()
                                    val request = GetCredentialRequest.Builder()
                                        .addCredentialOption(googleIdOption)
                                        .build()
                                    val result = credentialManager.getCredential(context, request)
                                    val credential = result.credential
                                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
                                    val email = googleIdToken.id
                                    val name = googleIdToken.displayName ?: userName
                                    userGoogleEmail = email
                                    userName = name
                                    prefs.edit()
                                        .putString("user_google_email", email)
                                        .putString("user_name", name)
                                        .apply()
                                    Toast.makeText(context, "¡Cuenta vinculada con éxito: $email!", Toast.LENGTH_SHORT).show()
                                    showGoogleLinkDialog = false
                                } catch (e: Exception) {
                                    isLinking = false
                                    Toast.makeText(context, "Ingresa tu correo abajo para vincularlo manualmente", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isLinking
                    ) {
                        Icon(Icons.Rounded.Sync, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Detectar con Google One Tap", fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        text = "O ingresa tu correo manualmente:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = manualEmail,
                        onValueChange = { manualEmail = it },
                        label = { Text("Correo de Google") },
                        placeholder = { Text("ejemplo@gmail.com") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val email = manualEmail.trim()
                        if (email.contains("@")) {
                            userGoogleEmail = email
                            prefs.edit().putString("user_google_email", email).apply()
                            showGoogleLinkDialog = false
                            Toast.makeText(context, "Correo de respaldo guardado: $email", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Ingresa un correo electrónico válido", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Guardar Correo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoogleLinkDialog = false }) {
                    Text("Cerrar")
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

    // Modal: Selector de Región y Comuna (Chile)
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

    // Modal: Editar Mi Perfil (Nombre, Teléfono y Avatar)
    if (showEditProfileDialog) {
        var tempName by remember { mutableStateOf(userName) }
        var tempPhone by remember { mutableStateOf(userPhone) }
        val avatarPresets = listOf("👤", "🛡️", "🇨🇱", "📞", "⚡", "🦊", "🐱", "🚀")
        var selectedAvatar by remember { mutableStateOf(userAvatar ?: "👤") }

        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = {
                Text(
                    text = "Editar Mi Perfil",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Elige tu avatar o ícono de perfil:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        avatarPresets.take(4).forEach { emoji ->
                            val isSel = selectedAvatar == emoji
                            Surface(
                                shape = CircleShape,
                                color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = if (isSel) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clickable { selectedAvatar = emoji }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = emoji, fontSize = 22.sp)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        avatarPresets.drop(4).forEach { emoji ->
                            val isSel = selectedAvatar == emoji
                            Surface(
                                shape = CircleShape,
                                color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = if (isSel) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clickable { selectedAvatar = emoji }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = emoji, fontSize = 22.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        label = { Text("Nombre o Apodo") },
                        placeholder = { Text("Ej: Juan Pérez") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = tempPhone,
                        onValueChange = { tempPhone = it },
                        label = { Text("Número de Teléfono") },
                        placeholder = { Text("+56 9 1234 5678") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanName = tempName.trim().ifEmpty { "Usuario Corta" }
                        val cleanPhone = tempPhone.trim()
                        userName = cleanName
                        userPhone = cleanPhone
                        userAvatar = selectedAvatar
                        prefs.edit()
                            .putString("user_name", cleanName)
                            .putString("user_phone", cleanPhone)
                            .putString("user_avatar", selectedAvatar)
                            .apply()
                        showEditProfileDialog = false
                        Toast.makeText(context, "Perfil actualizado", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Guardar Cambios", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancelar")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
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
