package com.corta.domain

data class ContactInfo(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val phoneNumbers: List<String> = emptyList(),
    val photoUri: String? = null,
    val isFavorite: Boolean = false
)
