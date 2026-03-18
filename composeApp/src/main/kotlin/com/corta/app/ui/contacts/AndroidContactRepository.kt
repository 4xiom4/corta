package com.corta.app.ui.contacts

import android.content.Context
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidContactRepository(private val context: Context) : ContactRepository {
    override suspend fun getContacts(): List<ContactInfo> = withContext(Dispatchers.IO) {
        val contactsById = linkedMapOf<String, ContactInfo>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ContactsContract.CommonDataKinds.Phone.STARRED
        )
        
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )
        
        cursor?.use {
            val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoUriIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
            val starredIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)
            
            while (it.moveToNext()) {
                val id = it.getString(idIndex)
                val name = it.getString(nameIndex) ?: ""
                val number = it.getString(numberIndex) ?: ""
                val photoUri = it.getString(photoUriIndex)
                val isFavorite = if (starredIndex != -1) it.getInt(starredIndex) == 1 else false

                val existing = contactsById[id]
                if (existing == null) {
                    contactsById[id] = ContactInfo(
                        id = id,
                        name = name,
                        phoneNumber = number,
                        phoneNumbers = listOf(number).filter { value -> value.isNotBlank() },
                        photoUri = photoUri,
                        isFavorite = isFavorite
                    )
                } else {
                    val updatedNumbers = if (number.isNotBlank() && !existing.phoneNumbers.contains(number)) {
                        existing.phoneNumbers + number
                    } else {
                        existing.phoneNumbers
                    }
                    contactsById[id] = existing.copy(
                        phoneNumber = updatedNumbers.firstOrNull() ?: existing.phoneNumber,
                        phoneNumbers = updatedNumbers,
                        isFavorite = existing.isFavorite || isFavorite
                    )
                }
            }
        }

        contactsById.values
            .asSequence()
            .filter { it.name.isNotBlank() || it.phoneNumber.isNotBlank() }
            .sortedBy { it.name.lowercase() }
            .toList()
    }

    override suspend fun toggleFavorite(contactId: String, isFavorite: Boolean): Boolean = withContext(Dispatchers.IO) {
        val values = android.content.ContentValues().apply {
            put(ContactsContract.Contacts.STARRED, if (isFavorite) 1 else 0)
        }
        val uri = android.net.Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId)
        val rowsUpdated = context.contentResolver.update(uri, values, null, null)
        rowsUpdated > 0
    }
}
