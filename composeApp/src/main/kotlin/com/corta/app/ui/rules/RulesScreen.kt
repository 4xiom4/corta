package com.corta.app.ui.rules

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corta.app.workers.SubtelSyncScheduler
import com.corta.app.ui.i18n.Strings
import com.corta.db.FilterRule
import com.corta.domain.CortaRepository
import com.corta.domain.FilterAction
import org.koin.compose.koinInject
import android.content.Context

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    onBack: () -> Unit = {},
    repository: CortaRepository = koinInject()
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("corta_prefs", Context.MODE_PRIVATE) }
    val rules by repository.observeRules().collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var useSubtelRules by remember { mutableStateOf(prefs.getBoolean("use_subtel_rules", true)) }

    val subtelRules = remember(rules) { rules.filter { it.source == "SUBTEL" } }
    val userRules = remember(rules) { rules.filter { it.source != "SUBTEL" } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(Strings.rules, style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(Strings.addRule) }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 88.dp) // Space for FAB
        ) {
            item {
                Text(
                    text = "Protección Automática",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            item {
                ListItem(
                    headlineContent = { Text("Reglas Anti-Spam (Subtel)", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Bloqueo automático según normativa vigente.") },
                    leadingContent = { Icon(Icons.Rounded.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        Switch(
                            checked = useSubtelRules,
                            onCheckedChange = {
                                useSubtelRules = it
                                prefs.edit().putBoolean("use_subtel_rules", it).apply()
                                SubtelSyncScheduler.enqueueImmediate(context, it)
                                SubtelSyncScheduler.ensurePeriodic(context, it)
                            }
                        )
                    }
                )
            }

            if (subtelRules.isNotEmpty()) {
                item {
                    Text(
                        text = "Prefijos bloqueados por SUBTEL",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(subtelRules, key = { "subtel_${it.id}" }) { rule ->
                    RuleListItem(rule = rule, onDelete = null)
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                Text(
                    text = Strings.blockedNumbers,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (userRules.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            Strings.noRulesDefined,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(userRules, key = { "user_${it.id}" }) { rule ->
                    RuleListItem(
                        rule = rule,
                        onDelete = { repository.deleteRule(rule.pattern) }
                    )
                }
            }
        }

        if (showAddDialog) {
            AddRuleDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { number, alias ->
                    repository.addRule(number, FilterAction.BLOCK, false, alias)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun RuleListItem(rule: FilterRule, onDelete: (() -> Unit)?) {
    ListItem(
        headlineContent = { Text(rule.pattern, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium) },
        supportingContent = {
            if (!rule.alias.isNullOrBlank()) {
                Text(rule.alias!!, style = MaterialTheme.typography.bodySmall)
            } else {
                Text(if (rule.source == "SUBTEL") "Fuente: SUBTEL" else "Fuente: Usuario", style = MaterialTheme.typography.bodySmall)
            }
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.Block,
                contentDescription = null,
                tint = if (rule.source == "SUBTEL") MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = Strings.delete,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    )
}

@Composable
fun AddRuleDialog(onDismiss: () -> Unit, onAdd: (String, String?) -> Unit) {
    var phoneNumber by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.addRule, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(Strings.enterPhoneNumber, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text(Strings.phoneNumber) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("Apodo corto (Opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (phoneNumber.isNotBlank()) {
                        onAdd(phoneNumber, alias.ifBlank { null })
                    }
                }
            ) {
                Text(Strings.add)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Strings.cancel)
            }
        },
        shape = MaterialTheme.shapes.extraLarge
    )
}
