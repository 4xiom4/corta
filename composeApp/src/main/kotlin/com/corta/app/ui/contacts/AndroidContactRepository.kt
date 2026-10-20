package com.corta.app.ui.contacts

import android.content.Context
import android.provider.ContactsContract
import com.corta.domain.ContactInfo
import com.corta.domain.ContactRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidContactRepository(private val context: Context) : ContactRepository {
    private var cachedContacts: List<ContactInfo>? = null
    private var lastLoadedAt: Long = 0L
    private val cacheTtlMs = 5 * 60 * 1000L // 5 minutos para reducir lag por recarga

    override suspend fun getContacts(): List<ContactInfo> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cachedContacts?.let { cached ->
            if ((now - lastLoadedAt) < cacheTtlMs && cached.isNotEmpty()) return@withContext cached
        }

        val contactsById = linkedMapOf<String, ContactInfo>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ContactsContract.CommonDataKinds.Phone.STARRED,
            ContactsContract.RawContacts.ACCOUNT_TYPE,
            ContactsContract.RawContacts.ACCOUNT_NAME
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
            val accTypeIndex = it.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)
            val accNameIndex = it.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME)
            
            while (it.moveToNext()) {
                val id = it.getString(idIndex)
                val name = it.getString(nameIndex) ?: ""
                val number = it.getString(numberIndex) ?: ""
                val photoUri = it.getString(photoUriIndex)
                val isFavorite = if (starredIndex != -1) it.getInt(starredIndex) == 1 else false
                val rawAccType = if (accTypeIndex != -1) it.getString(accTypeIndex) else null
                val accName = if (accNameIndex != -1) it.getString(accNameIndex) else null

                val source = when {
                    rawAccType == null -> "DEVICE"
                    rawAccType.contains("google", ignoreCase = true) -> "GOOGLE"
                    rawAccType.contains("whatsapp", ignoreCase = true) -> "WHATSAPP"
                    rawAccType.contains("sim", ignoreCase = true) -> "SIM"
                    else -> "DEVICE"
                }

                val existing = contactsById[id]
                if (existing == null) {
                    contactsById[id] = ContactInfo(
                        id = id,
                        name = name,
                        phoneNumber = number,
                        phoneNumbers = listOf(number).filter { value -> value.isNotBlank() },
                        photoUri = photoUri,
                        isFavorite = isFavorite,
                        source = source,
                        accountName = accName
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
                        isFavorite = existing.isFavorite || isFavorite,
                        source = if (existing.source == "DEVICE") source else existing.source,
                        accountName = existing.accountName ?: accName
                    )
                }
            }
        }

        val result = contactsById.values
            .asSequence()
            .filter { it.name.isNotBlank() || it.phoneNumber.isNotBlank() }
            .sortedBy { it.name.lowercase() }
            .toList()

        cachedContacts = result
        lastLoadedAt = now
        result
    }

    override suspend fun toggleFavorite(contactId: String, isFavorite: Boolean): Boolean = withContext(Dispatchers.IO) {
        val values = android.content.ContentValues().apply {
            put(ContactsContract.Contacts.STARRED, if (isFavorite) 1 else 0)
        }
        val uri = android.net.Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId)
        val rowsUpdated = context.contentResolver.update(uri, values, null, null)
        rowsUpdated > 0
    }

    override suspend fun createContact(name: String, phoneNumber: String): Boolean = withContext(Dispatchers.IO) {
        if (name.isBlank() && phoneNumber.isBlank()) return@withContext false
        try {
            val ops = ArrayList<android.content.ContentProviderOperation>()
            val rawContactInsertIndex = ops.size

            ops.add(
                android.content.ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build()
            )

            if (name.isNotBlank()) {
                ops.add(
                    android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name.trim())
                        .build()
                )
            }

            if (phoneNumber.isNotBlank()) {
                ops.add(
                    android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phoneNumber.trim())
                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                        .build()
                )
            }

            val results = context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            invalidateCache()
            results.isNotEmpty()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun invalidateCache() {
        cachedContacts = null
        lastLoadedAt = 0L
    }
}
