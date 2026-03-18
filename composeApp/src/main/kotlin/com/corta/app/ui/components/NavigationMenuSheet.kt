package com.corta.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.corta.app.ui.i18n.Strings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationMenuSheet(
    onDismiss: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToRules: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToDiagnostics: (() -> Unit)? = null
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 48.dp)
        ) {
            Text(
                text = "Opciones",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )

            Surface(
                onClick = {
                    onDismiss()
                    onNavigateToStats()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = androidx.compose.ui.graphics.Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.BarChart, null, tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(Strings.statistics, style = MaterialTheme.typography.bodyLarge)
                }
            }
            
            Surface(
                onClick = {
                    onDismiss()
                    onNavigateToSettings()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = androidx.compose.ui.graphics.Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Settings, null, tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(Strings.settings, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Surface(
                onClick = {
                    onDismiss()
                    onNavigateToRules()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = androidx.compose.ui.graphics.Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Security, null, tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(Strings.rules, style = MaterialTheme.typography.bodyLarge)
                }
            }

            onNavigateToDiagnostics?.let { onDiag ->
                Surface(
                    onClick = {
                        onDismiss()
                        onDiag()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = androidx.compose.ui.graphics.Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Build, null, tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Diagnóstico", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
