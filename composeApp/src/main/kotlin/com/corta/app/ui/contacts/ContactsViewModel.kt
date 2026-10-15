package com.corta.app.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corta.domain.ContactInfo
import com.corta.domain.ContactRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

class ContactsViewModel(
    private val repository: ContactRepository
) : ViewModel() {

    private val _contacts = MutableStateFlow<List<ContactInfo>>(emptyList())
    val contacts: StateFlow<List<ContactInfo>> = _contacts.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showFavoritesAtTop = MutableStateFlow(true)
    val showFavoritesAtTop: StateFlow<Boolean> = _showFavoritesAtTop.asStateFlow()

    val filteredContacts: StateFlow<List<ContactInfo>> = combine(_contacts, _searchQuery) { contacts, query ->
        if (query.isBlank()) contacts
        else contacts.filter { it.name.contains(query, ignoreCase = true) || it.phoneNumber.contains(query) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupedContacts: StateFlow<Map<String, List<ContactInfo>>> = combine(
        _contacts,
        _searchQuery,
        _showFavoritesAtTop
    ) { contacts, query, showFavs ->
        val filtered = if (query.isBlank()) contacts
        else contacts.filter { it.name.contains(query, ignoreCase = true) || it.phoneNumber.contains(query) }

        if (showFavs) {
            val favs = filtered.filter { it.isFavorite }
            val others = filtered.filter { !it.isFavorite }

            val res = mutableMapOf<String, List<ContactInfo>>()
            if (favs.isNotEmpty()) res["★ Favoritos"] = favs

            val othersGrouped = others.groupBy {
                it.name.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
            }.toSortedMap()
            res.putAll(othersGrouped)
            res
        } else {
            filtered.groupBy {
                it.name.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
            }.toSortedMap()
        }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadContacts()
    }

    fun loadContacts() {
        if (_isLoading.value) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                _contacts.value = repository.getContacts()
            } catch (e: Exception) {
                Timber.tag("Corta.ContactsVM").e(e, "Error loading contacts")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refresh() {
        repository.invalidateCache()
        loadContacts()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setShowFavoritesAtTop(show: Boolean) {
        _showFavoritesAtTop.value = show
    }

    fun toggleFavorite(contactId: String, isFavorite: Boolean) {
        viewModelScope.launch {
            try {
                repository.toggleFavorite(contactId, isFavorite)
                _contacts.value = repository.getContacts()
            } catch (e: Exception) {
                Timber.tag("Corta.ContactsVM").e(e, "Error toggling favorite")
            }
        }
    }
}
