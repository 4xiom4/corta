package com.corta.app.ui.contacts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.corta.app.ui.designsystem.CortaCategoryExploreButton
import com.corta.app.ui.designsystem.CortaFloatingActionButton
import com.corta.app.ui.designsystem.StitchFilterChip
import com.corta.app.ui.export.CortaExportImportHelper
import com.corta.app.ui.i18n.Strings
import com.corta.app.ui.theme.Handedness
import com.corta.app.ui.theme.LocalDetailedMode
import com.corta.app.ui.theme.LocalHandedness
import com.corta.domain.ChileanTelecomClassifier
import com.corta.domain.ContactInfo
import com.corta.domain.ContactRepository
import com.corta.domain.CortaRepository
import com.corta.domain.FilterAction
import com.corta.domain.PhoneNumberUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.corta.db.ContactGroup
import org.koin.compose.koinInject

enum class ContactCategoryFilter {
    ALL,
    FAVORITES,
    EMERGENCIES,
    MUNICIPAL,
    OFFICIAL_600,
    PERSONAL
}

enum class ContactSourceFilter(val displayName: String) {
    ALL("Todas las cuentas"),
    GOOGLE("Google"),
    WHATSAPP("WhatsApp"),
    SIM("Tarjeta SIM"),
    DEVICE("Teléfono local")
}

enum class ContactSortOrder(val displayName: String) {
    NAME_ASC("Nombre (A - Z)"),
    NAME_DESC("Nombre (Z - A)"),
    FAVORITES_FIRST("Favoritos primero"),
    PHONE_ONLY("Solo con teléfono")
}

enum class ContactAvatarType {
    INITIALS,
    EMERGENCY_SHIELD,
    MUNICIPAL_POLICE,
    OFFICIAL_BANK
}

