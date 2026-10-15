package com.corta.app.ui.rules

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corta.app.ui.i18n.rememberCortaStrings
import com.corta.app.ui.theme.LocalDetailedMode
import com.corta.db.FilterRule
import com.corta.domain.CortaRepository
import com.corta.domain.FilterAction
import org.koin.compose.koinInject

enum class RuleCategoryFilter {
    ALL, SUBTEL, WHITELIST, CUSTOM
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    onBack: () -> Unit = {},
    repository: CortaRepository = koinInject()
) {
    val strings = rememberCortaStrings()
    val isDetailedMode = LocalDetailedMode.current
    val rules by repository.observeRules().collectAsState(initial = emptyList())

    var selectedCategory by remember { mutableStateOf(RuleCategoryFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    val subtelRules = remember(rules) { rules.filter { it.source == "SUBTEL" } }
    val userRules = remember(rules) { rules.filter { it.source == "USER" || it.source == "MANUAL" } }

    val emergencyList = remember {
        listOf(
            Triple("131", "SAMU (Ambulancia)", "Emergencia"),
            Triple("132", "Cuerpo de Bomberos de Chile", "Emergencia"),
            Triple("133", "Carabineros de Chile (Emergencias)", "Emergencia"),
            Triple("134", "Policía de Investigaciones (PDI)", "Emergencia"),
            Triple("137", "Búsqueda y Salvamento Marítimo", "Emergencia"),
            Triple("147", "Fono Familia (Violencia Intrafamiliar)", "Familia"),
            Triple("149", "Fono Niños Protegidos", "Familia"),
            Triple("14xx", "Seguridad Ciudadana Municipal (1402, 1414, 1406...)", "Municipal"),
            Triple("600 360 7777", "Salud Responde (MINSAL)", "Salud")
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                isSearchActive = false
                                searchQuery = ""
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Atrás",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    "Buscar número, prefijo o regla...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Rounded.Close, contentDescription = "Limpiar")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                strings.common.rules,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Normativa SUBTEL y listas locales",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = strings.diagnostics.backCd
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Rounded.Search, contentDescription = "Buscar")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Nueva Regla", fontWeight = FontWeight.Bold) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Horizontal scrollable category filter pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val totalCount = subtelRules.size + emergencyList.size + userRules.size
                FilterChip(
                    selected = selectedCategory == RuleCategoryFilter.ALL,
                    onClick = { selectedCategory = RuleCategoryFilter.ALL },
                    label = { Text("Todas ($totalCount)") },
                    shape = RoundedCornerShape(20.dp)
                )
                FilterChip(
                    selected = selectedCategory == RuleCategoryFilter.SUBTEL,
                    onClick = { selectedCategory = RuleCategoryFilter.SUBTEL },
                    label = { Text("SUBTEL 809 (${subtelRules.size})") },
                    leadingIcon = {
                        Icon(Icons.Rounded.VerifiedUser, null, modifier = Modifier.size(16.dp))
                    },
                    shape = RoundedCornerShape(20.dp)
                )
                FilterChip(
                    selected = selectedCategory == RuleCategoryFilter.WHITELIST,
                    onClick = { selectedCategory = RuleCategoryFilter.WHITELIST },
                    label = { Text("Lista Blanca (${emergencyList.size})") },
                    leadingIcon = {
                        Icon(Icons.Rounded.HealthAndSafety, null, modifier = Modifier.size(16.dp))
                    },
                    shape = RoundedCornerShape(20.dp)
                )
                FilterChip(
                    selected = selectedCategory == RuleCategoryFilter.CUSTOM,
                    onClick = { selectedCategory = RuleCategoryFilter.CUSTOM },
                    label = { Text("Mis Reglas (${userRules.size})") },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAddCheck, null, modifier = Modifier.size(16.dp))
                    },
                    shape = RoundedCornerShape(20.dp)
                )
            }

            // Rules List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // SUBTEL Section
                if (selectedCategory == RuleCategoryFilter.ALL || selectedCategory == RuleCategoryFilter.SUBTEL) {
                    val filteredSubtel = subtelRules.filter {
                        searchQuery.isBlank() ||
                            it.pattern.contains(searchQuery, ignoreCase = true) ||
                            (it.alias?.contains(searchQuery, ignoreCase = true) == true)
                    }

                    item {
                        SubtelBannerCard()
                    }

                    if (filteredSubtel.isNotEmpty()) {
                        items(filteredSubtel, key = { "subtel_${it.id}" }) { rule ->
                            RuleCard(
                                rule = rule,
                                isDetailedMode = isDetailedMode,
                                isWhitelist = false,
                                onDelete = null
                            )
                        }
                    }
                }

                // Whitelist Section (Inmutable Emergency & Municipal Lines)
                if (selectedCategory == RuleCategoryFilter.ALL || selectedCategory == RuleCategoryFilter.WHITELIST) {
                    val filteredWhitelist = emergencyList.filter { (number, name, _) ->
                        searchQuery.isBlank() ||
                            number.contains(searchQuery, ignoreCase = true) ||
                            name.contains(searchQuery, ignoreCase = true)
                    }

                    item {
                        Text(
                            text = "LISTA BLANCA CRÍTICA INMUTABLE",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
                        )
                    }

                    items(filteredWhitelist, key = { "whitelist_${it.first}" }) { (number, name, tag) ->
                        WhitelistCard(
                            number = number,
                            serviceName = name,
                            tag = tag,
                            isDetailedMode = isDetailedMode
                        )
                    }
                }

                // Custom User Rules Section
                if (selectedCategory == RuleCategoryFilter.ALL || selectedCategory == RuleCategoryFilter.CUSTOM) {
                    val filteredUser = userRules.filter {
                        searchQuery.isBlank() ||
                            it.pattern.contains(searchQuery, ignoreCase = true) ||
                            (it.alias?.contains(searchQuery, ignoreCase = true) == true)
                    }

                    item {
                        Text(
                            text = "REGLAS PERSONALIZADAS (${filteredUser.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
                        )
                    }

                    if (filteredUser.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.PlaylistAddCheck,
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Sin reglas personalizadas",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Toca '+ Nueva Regla' para bloquear prefijos o números específicos.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredUser, key = { "user_${it.id}" }) { rule ->
                            RuleCard(
                                rule = rule,
                                isDetailedMode = isDetailedMode,
                                isWhitelist = false,
                                onDelete = { repository.deleteRule(rule.pattern) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddRuleDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { pattern, isRegex, alias ->
                repository.addRule(pattern, FilterAction.BLOCK, isRegex = isRegex, alias = alias)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun SubtelBannerCard() {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Norma SUBTEL Chile (809 y 600)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Telemarketing masivo no solicitado bloqueado de fábrica en memoria local (<2ms) sin peticiones a internet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun RuleCard(
    rule: FilterRule,
    isDetailedMode: Boolean,
    isWhitelist: Boolean,
    onDelete: (() -> Unit)?
) {
    val isSubtel = rule.source == "SUBTEL"

    val friendlyTitle = remember(rule.pattern, rule.source) {
        when {
            rule.pattern.contains("809") -> "Prefijo 809 (Telemarketing Masivo)"
            rule.pattern.contains("600") -> "Prefijo 600 (Avisos de Cobranza)"
            rule.isRegex == true -> "Patrón Regex: ${rule.pattern}"
            rule.pattern.length <= 5 -> "Prefijo ${rule.pattern}"
            else -> rule.pattern
        }
    }

    val friendlySubtitle = remember(rule.alias, rule.source) {
        rule.alias ?: if (isSubtel) "Bloqueo preventivo por Ley SUBTEL" else "Bloqueo personalizado de usuario"
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isWhitelist) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isWhitelist) Icons.Rounded.CheckCircle else Icons.Rounded.Block,
                                contentDescription = null,
                                tint = if (isWhitelist) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = friendlyTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSubtel) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = if (isSubtel) "SUBTEL" else if (rule.isRegex == true) "Regex" else "Manual",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSubtel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = friendlySubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Eliminar",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Forensic Telemetry in Detailed Mode
            if (isDetailedMode) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "⚡ <0.5ms (Trie)",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (rule.isRegex == true) "Regex: ${rule.pattern}" else "Patrón: ${rule.pattern}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "Acción: ${rule.action}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WhitelistCard(
    number: String,
    serviceName: String,
    tag: String,
    isDetailedMode: Boolean
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.HealthAndSafety,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = number,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = serviceName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "Inmutable",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (isDetailedMode) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "Prioridad 1 (Pre-Evaluación)",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "Legal Chile",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

enum class RuleMatchType {
    PREFIX, EXACT, REGEX
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRuleDialog(
    onDismiss: () -> Unit,
    onAdd: (pattern: String, isRegex: Boolean, alias: String?) -> Unit
) {
    val strings = rememberCortaStrings()
    var pattern by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }
    var matchType by remember { mutableStateOf(RuleMatchType.PREFIX) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Agregar Regla de Bloqueo",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Tipo de Coincidencia",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = matchType == RuleMatchType.PREFIX,
                        onClick = { matchType = RuleMatchType.PREFIX },
                        label = { Text("Prefijo", style = MaterialTheme.typography.labelMedium) },
                        shape = RoundedCornerShape(12.dp)
                    )
                    FilterChip(
                        selected = matchType == RuleMatchType.EXACT,
                        onClick = { matchType = RuleMatchType.EXACT },
                        label = { Text("Exacto", style = MaterialTheme.typography.labelMedium) },
                        shape = RoundedCornerShape(12.dp)
                    )
                    FilterChip(
                        selected = matchType == RuleMatchType.REGEX,
                        onClick = { matchType = RuleMatchType.REGEX },
                        label = { Text("Regex", style = MaterialTheme.typography.labelMedium) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                val placeholderText = when (matchType) {
                    RuleMatchType.PREFIX -> "Ej: 92225 o 809"
                    RuleMatchType.EXACT -> "Ej: +56912345678"
                    RuleMatchType.REGEX -> "Ej: .*92225.*"
                }

                val helperText = when (matchType) {
                    RuleMatchType.PREFIX -> "Bloquea cualquier número que comience con estos dígitos."
                    RuleMatchType.EXACT -> "Bloquea únicamente el número exacto ingresado."
                    RuleMatchType.REGEX -> "Expresión regular avanzada para patrones complejos."
                }

                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Número o Dígitos") },
                    placeholder = { Text(placeholderText) },
                    supportingText = { Text(helperText, style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("Motivo / Etiqueta (opcional)") },
                    placeholder = { Text("Ej: Cobranzas / Ventas") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pattern.isNotBlank()) {
                        val isRegex = matchType == RuleMatchType.REGEX
                        onAdd(pattern.trim(), isRegex, alias.ifBlank { null })
                    }
                },
                enabled = pattern.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.common.cancel)
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}
