package com.corta.app.ui.contacts

import androidx.compose.runtime.Immutable

@Immutable
data class ContactInfo(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val phoneNumbers: List<String> = emptyList(),
    val photoUri: String? = null,
    val isFavorite: Boolean = false
)
