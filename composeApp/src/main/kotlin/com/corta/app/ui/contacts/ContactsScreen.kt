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
import com.corta.app.ui.designsystem.CortaCategoryExploreButton
import com.corta.app.ui.designsystem.CortaFloatingActionButton
import com.corta.app.ui.designsystem.StitchFilterChip
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
import org.koin.compose.koinInject

enum class ContactCategoryFilter {
    ALL,
    FAVORITES,
    EMERGENCIES,
    MUNICIPAL,
    OFFICIAL_600,
    PERSONAL
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
    val initialContactFilter = remember {
        val savedName = prefs.getString("default_contact_tab", ContactCategoryFilter.PERSONAL.name)
        ContactCategoryFilter.entries.firstOrNull { it.name == savedName } ?: ContactCategoryFilter.PERSONAL
    }
    var currentFilter by remember { mutableStateOf(initialContactFilter) }
    var highlightedTagForDictionary by remember { mutableStateOf<String?>(null) }
    var showCategoriesSheet by remember { mutableStateOf(false) }
    var selectedContactForOptions by remember { mutableStateOf<ClassifiedContact?>(null) }

    // Pre-seeded Chilean Directory
    val chileanDirectory = remember {
        buildChileanDirectory()
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
    val categoryCounts = remember(classifiedContacts) {
        val total = classifiedContacts.size
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
    val (favoriteList, groupedContacts) = remember(classifiedContacts, currentFilter, searchQuery) {
        val query = searchQuery.trim()
        val filtered = classifiedContacts.filter { c ->
            val matchesFilter = when (currentFilter) {
                ContactCategoryFilter.ALL -> true
                ContactCategoryFilter.FAVORITES -> c.isFavorite
                ContactCategoryFilter.EMERGENCIES -> c.category == ContactCategoryFilter.EMERGENCIES
                ContactCategoryFilter.MUNICIPAL -> c.category == ContactCategoryFilter.MUNICIPAL
                ContactCategoryFilter.OFFICIAL_600 -> c.category == ContactCategoryFilter.OFFICIAL_600
                ContactCategoryFilter.PERSONAL -> c.category == ContactCategoryFilter.PERSONAL
            }
            if (!matchesFilter) return@filter false

            if (query.isEmpty()) true
            else {
                c.name.contains(query, ignoreCase = true) ||
                    c.phoneNumber.contains(query) ||
                    c.tag.contains(query, ignoreCase = true) ||
                    (c.subtitle?.contains(query, ignoreCase = true) == true)
            }
        }

        if (currentFilter == ContactCategoryFilter.ALL && query.isEmpty()) {
            val favs = filtered.filter { it.isFavorite }
            val nonFavs = filtered.filter { !it.isFavorite }
            val grouped = nonFavs.groupBy {
                it.name.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
            }.toSortedMap()
            Pair(favs, grouped)
        } else if (currentFilter == ContactCategoryFilter.FAVORITES) {
            Pair(filtered, emptyMap<String, List<ClassifiedContact>>())
        } else {
            val grouped = filtered.groupBy {
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
                onFilterSelect = { currentFilter = it },
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
                                    onClick = {
                                        if (contact.rawContact != null) {
                                            selectedContactForOptions = contact
                                        } else {
                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phoneNumber}"))
                                            context.startActivity(intent)
                                        }
                                    },
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
                                    onClick = {
                                        if (contact.rawContact != null) {
                                            selectedContactForOptions = contact
                                        } else {
                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phoneNumber}"))
                                            context.startActivity(intent)
                                        }
                                    },
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

    // Modal: All Categories Explorer
    if (showCategoriesSheet) {
        ContactCategoriesBottomSheet(
            currentFilter = currentFilter,
            counts = categoryCounts,
            onSelectFilter = {
                currentFilter = it
                showCategoriesSheet = false
            },
            onDismiss = { showCategoriesSheet = false }
        )
    }

    // Modal: Contact Options & Actions
    selectedContactForOptions?.let { contact ->
        ContactOptionsSheet(
            contact = contact,
            onDismiss = { selectedContactForOptions = null },
            cortaRepository = cortaRepository,
            viewModel = viewModel
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
    onFilterSelect: (ContactCategoryFilter) -> Unit,
    onOpenMoreCategories: () -> Unit
) {
    val scrollState = rememberScrollState()
    val isSecondaryActive = currentFilter in listOf(
        ContactCategoryFilter.MUNICIPAL,
        ContactCategoryFilter.OFFICIAL_600,
        ContactCategoryFilter.PERSONAL
    )

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
            // Chip 1: Personales (Default)
            StitchFilterChip(
                selected = currentFilter == ContactCategoryFilter.PERSONAL,
                onClick = { onFilterSelect(ContactCategoryFilter.PERSONAL) },
                label = "Personal",
                icon = Icons.Rounded.Person,
                count = counts[ContactCategoryFilter.PERSONAL]
            )

            // Chip 2: Favoritos
            StitchFilterChip(
                selected = currentFilter == ContactCategoryFilter.FAVORITES,
                onClick = { onFilterSelect(ContactCategoryFilter.FAVORITES) },
                label = "Favoritos",
                icon = Icons.Rounded.Star,
                iconTint = MaterialTheme.colorScheme.primary,
                count = counts[ContactCategoryFilter.FAVORITES]
            )

            // Chip 3: Emergencias
            StitchFilterChip(
                selected = currentFilter == ContactCategoryFilter.EMERGENCIES,
                onClick = { onFilterSelect(ContactCategoryFilter.EMERGENCIES) },
                label = "Emergencias",
                icon = Icons.Rounded.Shield,
                iconTint = MaterialTheme.colorScheme.secondary,
                count = counts[ContactCategoryFilter.EMERGENCIES]
            )

            // Chip 4: Comunas 14xx
            StitchFilterChip(
                selected = currentFilter == ContactCategoryFilter.MUNICIPAL,
                onClick = { onFilterSelect(ContactCategoryFilter.MUNICIPAL) },
                label = "Comunas",
                icon = Icons.Rounded.LocalPolice,
                iconTint = MaterialTheme.colorScheme.tertiary,
                count = counts[ContactCategoryFilter.MUNICIPAL]
            )

            // Chip 5: Oficiales 600
            StitchFilterChip(
                selected = currentFilter == ContactCategoryFilter.OFFICIAL_600,
                onClick = { onFilterSelect(ContactCategoryFilter.OFFICIAL_600) },
                label = "Oficiales",
                icon = Icons.Rounded.AccountBalance,
                iconTint = MaterialTheme.colorScheme.primary,
                count = counts[ContactCategoryFilter.OFFICIAL_600]
            )

            // Chip 6: Todos
            StitchFilterChip(
                selected = currentFilter == ContactCategoryFilter.ALL,
                onClick = { onFilterSelect(ContactCategoryFilter.ALL) },
                label = "Todos",
                count = counts[ContactCategoryFilter.ALL]
            )
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
        shape = RoundedCornerShape(14.dp),
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

            // Main Info Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // Row 1: Name + Star/Verified + Tag Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = contact.name,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (contact.isFavorite) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = "Favorito",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        if (contact.isVerified) {
                            Icon(
                                imageVector = Icons.Rounded.Verified,
                                contentDescription = "Verificado",
                                tint = if (contact.isEmergency) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // Single-Word Interactive Tag Badge right next to name
                        ContactTagBadge(
                            tag = contact.tag,
                            category = contact.category,
                            onClick = { onTagClick(contact.tag) }
                        )
                    }
                }

                // Row 2: Category Icon + Subtitle / Formatted Phone Number
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
                        text = contact.subtitle ?: contact.displayPhone,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Quick Call Action Button (44dp target)
            val isFav = contact.isFavorite
            IconButton(
                onClick = onCallClick,
                modifier = Modifier.size(44.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isFav) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (isFav) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Call,
                            contentDescription = "Llamar a ${contact.name}",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Contextual Telecom Avatar with badge indicators (42dp harmonized).
 */
@Composable
private fun ContactAvatar(
    contact: ClassifiedContact
) {
    Box(
        modifier = Modifier.size(42.dp),
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
 * Bottom sheet to explore and switch all contact categories with live counts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactCategoriesBottomSheet(
    currentFilter: ContactCategoryFilter,
    counts: Map<ContactCategoryFilter, Int>,
    onSelectFilter: (ContactCategoryFilter) -> Unit,
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
            Text(
                text = "Categorías de Contactos",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Explora el directorio chileno y tus contactos clasificados.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            val options = listOf(
                Pair(ContactCategoryFilter.PERSONAL, "Libreta telefónica del dispositivo"),
                Pair(ContactCategoryFilter.FAVORITES, "Contactos prioritarios guardados con estrella"),
                Pair(ContactCategoryFilter.EMERGENCIES, "Líneas nacionales de emergencia (13x, 4141)"),
                Pair(ContactCategoryFilter.MUNICIPAL, "Seguridad Comunal y Patrullaje vecinal 14xx"),
                Pair(ContactCategoryFilter.OFFICIAL_600, "Servicios y Utilidad Pública verificados 600 / 800"),
                Pair(ContactCategoryFilter.ALL, "Todos los contactos y servicios del directorio")
            )

            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("corta_prefs", Context.MODE_PRIVATE) }
            val defaultContactTabName = prefs.getString("default_contact_tab", ContactCategoryFilter.PERSONAL.name)

            options.forEach { (cat, desc) ->
                val isSelected = cat == currentFilter
                val isDefault = cat.name == defaultContactTabName
                val count = counts[cat] ?: 0

                ListItem(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectFilter(cat) },
                    headlineContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = when (cat) {
                                    ContactCategoryFilter.ALL -> "Todos ($count)"
                                    ContactCategoryFilter.FAVORITES -> "Favoritos ($count)"
                                    ContactCategoryFilter.EMERGENCIES -> "Emergencias ($count)"
                                    ContactCategoryFilter.MUNICIPAL -> "Comunas 14xx ($count)"
                                    ContactCategoryFilter.OFFICIAL_600 -> "Oficiales 600 ($count)"
                                    ContactCategoryFilter.PERSONAL -> "Personales ($count)"
                                },
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            if (isDefault) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = "Inicial",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    },
                    supportingContent = {
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingContent = {
                        val icon = when (cat) {
                            ContactCategoryFilter.ALL -> Icons.Rounded.Contacts
                            ContactCategoryFilter.FAVORITES -> Icons.Rounded.Star
                            ContactCategoryFilter.EMERGENCIES -> Icons.Rounded.Shield
                            ContactCategoryFilter.MUNICIPAL -> Icons.Rounded.LocalPolice
                            ContactCategoryFilter.OFFICIAL_600 -> Icons.Rounded.AccountBalance
                            ContactCategoryFilter.PERSONAL -> Icons.Rounded.Person
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        if (isSelected) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    prefs.edit().putString("default_contact_tab", currentFilter.name).apply()
                    Toast.makeText(context, "Categoría inicial de Contactos guardada", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PushPin,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Fijar categoría actual como inicial al abrir Contactos",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
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
 * Builds the official Chilean telecom directory entries pre-seeded locally.
 */
private fun buildChileanDirectory(): List<ClassifiedContact> {
    val list = mutableListOf<ClassifiedContact>()

    // National Emergencies
    val emergencies = listOf(
        Pair("131", "SAMU Ambulancia"),
        Pair("132", "Bomberos de Chile"),
        Pair("133", "Carabineros de Chile"),
        Pair("134", "PDI Investigaciones"),
        Pair("130", "Conaf Incendios Forestales"),
        Pair("137", "Rescate Marítimo Armada"),
        Pair("147", "Fono Niños Protegidos"),
        Pair("149", "Fono Familia Carabineros"),
        Pair("4141", "Prevención del Suicidio (*4141)"),
        Pair("1514", "Fono Ayuda Mujer")
    )
    emergencies.forEach { (num, name) ->
        list.add(
            ClassifiedContact(
                id = "dir_emerg_$num",
                name = name,
                phoneNumber = num,
                displayPhone = num,
                isFavorite = num in listOf("133", "131", "132"), // Key 3 starred by default
                category = ContactCategoryFilter.EMERGENCIES,
                avatarType = ContactAvatarType.EMERGENCY_SHIELD,
                tag = "Emergencia",
                isVerified = true,
                isEmergency = true,
                subtitle = "$num · Marcación prioritaria protegida"
            )
        )
    }

    // Municipal Security 14xx (from classifier)
    val municipalEntries = ChileanTelecomClassifier.getAllEmergencyAndMunicipalEntries()
        .filter { it.first.startsWith("14") && it.first.length == 4 && it.first !in listOf("147", "149") }
    municipalEntries.forEach { (num, name) ->
        list.add(
            ClassifiedContact(
                id = "dir_muni_$num",
                name = name,
                phoneNumber = num,
                displayPhone = num,
                isFavorite = false,
                category = ContactCategoryFilter.MUNICIPAL,
                avatarType = ContactAvatarType.MUNICIPAL_POLICE,
                tag = "Municipal",
                isVerified = true,
                isMunicipal = true,
                subtitle = "$num · Patrullaje vecinal protegido"
            )
        )
    }

    // Verified Public Services 600 / 800
    val officialEntries = listOf(
        Triple("6003607777", "Salud Responde (MINSAL)", "+56 600 360 7777 · Verificado SUBTEL 600"),
        Triple("6003702000", "Registro Civil e Identificación", "+56 600 370 2000 · Verificado SUBTEL 600"),
        Triple("6006003000", "BancoEstado 600", "+56 600 600 3000 · Verificado SUBTEL 600"),
        Triple("800220800", "FONASA Fonoinformativo", "+56 800 220 800 · Verificado SUBTEL 800"),
        Triple("6005946000", "SERNAC Atención Ciudadana", "+56 600 594 6000 · Verificado SUBTEL 600"),
        Triple("800800800", "Chilexpress", "+56 800 800 800 · Verificado SUBTEL")
    )
    officialEntries.forEach { (num, name, sub) ->
        list.add(
            ClassifiedContact(
                id = "dir_off_$num",
                name = name,
                phoneNumber = num,
                displayPhone = PhoneNumberUtils.formatDisplayNumber(num),
                isFavorite = false,
                category = ContactCategoryFilter.OFFICIAL_600,
                avatarType = ContactAvatarType.OFFICIAL_BANK,
                tag = "Oficial",
                isVerified = true,
                subtitle = sub
            )
        )
    }

    return list
}