@Immutable
data class ClassifiedContact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val displayPhone: String,
    val isFavorite: Boolean,
    val category: ContactCategoryFilter,
    val avatarType: ContactAvatarType,
    val tag: String,
    val isVerified: Boolean,
    val isEmergency: Boolean = false,
    val isMunicipal: Boolean = false,
    val subtitle: String? = null,
    val rawContact: ContactInfo? = null
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    repository: ContactRepository,
    onContactClick: (ContactInfo) -> Unit,
    onCallClick: (ContactInfo) -> Unit,
    onMessageClick: (ContactInfo) -> Unit,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    viewModel: ContactsViewModel = org.koin.androidx.compose.koinViewModel()
) {
    val context = LocalContext.current
    val cortaRepository: CortaRepository = koinInject()
    val isDetailedMode = LocalDetailedMode.current
    val coroutineScope = rememberCoroutineScope()
    val strings = Strings

    val rawContacts by viewModel.contacts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val prefs = remember { context.getSharedPreferences("corta_prefs", Context.MODE_PRIVATE) }
    val defaultOrder = remember {
        listOf(
            ContactCategoryFilter.PERSONAL,
            ContactCategoryFilter.FAVORITES,
            ContactCategoryFilter.EMERGENCIES,
            ContactCategoryFilter.MUNICIPAL,
            ContactCategoryFilter.OFFICIAL_600,
            ContactCategoryFilter.ALL
        )
    }
    var categoryOrder by remember {
        val saved = prefs.getString("contact_category_order", null)
        val list = if (saved != null) {
            saved.split(",").mapNotNull { name ->
                try { ContactCategoryFilter.valueOf(name) } catch (e: Exception) { null }
            }
        } else emptyList()
        mutableStateOf(if (list.size == defaultOrder.size) list else defaultOrder)
    }
    var currentFilter by remember { mutableStateOf(categoryOrder.firstOrNull() ?: ContactCategoryFilter.PERSONAL) }
    var highlightedTagForDictionary by remember { mutableStateOf<String?>(null) }
    var showCategoriesSheet by remember { mutableStateOf(false) }
    var showRegionPicker by remember { mutableStateOf(false) }
    var showReorderSheet by remember { mutableStateOf(false) }
    var selectedContactForOptions by remember { mutableStateOf<ClassifiedContact?>(null) }

    var selectedRegion by remember {
        val savedCode = prefs.getString("user_selected_region", ChileanRegion.RM.code)
        mutableStateOf(ChileanRegion.fromCode(savedCode))
    }

    var selectedComuna by remember {
        mutableStateOf(prefs.getString("user_selected_comuna", null))
    }

    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "user_selected_region" || key == "user_selected_comuna") {
                selectedRegion = ChileanRegion.fromCode(prefs.getString("user_selected_region", ChileanRegion.RM.code))
                selectedComuna = prefs.getString("user_selected_comuna", null)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val contactGroups by cortaRepository.observeContactGroups().collectAsState(initial = emptyList())
    var selectedGroup by remember { mutableStateOf<ContactGroup?>(null) }
    var groupMemberIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(selectedGroup) {
        val grp = selectedGroup
        groupMemberIds = if (grp != null) {
            withContext(Dispatchers.IO) {
                cortaRepository.getGroupMembers(grp.id).toSet()
            }
        } else emptySet()
    }

    var currentSourceFilter by remember { mutableStateOf(ContactSourceFilter.ALL) }
    var currentSortOrder by remember {
        val savedSort = prefs.getString("contact_sort_order", ContactSortOrder.NAME_ASC.name)
        mutableStateOf(try { ContactSortOrder.valueOf(savedSort ?: ContactSortOrder.NAME_ASC.name) } catch (e: Exception) { ContactSortOrder.NAME_ASC })
    }

    val sourceCounts = remember(rawContacts) {
        val total = rawContacts.size
        val google = rawContacts.count { it.source.equals("GOOGLE", ignoreCase = true) }
        val wa = rawContacts.count { it.source.equals("WHATSAPP", ignoreCase = true) }
        val sim = rawContacts.count { it.source.equals("SIM", ignoreCase = true) }
        val dev = rawContacts.count { it.source.equals("DEVICE", ignoreCase = true) || it.source.isBlank() }
        mapOf(
            ContactSourceFilter.ALL to total,
            ContactSourceFilter.GOOGLE to google,
            ContactSourceFilter.WHATSAPP to wa,
            ContactSourceFilter.SIM to sim,
            ContactSourceFilter.DEVICE to dev
        )
    }

    val vcfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                val imported = CortaExportImportHelper.importContactsFromVcf(context, it)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "${imported.size} contactos leídos desde archivo vCard", Toast.LENGTH_SHORT).show()
                    viewModel.refresh()
                }
            }
        }
    }

    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                val imported = CortaExportImportHelper.importContactsFromCsv(context, it)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "${imported.size} contactos importados desde archivo CSV", Toast.LENGTH_SHORT).show()
                    viewModel.refresh()
                }
            }
        }
    }

    val effectiveComuna = remember(selectedRegion, selectedComuna) {
        if (selectedRegion == ChileanRegion.ALL || selectedComuna.isNullOrBlank()) null
        else if (ChileanRegionalDirectory.isValidComunaForRegion(selectedRegion, selectedComuna)) selectedComuna
        else null
    }

    // Comprehensive Chilean Directory (~650 numbers filtered by user region and prioritized by comuna)
    val chileanDirectory = remember(selectedRegion, effectiveComuna) {
        ChileanRegionalDirectory.getEntriesForRegionAndComuna(selectedRegion, effectiveComuna).map { it.toClassifiedContact() }
    }

    // Memoized classified contacts
    val classifiedContacts = remember(rawContacts, chileanDirectory) {
        val list = mutableListOf<ClassifiedContact>()
        // 1. Personal device contacts
        rawContacts.forEach { c ->
            list.add(
                ClassifiedContact(
                    id = "personal_${c.id}",
                    name = c.name.ifBlank { c.phoneNumber },
                    phoneNumber = c.phoneNumber,
                    displayPhone = PhoneNumberUtils.formatDisplayNumber(c.phoneNumber),
                    isFavorite = c.isFavorite,
                    category = ContactCategoryFilter.PERSONAL,
                    avatarType = ContactAvatarType.INITIALS,
                    tag = if (c.isFavorite) "Favorito" else "Personal",
                    isVerified = false,
                    rawContact = c
                )
            )
        }
        // 2. Chilean Telecom directory entries
        list.addAll(chileanDirectory)
        list
    }

    // Category counts for badges
    val categoryCounts = remember(classifiedContacts, selectedGroup, groupMemberIds) {
        val total = if (selectedGroup != null) {
            classifiedContacts.count { c ->
                val personalId = c.rawContact?.id ?: c.id.removePrefix("personal_")
                personalId in groupMemberIds
            }
        } else classifiedContacts.size
        val favs = classifiedContacts.count { it.isFavorite }
        val emerg = classifiedContacts.count { it.category == ContactCategoryFilter.EMERGENCIES }
        val munic = classifiedContacts.count { it.category == ContactCategoryFilter.MUNICIPAL }
        val official = classifiedContacts.count { it.category == ContactCategoryFilter.OFFICIAL_600 }
        val personal = classifiedContacts.count { it.category == ContactCategoryFilter.PERSONAL }
        mapOf(
            ContactCategoryFilter.ALL to total,
            ContactCategoryFilter.FAVORITES to favs,
            ContactCategoryFilter.EMERGENCIES to emerg,
            ContactCategoryFilter.MUNICIPAL to munic,
            ContactCategoryFilter.OFFICIAL_600 to official,
            ContactCategoryFilter.PERSONAL to personal
        )
    }

    // Filtered and grouped contacts
    val (favoriteList, groupedContacts) = remember(
        classifiedContacts,
        currentFilter,
        currentSourceFilter,
        currentSortOrder,
        selectedGroup,
        groupMemberIds,
        searchQuery
    ) {
        val query = searchQuery.trim()
        val filtered = classifiedContacts.filter { c ->
            if (selectedGroup != null) {
                val personalId = c.rawContact?.id ?: c.id.removePrefix("personal_")
                if (personalId !in groupMemberIds) return@filter false
            } else {
                val matchesFilter = when (currentFilter) {
                    ContactCategoryFilter.ALL -> true
                    ContactCategoryFilter.FAVORITES -> c.isFavorite
                    ContactCategoryFilter.EMERGENCIES -> c.category == ContactCategoryFilter.EMERGENCIES
                    ContactCategoryFilter.MUNICIPAL -> c.category == ContactCategoryFilter.MUNICIPAL
                    ContactCategoryFilter.OFFICIAL_600 -> c.category == ContactCategoryFilter.OFFICIAL_600
                    ContactCategoryFilter.PERSONAL -> c.category == ContactCategoryFilter.PERSONAL
                }
                if (!matchesFilter) return@filter false
            }

            // Source filter
            val matchesSource = when (currentSourceFilter) {
                ContactSourceFilter.ALL -> true
                ContactSourceFilter.GOOGLE -> c.rawContact?.source?.equals("GOOGLE", ignoreCase = true) == true
                ContactSourceFilter.WHATSAPP -> c.rawContact?.source?.equals("WHATSAPP", ignoreCase = true) == true
                ContactSourceFilter.SIM -> c.rawContact?.source?.equals("SIM", ignoreCase = true) == true
                ContactSourceFilter.DEVICE -> c.rawContact?.source?.equals("DEVICE", ignoreCase = true) == true || c.rawContact == null
            }
            if (!matchesSource) return@filter false

            // Phone only sort check
            if (currentSortOrder == ContactSortOrder.PHONE_ONLY && c.phoneNumber.isBlank()) {
                return@filter false
            }

            val normQuery = query.normalizeForSearch()
            if (normQuery.isEmpty()) true
            else {
                c.name.normalizeForSearch().contains(normQuery) ||
                    c.phoneNumber.contains(query) ||
                    c.tag.normalizeForSearch().contains(normQuery) ||
                    (c.subtitle?.normalizeForSearch()?.contains(normQuery) == true)
            }
        }

        val sortedList = when (currentSortOrder) {
            ContactSortOrder.NAME_ASC, ContactSortOrder.PHONE_ONLY -> filtered.sortedBy { it.name.lowercase() }
            ContactSortOrder.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
            ContactSortOrder.FAVORITES_FIRST -> filtered.sortedWith(compareByDescending<ClassifiedContact> { it.isFavorite }.thenBy { it.name.lowercase() })
        }

        if (currentSortOrder == ContactSortOrder.NAME_DESC) {
            val grouped = sortedList.groupBy {
                it.name.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
            }.toSortedMap(compareByDescending { it })
            Pair(emptyList(), grouped)
        } else if (currentFilter == ContactCategoryFilter.ALL && query.isEmpty() && selectedGroup == null && currentSortOrder != ContactSortOrder.NAME_DESC) {
            val favs = sortedList.filter { it.isFavorite }
            val nonFavs = sortedList.filter { !it.isFavorite }
            val grouped = nonFavs.groupBy {
                it.name.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
            }.toSortedMap()
            Pair(favs, grouped)
        } else if (currentFilter == ContactCategoryFilter.FAVORITES && selectedGroup == null) {
            Pair(sortedList, emptyMap<String, List<ClassifiedContact>>())
        } else {
            val grouped = sortedList.groupBy {
                it.name.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
            }.toSortedMap()
            Pair(emptyList(), grouped)
        }
    }

    val listState = rememberLazyListState()
    val handedness = LocalHandedness.current
    val alphabet = remember(groupedContacts) { groupedContacts.keys.toList() }

    val precomputedOffsets = remember(favoriteList, groupedContacts) {
        val offsets = mutableMapOf<String, Int>()
        var currentOffset = if (favoriteList.isNotEmpty()) 1 + favoriteList.size else 0
        groupedContacts.keys.forEach { letter ->
            offsets[letter] = currentOffset
            currentOffset += 1 + (groupedContacts[letter]?.size ?: 0)
        }
        offsets
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Category Quick Chips Bar (Stitch Design)
            ContactCategoryChipsRow(
                currentFilter = currentFilter,
                counts = categoryCounts,
                categoryOrder = categoryOrder,
                selectedRegion = selectedRegion,
                selectedComuna = selectedComuna,
                selectedGroup = selectedGroup,
                currentSourceFilter = currentSourceFilter,
                onFilterSelect = {
                    currentFilter = it
                    selectedGroup = null
                },
                onClearGroup = { selectedGroup = null },
                onClearSourceFilter = { currentSourceFilter = ContactSourceFilter.ALL },
                onOpenRegionPicker = { showRegionPicker = true },
                onOpenMoreCategories = { showCategoriesSheet = true }
            )

            // Content List
            if (isLoading) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (favoriteList.isEmpty() && groupedContacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.SearchOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = if (searchQuery.isNotEmpty()) "Sin resultados para \"$searchQuery\"" else "No hay contactos en esta categoría",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Prueba con otra búsqueda o selecciona 'Todos' en las pestañas superiores.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = if (handedness == Handedness.LEFT && alphabet.size >= 5) 36.dp else 16.dp,
                            end = if (handedness == Handedness.RIGHT && alphabet.size >= 5) 36.dp else 16.dp,
                            bottom = 96.dp,
                            top = 6.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Section: FAVORITOS (if any)
                        if (favoriteList.isNotEmpty()) {
                            stickyHeader(key = "header_favoritos") {
                                SectionHeader(
                                    title = "FAVORITOS",
                                    actionText = if (currentFilter == ContactCategoryFilter.ALL) "Ver solo favoritos" else null,
                                    onActionClick = { currentFilter = ContactCategoryFilter.FAVORITES }
                                )
                            }
                            items(favoriteList, key = { it.id }) { contact ->
                                ContactCard(
                                    contact = contact,
                                    onClick = { selectedContactForOptions = contact },
                                    onLongClick = { selectedContactForOptions = contact },
                                    onCallClick = {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phoneNumber}"))
                                        context.startActivity(intent)
                                    },
                                    onTagClick = { tag -> highlightedTagForDictionary = tag }
                                )
                            }
                        }

                        // Alphabetical sections
                        groupedContacts.forEach { (letter, contactsInLetter) ->
                            stickyHeader(key = "header_$letter") {
                                SectionHeader(
                                    title = letter,
                                    actionText = null,
                                    onActionClick = null
                                )
                            }
                            items(contactsInLetter, key = { it.id }) { contact ->
                                ContactCard(
                                    contact = contact,
                                    onClick = { selectedContactForOptions = contact },
                                    onLongClick = { selectedContactForOptions = contact },
                                    onCallClick = {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phoneNumber}"))
                                        context.startActivity(intent)
                                    },
                                    onTagClick = { tag -> highlightedTagForDictionary = tag }
                                )
                            }
                        }
                    }

                    // Fast Scrollbar (only when 5 or more alphabetical sections exist)
                    if (searchQuery.isEmpty() && alphabet.size >= 5) {
                        ContactsFastScrollBar(
                            alphabet = alphabet,
                            precomputedOffsets = precomputedOffsets,
                            listState = listState,
                            coroutineScope = coroutineScope
                        )
                    }
                }
            }
        }

        // Floating Action Button (Add Contact)
        CortaFloatingActionButton(
            onClick = {
                val intent = Intent(Intent.ACTION_INSERT).apply {
                    type = ContactsContract.RawContacts.CONTENT_TYPE
                }
                context.startActivity(intent)
            },
            icon = Icons.Rounded.PersonAdd,
            contentDescription = strings.contacts.addAction,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }

    // Modal: Contact Tag Dictionary
    highlightedTagForDictionary?.let { tag ->
        ContactTagDictionaryBottomSheet(
            highlightedTag = tag,
            onDismiss = { highlightedTagForDictionary = null }
        )
    }

    // Modal: All Categories Explorer & Reordering
    // Modal: All Categories Explorer & Reordering
    if (showCategoriesSheet) {
        ContactCategoriesBottomSheet(
            currentFilter = currentFilter,
            counts = categoryCounts,
            categoryOrder = categoryOrder,
            selectedRegion = selectedRegion,
            selectedComuna = selectedComuna,
            contactGroups = contactGroups,
            selectedGroup = selectedGroup,
            currentSourceFilter = currentSourceFilter,
            sourceCounts = sourceCounts,
            onSelectSourceFilter = { currentSourceFilter = it },
            currentSortOrder = currentSortOrder,
            onSelectSortOrder = {
                currentSortOrder = it
                prefs.edit().putString("contact_sort_order", it.name).apply()
            },
            onSelectFilter = {
                currentFilter = it
                selectedGroup = null
                showCategoriesSheet = false
            },
            onSelectGroup = { group ->
                selectedGroup = group
                showCategoriesSheet = false
            },
            onCreateGroup = { name ->
                coroutineScope.launch(Dispatchers.IO) {
                    cortaRepository.addContactGroup(name)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Grupo '$name' creado", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onReorderCategories = { newOrder ->
                categoryOrder = newOrder
                prefs.edit()
                    .putString("contact_category_order", newOrder.joinToString(",") { it.name })
                    .apply()
            },
            onOpenRegionPicker = {
                showCategoriesSheet = false
                showRegionPicker = true
            },
            onImportSimContacts = {
                showCategoriesSheet = false
                coroutineScope.launch(Dispatchers.IO) {
                    val imported = CortaExportImportHelper.importContactsFromSim(context)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "${imported.size} contactos leídos desde la tarjeta SIM", Toast.LENGTH_SHORT).show()
                        viewModel.refresh()
                    }
                }
            },
            onExportVcf = {
                showCategoriesSheet = false
                CortaExportImportHelper.exportAllContactsToVcf(context, rawContacts)
            },
            onImportVcf = {
                showCategoriesSheet = false
                vcfPickerLauncher.launch(arrayOf("text/x-vcard", "text/vcard", "*/*"))
            },
            onExportCsv = {
                showCategoriesSheet = false
                CortaExportImportHelper.exportContactsToCsv(context, rawContacts)
            },
            onImportCsv = {
                showCategoriesSheet = false
                csvPickerLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "*/*"))
            },
            onDismiss = { showCategoriesSheet = false }
        )
    }

    // Modal: Region & Comuna Selector
    if (showRegionPicker) {
        RegionSelectorBottomSheet(
            selectedRegion = selectedRegion,
            selectedComuna = selectedComuna,
            onSelectLocation = { region, comuna ->
                selectedRegion = region
                selectedComuna = comuna
                val editor = prefs.edit().putString("user_selected_region", region.code)
                if (comuna != null) {
                    editor.putString("user_selected_comuna", comuna)
                } else {
                    editor.remove("user_selected_comuna")
                }
                editor.apply()
                val locText = if (comuna != null) "$comuna (${region.shortName})" else region.displayName
                Toast.makeText(context, "Ubicación: $locText", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showRegionPicker = false }
        )
    }

    // Modal: Contact Details & Actions (Rich Truecaller-style sheet)
    selectedContactForOptions?.let { contact ->
        ContactDetailBottomSheet(
            contact = contact,
            cortaRepository = cortaRepository,
            onDismiss = { selectedContactForOptions = null },
            onToggleFavorite = { id, fav ->
                viewModel.toggleFavorite(id, fav)
            }
        )
    }
}

