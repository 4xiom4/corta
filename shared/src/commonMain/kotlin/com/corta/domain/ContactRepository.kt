package com.corta.app.ui.contacts

interface ContactRepository {
    suspend fun getContacts(): List<ContactInfo>
    suspend fun toggleFavorite(contactId: String, isFavorite: Boolean): Boolean
}
