package com.corta.app.ui.sms

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corta.app.ui.designsystem.StitchFilterChip
import com.corta.db.SpamKeyword
import com.corta.domain.CortaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpamKeywordsSheet(
    repository: CortaRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keywords by repository.observeSpamKeywords().collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("TODAS") }

    val filteredKeywords = remember(keywords, selectedFilter) {
        if (selectedFilter == "TODAS") keywords
        else keywords.filter { it.category.equals(selectedFilter, ignoreCase = true) }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Palabras Clave de Bloqueo SMS",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Gestión de términos bloqueados para smishing y fraude",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Agregar palabra clave",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Categorías de filtro
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StitchFilterChip(
                    selected = selectedFilter == "TODAS",
                    onClick = { selectedFilter = "TODAS" },
                    label = "Todas (${keywords.size})"
                )
                StitchFilterChip(
                    selected = selectedFilter == "SMISHING",
                    onClick = { selectedFilter = "SMISHING" },
                    label = "Smishing"
                )
                StitchFilterChip(
                    selected = selectedFilter == "FRAUD",
                    onClick = { selectedFilter = "FRAUD" },
                    label = "Fraude"
                )
                StitchFilterChip(
                    selected = selectedFilter == "MARKETING",
                    onClick = { selectedFilter = "MARKETING" },
                    label = "Marketing"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (filteredKeywords.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No hay palabras clave en esta categoría",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredKeywords, key = { it.id }) { kw ->
                        KeywordItemRow(
                            keyword = kw,
                            onToggle = { isActive ->
                                coroutineScope.launch(Dispatchers.IO) {
                                    repository.toggleSpamKeyword(kw.id, isActive)
                                }
                            },
                            onDelete = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    repository.deleteSpamKeyword(kw.id)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Palabra eliminada", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddKeywordDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { word, category ->
                showAddDialog = false
                coroutineScope.launch(Dispatchers.IO) {
                    repository.addSpamKeyword(word, category)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "\"$word\" agregada al filtro", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}

@Composable
private fun KeywordItemRow(
    keyword: SpamKeyword,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = keyword.keyword,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    CategoryBadge(category = keyword.category)
                }
                Text(
                    text = if (keyword.isActive == true) "Bloqueo activo en memoria (<2ms)" else "Desactivado",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (keyword.isActive == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = keyword.isActive == true,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Eliminar palabra",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun CategoryBadge(category: String) {
    val (bgColor, textColor, label) = when (category.uppercase()) {
        "SMISHING" -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, "Smishing")
        "FRAUD", "ESTAFA" -> Triple(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f), MaterialTheme.colorScheme.error, "Fraude")
        "MARKETING", "PROMO" -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, "Marketing")
        else -> Triple(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant, category)
    }

    Surface(
        shape = CircleShape,
        color = bgColor
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddKeywordDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var keywordText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("SMISHING") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva palabra o frase") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = keywordText,
                    onValueChange = { keywordText = it },
                    label = { Text("Palabra o término clave") },
                    placeholder = { Text("ej. bancoestado, clave única") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Tipo de filtro:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("SMISHING" to "Smishing", "FRAUD" to "Fraude", "MARKETING" to "Marketing").forEach { (cat, label) ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (keywordText.isNotBlank()) {
                        onAdd(keywordText.trim(), selectedCategory)
                    }
                },
                enabled = keywordText.isNotBlank()
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
