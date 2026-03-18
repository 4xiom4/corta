package com.corta.app.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

data class SimInfo(val subscriptionId: Int, val displayName: String, val number: String)

@Composable
fun SimSelectionDialog(
    onDismiss: () -> Unit,
    onSimSelected: (String) -> Unit
) {
    val context = LocalContext.current
    var simList by remember { mutableStateOf<List<SimInfo>>(emptyList()) }
    var manualNumber by remember { mutableStateOf("") }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        )
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val activeSubscriptionInfoList = subscriptionManager.activeSubscriptionInfoList
            
            val list = mutableListOf<SimInfo>()
            activeSubscriptionInfoList?.forEach { info ->
                list.add(
                    SimInfo(
                        subscriptionId = info.subscriptionId,
                        displayName = info.displayName?.toString() ?: "SIM ${info.simSlotIndex + 1}",
                        number = info.number ?: ""
                    )
                )
            }
            simList = list
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurar SIM / Número Principal") },
        text = {
            Column {
                if (simList.isNotEmpty()) {
                    Text("Selecciona una tarjeta SIM:")
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                        items(simList) { sim ->
                            ListItem(
                                modifier = Modifier.clickable { onSimSelected(sim.number) },
                                headlineContent = { Text(sim.displayName) },
                                supportingContent = { Text(sim.number.ifEmpty { "Número no detectado" }) },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("O ingresa manualmente:")
                } else {
                    Text("Ingresa tu número telefónico principal (ej: +569...):")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = manualNumber,
                    onValueChange = { manualNumber = it },
                    label = { Text("Número telefónico") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (manualNumber.isNotBlank()) {
                        onSimSelected(manualNumber)
                    }
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}