/**
 * Section header with sticky clean styling matching Stitch design.
 */
@Composable
private fun SectionHeader(
    title: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.width(10.dp))
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                thickness = 0.8.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
            if (actionText != null && onActionClick != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onActionClick() }
                )
            }
        }
    }
}

/**
 * Horizontal Category Chips Row with count badges and pinned '+' exploration button.
 */
@Composable
private fun ContactCategoryChipsRow(
    currentFilter: ContactCategoryFilter,
    counts: Map<ContactCategoryFilter, Int>,
    categoryOrder: List<ContactCategoryFilter>,
    selectedRegion: ChileanRegion,
    selectedComuna: String?,
    selectedGroup: ContactGroup?,
    currentSourceFilter: ContactSourceFilter = ContactSourceFilter.ALL,
    onFilterSelect: (ContactCategoryFilter) -> Unit,
    onClearGroup: () -> Unit,
    onClearSourceFilter: () -> Unit = {},
    onOpenRegionPicker: () -> Unit,
    onOpenMoreCategories: () -> Unit
) {
    val scrollState = rememberScrollState()
    val isSecondaryActive = currentFilter in listOf(
        ContactCategoryFilter.MUNICIPAL,
        ContactCategoryFilter.OFFICIAL_600,
        ContactCategoryFilter.PERSONAL
    ) || selectedGroup != null || currentSourceFilter != ContactSourceFilter.ALL

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Location Chip (Región y/o Comuna seleccionada)
            val locationLabel = when {
                selectedComuna != null -> "$selectedComuna (${selectedRegion.shortName})"
                selectedRegion != ChileanRegion.ALL -> selectedRegion.shortName
                else -> "Chile"
            }
            StitchFilterChip(
                selected = selectedRegion != ChileanRegion.ALL || selectedComuna != null,
                onClick = onOpenRegionPicker,
                label = locationLabel,
                icon = Icons.Rounded.LocationOn,
                iconTint = MaterialTheme.colorScheme.tertiary
            )

            // Active Contact Group Chip (if any selected)
            if (selectedGroup != null) {
                StitchFilterChip(
                    selected = true,
                    onClick = onClearGroup,
                    label = "Grupo: ${selectedGroup.name} ✕",
                    icon = Icons.Rounded.Group,
                    iconTint = MaterialTheme.colorScheme.primary
                )
            }

            // Active Account Source Filter Chip (if not ALL)
            if (currentSourceFilter != ContactSourceFilter.ALL) {
                StitchFilterChip(
                    selected = true,
                    onClick = onClearSourceFilter,
                    label = "${currentSourceFilter.displayName} ✕",
                    icon = Icons.Rounded.AccountCircle,
                    iconTint = MaterialTheme.colorScheme.secondary
                )
            }

            // Dynamic User Category Order
            categoryOrder.forEach { cat ->
                when (cat) {
                    ContactCategoryFilter.PERSONAL -> {
                        StitchFilterChip(
                            selected = currentFilter == ContactCategoryFilter.PERSONAL,
                            onClick = { onFilterSelect(ContactCategoryFilter.PERSONAL) },
                            label = "Personal",
                            icon = Icons.Rounded.Person,
                            count = counts[ContactCategoryFilter.PERSONAL]
                        )
                    }
                    ContactCategoryFilter.FAVORITES -> {
                        StitchFilterChip(
                            selected = currentFilter == ContactCategoryFilter.FAVORITES,
                            onClick = { onFilterSelect(ContactCategoryFilter.FAVORITES) },
                            label = "Favoritos",
                            icon = Icons.Rounded.Star,
                            iconTint = MaterialTheme.colorScheme.primary,
                            count = counts[ContactCategoryFilter.FAVORITES]
                        )
                    }
                    ContactCategoryFilter.EMERGENCIES -> {
                        StitchFilterChip(
                            selected = currentFilter == ContactCategoryFilter.EMERGENCIES,
                            onClick = { onFilterSelect(ContactCategoryFilter.EMERGENCIES) },
                            label = "Emergencias",
                            icon = Icons.Rounded.Shield,
                            iconTint = MaterialTheme.colorScheme.secondary,
                            count = counts[ContactCategoryFilter.EMERGENCIES]
                        )
                    }
                    ContactCategoryFilter.MUNICIPAL -> {
                        StitchFilterChip(
                            selected = currentFilter == ContactCategoryFilter.MUNICIPAL,
                            onClick = { onFilterSelect(ContactCategoryFilter.MUNICIPAL) },
                            label = "Comunas",
                            icon = Icons.Rounded.LocalPolice,
                            iconTint = MaterialTheme.colorScheme.tertiary,
                            count = counts[ContactCategoryFilter.MUNICIPAL]
                        )
                    }
                    ContactCategoryFilter.OFFICIAL_600 -> {
                        StitchFilterChip(
                            selected = currentFilter == ContactCategoryFilter.OFFICIAL_600,
                            onClick = { onFilterSelect(ContactCategoryFilter.OFFICIAL_600) },
                            label = "Oficiales",
                            icon = Icons.Rounded.AccountBalance,
                            iconTint = MaterialTheme.colorScheme.primary,
                            count = counts[ContactCategoryFilter.OFFICIAL_600]
                        )
                    }
                    ContactCategoryFilter.ALL -> {
                        StitchFilterChip(
                            selected = currentFilter == ContactCategoryFilter.ALL,
                            onClick = { onFilterSelect(ContactCategoryFilter.ALL) },
                            label = "Todos",
                            count = counts[ContactCategoryFilter.ALL]
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Pinned '+' Button to open category modal (always visible on screen)
        CortaCategoryExploreButton(
            onClick = onOpenMoreCategories,
            isActive = isSecondaryActive,
            contentDescription = "Explorar categorías"
        )
    }
}

/**
 * Modern Contact Card according to Stitch Native Telecom Clarity specifications.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactCard(
    contact: ClassifiedContact,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCallClick: () -> Unit,
    onTagClick: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Contextual Telecom Avatar (42dp, harmonized across Mensajes, Llamadas, Contactos)
            ContactAvatar(contact = contact)

            Spacer(modifier = Modifier.width(12.dp))

            // Main Info Column (3 structured lines: Line 1 Name+Badges, Line 2 Tag+Number, Line 3 Subtitle)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // Line 1: Name + Star / Verified
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = contact.name,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (contact.isFavorite) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = "Favorito",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (contact.isVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.Verified,
                            contentDescription = "Verificado",
                            tint = if (contact.isEmergency) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Line 2: Tag Badge + Formatted Phone Number (always visible!)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ContactTagBadge(
                        tag = contact.tag,
                        category = contact.category,
                        onClick = { onTagClick(contact.tag) }
                    )

                    if (contact.displayPhone.isNotBlank() && contact.displayPhone != contact.name) {
                        Text(
                            text = contact.displayPhone,
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Line 3: Category Icon + Subtitle / Comuna description
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val categoryIcon = when (contact.category) {
                        ContactCategoryFilter.EMERGENCIES -> Icons.Rounded.Shield
                        ContactCategoryFilter.MUNICIPAL -> Icons.Rounded.LocalPolice
                        ContactCategoryFilter.OFFICIAL_600 -> Icons.Rounded.AccountBalance
                        ContactCategoryFilter.FAVORITES -> Icons.Rounded.Star
                        else -> Icons.Rounded.Person
                    }
                    val categoryIconTint = when (contact.category) {
                        ContactCategoryFilter.EMERGENCIES -> MaterialTheme.colorScheme.secondary
                        ContactCategoryFilter.MUNICIPAL -> MaterialTheme.colorScheme.tertiary
                        ContactCategoryFilter.OFFICIAL_600 -> MaterialTheme.colorScheme.primary
                        ContactCategoryFilter.FAVORITES -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = null,
                        tint = categoryIconTint,
                        modifier = Modifier.size(13.dp)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = contact.subtitle ?: "Contacto guardado",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Standardized 40dp Circular Action Button (Harmonized across CallLog and Contacts)
            val isFav = contact.isFavorite
            Surface(
                onClick = onCallClick,
                shape = CircleShape,
                color = if (isFav) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Call,
                        contentDescription = "Llamar a ${contact.name}",
                        tint = if (isFav) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Contextual Telecom Avatar with badge indicators (42dp harmonized).
 */
@Composable
fun ContactAvatar(
    contact: ClassifiedContact,
    modifier: Modifier = Modifier.size(42.dp)
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when (contact.avatarType) {
            ContactAvatarType.EMERGENCY_SHIELD -> {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            ContactAvatarType.MUNICIPAL_POLICE -> {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.LocalPolice,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            ContactAvatarType.OFFICIAL_BANK -> {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.AccountBalance,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            ContactAvatarType.INITIALS -> {
                val (bgColor, fgColor) = when (kotlin.math.abs(contact.name.hashCode()) % 4) {
                    0 -> Pair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    1 -> Pair(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                    2 -> Pair(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                    else -> Pair(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.primary)
                }
                Surface(
                    shape = CircleShape,
                    color = bgColor,
                    contentColor = fgColor,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = contact.name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }

        // Star badge indicator for favorites
        if (contact.isFavorite && contact.avatarType == ContactAvatarType.INITIALS) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.BottomEnd)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Star,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
    }
}

/**
 * Single-word interactive tag badge with isolated tap detection.
 */
@Composable
private fun ContactTagBadge(
    tag: String,
    category: ContactCategoryFilter,
    onClick: () -> Unit
) {
    val (bg, fg) = when (category) {
        ContactCategoryFilter.EMERGENCIES -> Pair(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        ContactCategoryFilter.MUNICIPAL -> Pair(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        ContactCategoryFilter.OFFICIAL_600 -> Pair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        ContactCategoryFilter.FAVORITES -> Pair(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        else -> Pair(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = bg,
        contentColor = fg
    ) {
        Text(
            text = tag,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

/**
 * Bottom sheet displaying the complete Contact Tag Dictionary.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactTagDictionaryBottomSheet(
    highlightedTag: String,
    onDismiss: () -> Unit
) {
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Diccionario de Contactos",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Corta clasifica cada contacto según la normativa SUBTEL y su nivel de protección.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            data class ContactTagItem(
                val tag: String,
                val desc: String,
                val containerColor: Color,
                val contentColor: Color,
                val accentColor: Color
            )

            val tags = listOf(
                ContactTagItem("Personal", "Contacto guardado en tu libreta telefónica. Corta lo reconoce como seguro y nunca lo bloqueará por spam.", MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.primary),
                ContactTagItem("Favorito", "Contacto prioritario marcado con estrella. Notificaciones garantizadas y botón de marcación rápida destacado.", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primary),
                ContactTagItem("Emergencia", "Servicio nacional de respuesta inmediata (131 Salud, 132 Bomberos, 133 Carabineros, etc.). Marcación prioritaria 100% protegida.", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.secondary),
                ContactTagItem("Municipal", "Red comunal de seguridad ciudadana 14xx (Las Condes, Santiago, Maipú, Providencia, etc.). Inmune por ley.", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, MaterialTheme.colorScheme.tertiary),
                ContactTagItem("Oficial", "Línea de utilidad pública, banco o atención al cliente verificada por SUBTEL (código 600 / 800).", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primary),
                ContactTagItem("Lista Blanca", "Número que autorizaste personalmente en Corta para permitir siempre sus llamadas y SMS.", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.secondary)
            )

            tags.forEach { item ->
                val isHighlighted = item.tag.equals(highlightedTag, ignoreCase = true)
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isHighlighted) item.containerColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    border = if (isHighlighted) BorderStroke(1.5.dp, item.accentColor) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = item.containerColor,
                                contentColor = item.contentColor
                            ) {
                                Text(
                                    text = item.tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            if (isHighlighted) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "• Seleccionada",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = item.accentColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = item.desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bottom sheet to explore, switch, and reorder contact categories with inline Up/Down controls,
 * default tab setting, and contact group management.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactCategoriesBottomSheet(
    currentFilter: ContactCategoryFilter,
    counts: Map<ContactCategoryFilter, Int>,
    categoryOrder: List<ContactCategoryFilter>,
    selectedRegion: ChileanRegion,
    selectedComuna: String?,
    contactGroups: List<ContactGroup>,
    selectedGroup: ContactGroup?,
    currentSourceFilter: ContactSourceFilter = ContactSourceFilter.ALL,
    sourceCounts: Map<ContactSourceFilter, Int> = emptyMap(),
    onSelectSourceFilter: (ContactSourceFilter) -> Unit = {},
    currentSortOrder: ContactSortOrder = ContactSortOrder.NAME_ASC,
    onSelectSortOrder: (ContactSortOrder) -> Unit = {},
    onSelectFilter: (ContactCategoryFilter) -> Unit,
    onSelectGroup: (ContactGroup?) -> Unit,
    onCreateGroup: (String) -> Unit,
    onReorderCategories: (List<ContactCategoryFilter>) -> Unit,
    onOpenRegionPicker: () -> Unit,
    onImportSimContacts: () -> Unit = {},
    onExportVcf: () -> Unit,
    onImportVcf: () -> Unit,
    onExportCsv: () -> Unit = {},
    onImportCsv: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    val context = LocalContext.current

    if (showCreateGroupDialog) {
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            title = { Text("Nuevo Grupo de Contactos", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Crea un grupo personalizado para organizar a tu familia, trabajo o amigos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        placeholder = { Text("Ej. Familia, Trabajo, Amigos...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newGroupName.trim()
                        if (trimmed.isNotEmpty()) {
                            onCreateGroup(trimmed)
                            newGroupName = ""
                            showCreateGroupDialog = false
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Crear Grupo", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) {
                    Text("Cancelar")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Categorías y Grupos",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Usa ↑/↓ para ordenar. La primera categoría será la pestaña inicial.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = {
                        val defaultList = listOf(
                            ContactCategoryFilter.PERSONAL,
                            ContactCategoryFilter.FAVORITES,
                            ContactCategoryFilter.EMERGENCIES,
                            ContactCategoryFilter.MUNICIPAL,
                            ContactCategoryFilter.OFFICIAL_600,
                            ContactCategoryFilter.ALL
                        )
                        onReorderCategories(defaultList)
                    }
                ) {
                    Text("Restablecer", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Categories list with inline reorder
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                categoryOrder.forEachIndexed { index, cat ->
                    val isSelected = cat == currentFilter && selectedGroup == null
                    val isFirst = index == 0
                    val count = counts[cat] ?: 0

                    val catName = when (cat) {
                        ContactCategoryFilter.ALL -> "Todos"
                        ContactCategoryFilter.FAVORITES -> "Favoritos"
                        ContactCategoryFilter.EMERGENCIES -> "Emergencias"
                        ContactCategoryFilter.MUNICIPAL -> "Comunas 14xx"
                        ContactCategoryFilter.OFFICIAL_600 -> "Oficiales 600"
                        ContactCategoryFilter.PERSONAL -> "Personales"
                    }

                    val catDesc = when (cat) {
                        ContactCategoryFilter.PERSONAL -> "Libreta telefónica del dispositivo"
                        ContactCategoryFilter.FAVORITES -> "Contactos con estrella"
                        ContactCategoryFilter.EMERGENCIES -> "Líneas nacionales de emergencia"
                        ContactCategoryFilter.MUNICIPAL -> "Seguridad comunal y patrullaje 14xx"
                        ContactCategoryFilter.OFFICIAL_600 -> "Servicios públicos y bancos"
                        ContactCategoryFilter.ALL -> "Directorio completo y contactos"
                    }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerLowest
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Body (clickable to select)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSelectFilter(cat) }
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "$catName ($count)",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        fontSize = 14.5.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (isFirst) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                                        ) {
                                            Text(
                                                text = "Pestaña inicial",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = catDesc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Reordering buttons (↑ and ↓)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (index > 0) {
                                            val mutable = categoryOrder.toMutableList()
                                            val item = mutable.removeAt(index)
                                            mutable.add(index - 1, item)
                                            onReorderCategories(mutable)
                                        }
                                    },
                                    enabled = index > 0,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowUp,
                                        contentDescription = "Mover arriba",
                                        tint = if (index > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (index < categoryOrder.size - 1) {
                                            val mutable = categoryOrder.toMutableList()
                                            val item = mutable.removeAt(index)
                                            mutable.add(index + 1, item)
                                            onReorderCategories(mutable)
                                        }
                                    },
                                    enabled = index < categoryOrder.size - 1,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = "Mover abajo",
                                        tint = if (index < categoryOrder.size - 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Contact Groups Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mis Grupos de Contactos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = { showCreateGroupDialog = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nuevo Grupo", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            if (contactGroups.isEmpty()) {
                Text(
                    text = "No tienes grupos creados. Toca en '+ Nuevo Grupo' para crear uno.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Option to clear group filter if any
                    if (selectedGroup != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.clickable { onSelectGroup(null) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Quitar filtro de grupo",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    contactGroups.forEach { group ->
                        val isGroupActive = selectedGroup?.id == group.id
                        FilterChip(
                            selected = isGroupActive,
                            onClick = {
                                if (isGroupActive) onSelectGroup(null) else onSelectGroup(group)
                            },
                            label = { Text(group.name, fontWeight = if (isGroupActive) FontWeight.Bold else FontWeight.Medium) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Group,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isGroupActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Origen de los Contactos (Cuentas)
            Text(
                text = "Origen de Contactos (Cuentas)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ContactSourceFilter.entries.forEach { src ->
                    val isSelected = currentSourceFilter == src
                    val count = sourceCounts[src] ?: 0
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectSourceFilter(src) },
                        label = { Text("${src.displayName} ($count)", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Orden de visualización
            Text(
                text = "Orden de Visualización",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ContactSortOrder.entries.forEach { sort ->
                    val isSelected = currentSortOrder == sort
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectSortOrder(sort) },
                        label = { Text(sort.displayName, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Region & Comuna Row
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenRegionPicker() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ubicación activa",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val locLabel = when {
                            selectedComuna != null -> "$selectedComuna (${selectedRegion.displayName})"
                            selectedRegion != ChileanRegion.ALL -> selectedRegion.displayName
                            else -> "Chile (Todas las regiones)"
                        }
                        Text(
                            text = locLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = onOpenRegionPicker) {
                        Text("Cambiar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Bottom Sheet for Contact Options (Dial, SMS, Favorite, Whitelist, Block, Copy).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactOptionsSheet(
    contact: ClassifiedContact,
    onDismiss: () -> Unit,
    cortaRepository: CortaRepository,
    viewModel: ContactsViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val strings = Strings

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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ContactAvatar(contact = contact)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = contact.displayPhone,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))

            // Action: Llamar
            ListItem(
                headlineContent = { Text("Llamar", fontWeight = FontWeight.SemiBold) },
                leadingContent = { Icon(Icons.Rounded.Call, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable {
                    onDismiss()
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phoneNumber}"))
                    context.startActivity(intent)
                }
            )

            // Action: Enviar SMS
            ListItem(
                headlineContent = { Text("Enviar SMS", fontWeight = FontWeight.SemiBold) },
                leadingContent = { Icon(Icons.AutoMirrored.Rounded.Message, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable {
                    onDismiss()
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${contact.phoneNumber}"))
                    context.startActivity(intent)
                }
            )

            // Action: Toggle Favorite (if personal contact)
            if (contact.rawContact != null) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = if (contact.isFavorite) "Quitar de Favoritos" else "Marcar como Favorito",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = if (contact.isFavorite) Icons.Rounded.StarOutline else Icons.Rounded.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.clickable {
                        viewModel.toggleFavorite(contact.rawContact.id, !contact.isFavorite)
                        onDismiss()
                    }
                )
            }

            // Action: Whitelist
            ListItem(
                headlineContent = { Text("Agregar a Lista Blanca (Permitir siempre)", fontWeight = FontWeight.SemiBold) },
                leadingContent = { Icon(Icons.Rounded.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable {
                    onDismiss()
                    coroutineScope.launch(Dispatchers.IO) {
                        cortaRepository.addRule(contact.phoneNumber, FilterAction.ALLOW, false, contact.name)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "${contact.name} agregado a lista blanca", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )

            // Action: Block
            ListItem(
                headlineContent = {
                    Text(
                        text = "Bloquear número",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                },
                leadingContent = { Icon(Icons.Rounded.Block, null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable {
                    onDismiss()
                    coroutineScope.launch(Dispatchers.IO) {
                        cortaRepository.addRule(contact.phoneNumber, FilterAction.BLOCK, false, contact.name)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Número ${contact.phoneNumber} bloqueado", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )

            // Action: Edit (if personal contact)
            if (contact.rawContact != null) {
                ListItem(
                    headlineContent = { Text("Editar contacto en teléfono", fontWeight = FontWeight.SemiBold) },
                    leadingContent = { Icon(Icons.Rounded.Edit, null) },
                    modifier = Modifier.clickable {
                        onDismiss()
                        val intent = Intent(Intent.ACTION_EDIT).apply {
                            data = android.content.ContentUris.withAppendedId(
                                ContactsContract.Contacts.CONTENT_URI,
                                contact.rawContact.id.toLongOrNull() ?: 0L
                            )
                        }
                        context.startActivity(intent)
                    }
                )
            }

            // Action: Copy
            ListItem(
                headlineContent = { Text("Copiar número", fontWeight = FontWeight.SemiBold) },
                leadingContent = { Icon(Icons.Rounded.ContentCopy, null) },
                modifier = Modifier.clickable {
                    onDismiss()
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(ClipData.newPlainText("Teléfono Corta", contact.phoneNumber))
                    Toast.makeText(context, "Número copiado al portapapeles", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

/**
 * Vertical alphabet fast scrollbar with touch dragging and preview bubble.
 */
@Composable
private fun BoxScope.ContactsFastScrollBar(
    alphabet: List<String>,
    precomputedOffsets: Map<String, Int>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    val handedness = LocalHandedness.current
    var activeIndex by remember { mutableStateOf<Int?>(null) }
    var barHeightPx by remember { mutableStateOf(0f) }

    val firstVisible by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val scrollActiveLetter by remember(firstVisible, precomputedOffsets) {
        derivedStateOf {
            precomputedOffsets.entries.lastOrNull { firstVisible >= it.value }?.key
        }
    }

    val activeLetter = activeIndex?.let { alphabet.getOrNull(it) }
    val bubbleVisible = activeIndex != null

    val bubbleAlpha by animateFloatAsState(
        targetValue = if (bubbleVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "bubbleAlpha"
    )
    val bubbleScale by animateFloatAsState(
        targetValue = if (bubbleVisible) 1f else 0.7f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "bubbleScale"
    )

    if (bubbleAlpha > 0f && activeLetter != null) {
        Box(
            modifier = Modifier
                .align(if (handedness == Handedness.LEFT) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(
                    start = if (handedness == Handedness.LEFT) 48.dp else 0.dp,
                    end = if (handedness == Handedness.RIGHT) 48.dp else 0.dp
                )
                .graphicsLayer(
                    alpha = bubbleAlpha,
                    scaleX = bubbleScale,
                    scaleY = bubbleScale
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 8.dp,
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = activeLetter,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .align(if (handedness == Handedness.LEFT) Alignment.CenterStart else Alignment.CenterEnd)
            .padding(
                top = 8.dp,
                bottom = 96.dp,
                start = if (handedness == Handedness.LEFT) 4.dp else 0.dp,
                end = if (handedness == Handedness.RIGHT) 4.dp else 0.dp
            )
            .width(26.dp)
            .onGloballyPositioned { barHeightPx = it.size.height.toFloat() }
            .pointerInput(alphabet) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        val index = (offset.y / barHeightPx * alphabet.size).toInt().coerceIn(0, alphabet.size - 1)
                        activeIndex = index
                        coroutineScope.launch {
                            listState.scrollToItem(precomputedOffsets[alphabet[index]] ?: 0)
                        }
                    },
                    onDragEnd = { activeIndex = null },
                    onDragCancel = { activeIndex = null },
                    onVerticalDrag = { change, _ ->
                        val index = (change.position.y / barHeightPx * alphabet.size).toInt().coerceIn(0, alphabet.size - 1)
                        if (index != activeIndex) {
                            activeIndex = index
                            coroutineScope.launch {
                                listState.scrollToItem(precomputedOffsets[alphabet[index]] ?: 0)
                            }
                        }
                    }
                )
            }
            .pointerInput(alphabet) {
                detectTapGestures { offset ->
                    val index = (offset.y / barHeightPx * alphabet.size).toInt().coerceIn(0, alphabet.size - 1)
                    coroutineScope.launch {
                        listState.scrollToItem(precomputedOffsets[alphabet[index]] ?: 0)
                    }
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        alphabet.forEachIndexed { index, letter ->
            val isSelected = activeIndex == index || scrollActiveLetter == letter
            Text(
                text = letter,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = if (isSelected) 12.sp else 9.sp,
                modifier = Modifier.padding(vertical = 1.dp)
            )
        }
    }
}

/**
 * Extension to convert a ChileanDirectoryEntry from ChileanRegionalDirectory to a ClassifiedContact.
 */
fun ChileanDirectoryEntry.toClassifiedContact(): ClassifiedContact {
    val categoryFilter = when (this.category) {
        DirectoryCategory.EMERGENCY -> ContactCategoryFilter.EMERGENCIES
        DirectoryCategory.MUNICIPAL -> ContactCategoryFilter.MUNICIPAL
        DirectoryCategory.OFFICIAL_600, DirectoryCategory.UTILITIES -> ContactCategoryFilter.OFFICIAL_600
        DirectoryCategory.HEALTH_HOSPITAL, DirectoryCategory.POLICE_PDI, DirectoryCategory.FIRE_DEPARTMENT -> ContactCategoryFilter.EMERGENCIES
    }
    val avatarType = when (this.category) {
        DirectoryCategory.EMERGENCY, DirectoryCategory.FIRE_DEPARTMENT -> ContactAvatarType.EMERGENCY_SHIELD
        DirectoryCategory.MUNICIPAL, DirectoryCategory.POLICE_PDI -> ContactAvatarType.MUNICIPAL_POLICE
        DirectoryCategory.HEALTH_HOSPITAL -> ContactAvatarType.EMERGENCY_SHIELD
        DirectoryCategory.OFFICIAL_600, DirectoryCategory.UTILITIES -> ContactAvatarType.OFFICIAL_BANK
    }
    val tagText = when (this.category) {
        DirectoryCategory.EMERGENCY -> "Emergencia"
        DirectoryCategory.MUNICIPAL -> "Municipal"
        DirectoryCategory.HEALTH_HOSPITAL -> "Hospital"
        DirectoryCategory.POLICE_PDI -> "Policial"
        DirectoryCategory.FIRE_DEPARTMENT -> "Bomberos"
        DirectoryCategory.UTILITIES -> "Servicio"
        DirectoryCategory.OFFICIAL_600 -> "Oficial"
    }

    return ClassifiedContact(
        id = this.id,
        name = this.name,
        phoneNumber = this.number,
        displayPhone = PhoneNumberUtils.formatDisplayNumber(this.number),
        isFavorite = this.isFavorite,
        category = categoryFilter,
        avatarType = avatarType,
        tag = tagText,
        isVerified = this.isVerified,
        isEmergency = this.category in listOf(
            DirectoryCategory.EMERGENCY,
            DirectoryCategory.HEALTH_HOSPITAL,
            DirectoryCategory.FIRE_DEPARTMENT,
            DirectoryCategory.POLICE_PDI
        ),
        isMunicipal = this.category == DirectoryCategory.MUNICIPAL,
        subtitle = this.subtitle,
        rawContact = ContactInfo(
            id = this.id,
            name = this.name,
            phoneNumber = this.number,
            isFavorite = this.isFavorite,
            source = "CHILEAN_DIRECTORY"
        )
    )
}

/**
 * Bottom Sheet allowing user to choose their Chilean region and comuna to personalize telecom and emergency numbers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionSelectorBottomSheet(
    selectedRegion: ChileanRegion,
    selectedComuna: String?,
    onSelectLocation: (ChileanRegion, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var expandedRegionCode by remember { mutableStateOf<String?>(selectedRegion.code) }
    val allComunasWithRegion = remember { ChileanRegionalDirectory.getAllComunasWithRegion() }

    val filteredResults = remember(searchQuery) {
        val q = searchQuery.normalizeForSearch()
        if (q.isEmpty()) emptyList()
        else {
            allComunasWithRegion.filter { (comuna, region) ->
                comuna.normalizeForSearch().contains(q) ||
                    region.displayName.normalizeForSearch().contains(q) ||
                    region.shortName.normalizeForSearch().contains(q)
            }
        }
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
                .imePadding()
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "Selecciona tu Ubicación",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Prioriza comisarías, SAMU y seguridad municipal de tu comuna",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar comuna o región (ej. Maipú, Viña...)") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Buscar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Clear, "Limpiar")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (searchQuery.isNotBlank()) {
                    if (filteredResults.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No se encontraron comunas para \"$searchQuery\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(filteredResults, key = { "${it.second.code}_${it.first}" }) { (comuna, region) ->
                            val isSelected = selectedComuna.equals(comuna, ignoreCase = true) && selectedRegion == region
                            ListItem(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onSelectLocation(region, comuna)
                                        onDismiss()
                                    },
                                headlineContent = {
                                    Text(
                                        text = comuna,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = region.displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Rounded.Place,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingContent = {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = "Seleccionada",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent
                                )
                            )
                        }
                    }
                } else {
                    // Option: Chile completo (sin filtrar por comuna)
                    item {
                        val isAllSelected = selectedRegion == ChileanRegion.ALL && selectedComuna == null
                        ListItem(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onSelectLocation(ChileanRegion.ALL, null)
                                    onDismiss()
                                },
                            headlineContent = {
                                Text(
                                    text = "Chile (Todas las regiones y comunas)",
                                    fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = "Directorio nacional completo (~650 números de todo Chile)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Rounded.Public,
                                    contentDescription = null,
                                    tint = if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingContent = {
                                if (isAllSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Seleccionada",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if (isAllSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent
                            )
                        )
                    }

                    // Regions with expandable comunas list
                    items(ChileanRegion.entries.filter { it != ChileanRegion.ALL }, key = { it.code }) { region ->
                        val isRegionActive = selectedRegion == region
                        val isExpanded = expandedRegionCode == region.code
                        val comunasForRegion = remember(region) { ChileanRegionalDirectory.getComunasForRegion(region) }

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isRegionActive && selectedComuna == null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerLowest
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isRegionActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Column {
                                ListItem(
                                    modifier = Modifier.clickable {
                                        expandedRegionCode = if (isExpanded) null else region.code
                                    },
                                    headlineContent = {
                                        Text(
                                            text = region.displayName,
                                            fontWeight = if (isRegionActive) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isRegionActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    supportingContent = {
                                        val hasValidComuna = isRegionActive && selectedComuna != null && ChileanRegionalDirectory.isValidComunaForRegion(region, selectedComuna)
                                        Text(
                                            text = if (hasValidComuna) "Comuna activa: $selectedComuna" else "${comunasForRegion.size} comunas",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (hasValidComuna) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    leadingContent = {
                                        Icon(
                                            imageVector = Icons.Rounded.Place,
                                            contentDescription = null,
                                            tint = if (isRegionActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    trailingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            TextButton(onClick = {
                                                onSelectLocation(region, null)
                                                onDismiss()
                                            }) {
                                                Text("Toda la región", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                                contentDescription = if (isExpanded) "Colapsar" else "Expandir",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )

                                if (isExpanded) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                        modifier = Modifier.padding(horizontal = 14.dp)
                                    )
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = "Selecciona tu comuna:",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            // Chip for "Toda la región"
                                            FilterChip(
                                                selected = isRegionActive && selectedComuna == null,
                                                onClick = {
                                                    onSelectLocation(region, null)
                                                    onDismiss()
                                                },
                                                label = { Text("Toda la región") },
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            comunasForRegion.forEach { com ->
                                                val isComunaSelected = isRegionActive && selectedComuna.equals(com, ignoreCase = true)
                                                FilterChip(
                                                    selected = isComunaSelected,
                                                    onClick = {
                                                        onSelectLocation(region, com)
                                                        onDismiss()
                                                    },
                                                    label = { Text(com, fontWeight = if (isComunaSelected) FontWeight.Bold else FontWeight.Normal) },
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun String.normalizeForSearch(): String {
    val temp = java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
    return "\\p{InCombiningDiacriticalMarks}+".toRegex().replace(temp, "")
        .replace("ñ", "n", ignoreCase = true)
        .lowercase()
        .trim()
}
