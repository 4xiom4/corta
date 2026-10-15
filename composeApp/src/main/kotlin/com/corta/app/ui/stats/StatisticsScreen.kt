package com.corta.app.ui.stats

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Build
import android.provider.Telephony
import android.telecom.TelecomManager
import android.content.Context
import com.corta.app.ui.i18n.Strings
import com.corta.domain.CortaRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import timber.log.Timber
import java.util.Calendar
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    repository: CortaRepository = koinInject(),
    systemLogImporter: com.corta.app.data.AndroidSystemLogImporter = koinInject(),
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val callLogs by repository.observeCallLogs().collectAsState(initial = emptyList())
    val smsLogs by repository.observeSmsLogs().collectAsState(initial = emptyList())

    // Estados para animación y loading
    var showNoDataMessage by remember { mutableStateOf(false) }
    var hasImportedHistorical by remember { mutableStateOf(false) }

    val totalBlockedCalls = remember(callLogs) { callLogs.count { it.actionTaken == "BLOCK" } }
    val totalBlockedSms = remember(smsLogs) { smsLogs.count { it.actionTaken == "BLOCK" } }
    val totalCalls = remember(callLogs) { callLogs.size }
    val totalSms = remember(smsLogs) { smsLogs.size }

    val topSpammers = remember(callLogs, smsLogs) {
        val counts = mutableMapOf<String, Int>()
        callLogs.filter { it.actionTaken == "BLOCK" }.forEach { counts[it.phoneNumber] = (counts[it.phoneNumber] ?: 0) + 1 }
        smsLogs.filter { it.actionTaken == "BLOCK" }.forEach { counts[it.sender] = (counts[it.sender] ?: 0) + 1 }
        counts.toList().sortedByDescending { it.second }.take(5)
    }

    LaunchedEffect(callLogs, smsLogs) {
        showNoDataMessage = callLogs.isEmpty() && smsLogs.isEmpty()

        // Importar datos históricos si no hay datos y no se ha importado antes
        if (!hasImportedHistorical && showNoDataMessage) {
            hasImportedHistorical = true
            scope.launch {
                try {
                    systemLogImporter.importAll(context)
                    Timber.tag(TAG).d("Imported historical data from system")
                } catch (e: Throwable) {
                    Timber.tag(TAG).e(e, "Error importing historical data")
                }
            }
        }
    }

    // Calcular estadísticas adicionales
    val blockedCallsPercentage = if (totalCalls > 0) (totalBlockedCalls.toFloat() / totalCalls * 100).toInt() else 0
    val blockedSmsPercentage = if (totalSms > 0) (totalBlockedSms.toFloat() / totalSms * 100).toInt() else 0

    val spamByHour = remember(callLogs, smsLogs) {
        val hours = IntArray(24) { 0 }
        val cal = Calendar.getInstance()
        callLogs.filter { it.actionTaken == "BLOCK" }.forEach {
            cal.timeInMillis = it.timestamp
            hours[cal.get(Calendar.HOUR_OF_DAY)]++
        }
        smsLogs.filter { it.actionTaken == "BLOCK" }.forEach {
            cal.timeInMillis = it.timestamp
            hours[cal.get(Calendar.HOUR_OF_DAY)]++
        }
        hours.toList()
    }

    val protectionScore = remember(totalCalls, totalSms, totalBlockedCalls, totalBlockedSms) {
        val totalInteractions = totalCalls + totalSms
        val totalBlocked = totalBlockedCalls + totalBlockedSms
        if (totalInteractions == 0) 0 else (totalBlocked.toFloat() / totalInteractions * 100).toInt()
    }

    val timeSavedMinutes = remember(totalBlockedCalls, totalBlockedSms) {
        (totalBlockedCalls * 0.5 + totalBlockedSms * 0.2).toInt() // 30s por llamada, 12s por SMS
    }

    val todayBlocked = remember(callLogs, smsLogs) {
        val today = System.currentTimeMillis() / (24 * 60 * 60 * 1000) * (24 * 60 * 60 * 1000)
        callLogs.filter { it.timestamp >= today && it.actionTaken == "BLOCK" }.size +
        smsLogs.filter { it.timestamp >= today && it.actionTaken == "BLOCK" }.size
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.stats.statistics, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { padding ->
        if (showNoDataMessage) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(Icons.Rounded.BarChart, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    Spacer(Modifier.height(16.dp))
                    Text(Strings.stats.noData, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(Strings.stats.importing, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    systemLogImporter.importAll(context)
                                    Timber.tag(TAG).d("Manual historical data import triggered")
                                } catch (e: Throwable) {
                                    Timber.tag(TAG).e(e, "Error importing historical data")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(Strings.stats.importHistorical)
                    }
                    Text(
                        "Las estadísticas aparecerán cuando Corta bloquee tus primeras comunicaciones spam.",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatCard(
                            title = Strings.stats.sectionCalls,
                            value = totalBlockedCalls.toString(),
                            subtitle = Strings.stats.blockedLabel,
                            icon = Icons.Rounded.Phone,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.primaryContainer
                        )
                        StatCard(
                            title = Strings.stats.sectionSms,
                            value = totalBlockedSms.toString(),
                            subtitle = Strings.stats.blockedLabelPlural,
                            icon = Icons.AutoMirrored.Rounded.Message,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        )
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(Strings.stats.protectionLevel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(Strings.stats.basedOnFiltered, style = MaterialTheme.typography.bodySmall)
                            }
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = { protectionScore / 100f },
                                    modifier = Modifier.size(56.dp),
                                    strokeWidth = 6.dp,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Text("$protectionScore%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(Strings.stats.timeSaved, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (timeSavedMinutes < 60) "~$timeSavedMinutes minutos" else "~${timeSavedMinutes / 60}h ${timeSavedMinutes % 60}m",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(Strings.stats.timeSavedCaption, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(Strings.stats.spamByHour, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(24.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().height(120.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                val max = spamByHour.maxOrNull()?.coerceAtLeast(1) ?: 1
                                spamByHour.forEachIndexed { index, count ->
                                    if (index % 4 == 0 || count > 0) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight(count.toFloat() / max)
                                                .padding(horizontal = 2.dp)
                                                .background(
                                                    if (count > 0) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.surfaceVariant,
                                                    RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                                )
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("00:00", style = MaterialTheme.typography.labelSmall)
                                Text("12:00", style = MaterialTheme.typography.labelSmall)
                                Text("23:59", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                if (topSpammers.isNotEmpty()) {
                    item {
                        Text(Strings.stats.topSpammersList, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    }
                    items(topSpammers) { (number, count) ->
                        ListItem(
                            headlineContent = { Text(number, fontWeight = FontWeight.Bold) },
                            supportingContent = { Text(Strings.stats.connectionAttempts.format(count)) },
                            leadingContent = {
                                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer) {
                                    Icon(Icons.Rounded.Error, null, modifier = Modifier.padding(8.dp).size(20.dp), tint = MaterialTheme.colorScheme.error)
                                }
                            },
                            trailingContent = {
                                Text(count.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

// Funciones helper para verificar configuración
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

private const val TAG = "Corta.Stats"
