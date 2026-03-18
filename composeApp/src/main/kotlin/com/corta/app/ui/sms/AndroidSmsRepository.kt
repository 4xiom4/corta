package com.corta.app.ui.sms

import android.content.Context
import android.provider.Telephony
import com.corta.domain.CortaRepository
import com.corta.domain.FilterAction
import com.corta.domain.PhoneNumberUtils
import com.corta.domain.SmsMessage
import com.corta.domain.SmsRepository
import com.corta.db.BlockedSms
import com.corta.db.FilterRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

class AndroidSmsRepository(
    private val context: Context,
    private val cortaRepository: CortaRepository
) : SmsRepository {

    private fun getBlockFilters(rules: List<FilterRule>): Triple<Set<String>, List<String>, List<Regex>> {
        val blockExact = HashSet<String>()
        val blockPrefix = mutableListOf<String>()
        val blockRegex = mutableListOf<Regex>()

        rules.filter { it.action == FilterAction.BLOCK.name }.forEach { rule ->
            if (rule.isRegex == true) {
                runCatching { Regex(rule.pattern) }.getOrNull()?.let { blockRegex.add(it) }
            } else {
                val normalizedPattern = PhoneNumberUtils.normalize(rule.pattern)
                blockExact.add(normalizedPattern)
                blockExact.add(rule.pattern)
                if (rule.pattern.length < 11) {
                    blockPrefix.add(rule.pattern)
                }
                if (normalizedPattern.length < 11) {
                    blockPrefix.add(normalizedPattern)
                }
            }
        }
        return Triple(blockExact, blockPrefix, blockRegex)
    }

    private fun isAddressBlocked(address: String, filters: Triple<Set<String>, List<String>, List<Regex>>): Boolean {
        val (exact, prefix, regex) = filters
        val normalized = PhoneNumberUtils.normalize(address)
        if (normalized in exact || address in exact) return true
        if (prefix.any { normalized.startsWith(it) || address.startsWith(it) }) return true
        if (regex.any { it.containsMatchIn(normalized) || it.containsMatchIn(address) }) return true
        return false
    }

    override suspend fun getConversations(rules: List<FilterRule>): List<SmsMessage> = withContext(Dispatchers.IO) {
        val filters = getBlockFilters(rules)

        val messages = mutableListOf<SmsMessage>()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )

        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            null,
            null,
            Telephony.Sms.DEFAULT_SORT_ORDER
        )

        val threadIdsSeen = mutableSetOf<String>()

        cursor?.use {
            val idIndex = it.getColumnIndex(Telephony.Sms._ID)
            val threadIdIndex = it.getColumnIndex(Telephony.Sms.THREAD_ID)
            val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
            val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)

            while (it.moveToNext()) {
                val threadId = it.getString(threadIdIndex)
                if (threadIdsSeen.add(threadId)) {
                    val address = it.getString(addressIndex) ?: "Unknown"
                    if (!isAddressBlocked(address, filters)) {
                        messages.add(SmsMessage(
                            id = it.getString(idIndex),
                            threadId = threadId,
                            address = address,
                            body = it.getString(bodyIndex) ?: "",
                            date = it.getLong(dateIndex),
                            type = it.getInt(typeIndex)
                        ))
                    }
                }
            }
        }
        messages
    }

    override suspend fun getBlockedConversations(rules: List<FilterRule>): List<SmsMessage> = withContext(Dispatchers.IO) {
        val filters = getBlockFilters(rules)

        val messagesFromSystem = mutableListOf<SmsMessage>()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )

        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            null,
            null,
            Telephony.Sms.DEFAULT_SORT_ORDER
        )

        val threadIdsSeen = mutableSetOf<String>()

        cursor?.use {
            val idIndex = it.getColumnIndex(Telephony.Sms._ID)
            val threadIdIndex = it.getColumnIndex(Telephony.Sms.THREAD_ID)
            val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
            val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)

            while (it.moveToNext()) {
                val threadId = it.getString(threadIdIndex)
                if (threadIdsSeen.add(threadId)) {
                    val address = it.getString(addressIndex) ?: "Unknown"
                    if (isAddressBlocked(address, filters)) {
                        messagesFromSystem.add(SmsMessage(
                            id = it.getString(idIndex),
                            threadId = threadId,
                            address = address,
                            body = it.getString(bodyIndex) ?: "",
                            date = it.getLong(dateIndex),
                            type = it.getInt(typeIndex)
                        ))
                    }
                }
            }
        }

        val blockedList = cortaRepository.observeBlockedSms().first()
        val internalMessages = blockedList.map { blocked ->
            SmsMessage(
                id = blocked.id.toString(),
                threadId = "BLOCKED_${blocked.sender}",
                address = blocked.sender,
                body = blocked.body,
                date = blocked.timestamp,
                type = 1
            )
        }

        (messagesFromSystem + internalMessages).sortedByDescending { it.date }.distinctBy { it.address }
    }

    override suspend fun getMessagesForThread(threadId: String): List<SmsMessage> = withContext(Dispatchers.IO) {
        if (threadId.startsWith("BLOCKED_")) {
            val sender = threadId.removePrefix("BLOCKED_")
            return@withContext cortaRepository.observeBlockedSms()
                .first()
                .filter { it.sender == sender }
                .sortedBy { it.timestamp }
                .map { blocked ->
                    SmsMessage(
                        id = blocked.id.toString(),
                        threadId = threadId,
                        address = blocked.sender,
                        body = blocked.body,
                        date = blocked.timestamp,
                        type = 1
                    )
                }
        }

        val messages = mutableListOf<SmsMessage>()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )

        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            "${Telephony.Sms.THREAD_ID} = ?",
            arrayOf(threadId),
            Telephony.Sms.DEFAULT_SORT_ORDER
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(Telephony.Sms._ID)
            val threadIdIndex = it.getColumnIndex(Telephony.Sms.THREAD_ID)
            val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
            val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)

            while (it.moveToNext()) {
                messages.add(SmsMessage(
                    id = it.getString(idIndex),
                    threadId = it.getString(threadIdIndex),
                    address = it.getString(addressIndex) ?: "Unknown",
                    body = it.getString(bodyIndex) ?: "",
                    date = it.getLong(dateIndex),
                    type = it.getInt(typeIndex)
                ))
            }
        }
        messages
    }
}
