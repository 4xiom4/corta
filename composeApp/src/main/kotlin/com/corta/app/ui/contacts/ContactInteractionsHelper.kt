package com.corta.app.ui.contacts

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.corta.db.BlockedSms
import com.corta.db.CallLogEntry
import com.corta.domain.CortaRepository
import com.corta.domain.PhoneNumberUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class InteractionChannel {
    CALL_INCOMING,
    CALL_OUTGOING,
    CALL_MISSED,
    CALL_BLOCKED,
    SMS_INCOMING,
    SMS_OUTGOING
}

data class ContactInteractionItem(
    val id: String,
    val channel: InteractionChannel,
    val timestamp: Long,
    val durationSeconds: Long = 0,
    val previewText: String? = null,
    val isSpamOrBlocked: Boolean = false
)

object ContactInteractionsHelper {

    suspend fun getRecentInteractions(
        context: Context,
        cortaRepository: CortaRepository,
        phoneNumbers: List<String>,
        limit: Int = 8
    ): List<ContactInteractionItem> = withContext(Dispatchers.IO) {
        val candidates = mutableSetOf<String>()
        phoneNumbers.forEach { num ->
            if (num.isNotBlank()) {
                candidates.addAll(PhoneNumberUtils.getSymmetricCandidates(num))
                candidates.add(num.trim())
                candidates.add(PhoneNumberUtils.normalize(num))
            }
        }
        if (candidates.isEmpty()) return@withContext emptyList()

        val results = mutableListOf<ContactInteractionItem>()

        // 1. Query Calls from CallLog.Calls (Android 10/15 safe)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            try {
                val candidateList = candidates.toList()
                val inPlaceholders = candidateList.joinToString(",") { "?" }
                val selection = "${CallLog.Calls.NUMBER} IN ($inPlaceholders)"
                val selectionArgs = candidateList.toTypedArray()
                val projection = arrayOf(
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.DATE,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DURATION
                )

                val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val bundle = Bundle().apply {
                        putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                        putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                        putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(CallLog.Calls.DATE))
                        putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                        putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                    }
                    context.contentResolver.query(CallLog.Calls.CONTENT_URI, projection, bundle, null)
                } else {
                    context.contentResolver.query(
                        CallLog.Calls.CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        "${CallLog.Calls.DATE} DESC"
                    )
                }

                cursor?.use {
                    val idIdx = it.getColumnIndex(CallLog.Calls._ID)
                    val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                    val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                    val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)

                    while (it.moveToNext()) {
                        val id = if (idIdx != -1) it.getString(idIdx) ?: "" else ""
                        val date = if (dateIdx != -1) it.getLong(dateIdx) else 0L
                        val type = if (typeIdx != -1) it.getInt(typeIdx) else 0
                        val dur = if (durIdx != -1) it.getLong(durIdx) else 0L

                        val channel = when (type) {
                            CallLog.Calls.INCOMING_TYPE -> InteractionChannel.CALL_INCOMING
                            CallLog.Calls.OUTGOING_TYPE -> InteractionChannel.CALL_OUTGOING
                            CallLog.Calls.MISSED_TYPE -> InteractionChannel.CALL_MISSED
                            CallLog.Calls.REJECTED_TYPE, CallLog.Calls.BLOCKED_TYPE -> InteractionChannel.CALL_BLOCKED
                            else -> InteractionChannel.CALL_INCOMING
                        }

                        results.add(
                            ContactInteractionItem(
                                id = "call_$id",
                                channel = channel,
                                timestamp = date,
                                durationSeconds = dur,
                                isSpamOrBlocked = channel == InteractionChannel.CALL_BLOCKED
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Ignore exception safely
            }
        }

        // 2. Query SMS from Telephony.Sms (Android 10/15 safe)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            try {
                val candidateList = candidates.toList()
                val inPlaceholders = candidateList.joinToString(",") { "?" }
                val selection = "${Telephony.Sms.ADDRESS} IN ($inPlaceholders)"
                val selectionArgs = candidateList.toTypedArray()
                val projection = arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE,
                    Telephony.Sms.BODY
                )

                val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val bundle = Bundle().apply {
                        putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                        putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                        putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(Telephony.Sms.DATE))
                        putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                        putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                    }
                    context.contentResolver.query(Telephony.Sms.CONTENT_URI, projection, bundle, null)
                } else {
                    context.contentResolver.query(
                        Telephony.Sms.CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        "${Telephony.Sms.DATE} DESC"
                    )
                }

                cursor?.use {
                    val idIdx = it.getColumnIndex(Telephony.Sms._ID)
                    val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)
                    val typeIdx = it.getColumnIndex(Telephony.Sms.TYPE)
                    val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)

                    while (it.moveToNext()) {
                        val id = if (idIdx != -1) it.getString(idIdx) ?: "" else ""
                        val date = if (dateIdx != -1) it.getLong(dateIdx) else 0L
                        val type = if (typeIdx != -1) it.getInt(typeIdx) else 0
                        val body = if (bodyIdx != -1) it.getString(bodyIdx) ?: "" else ""

                        val channel = if (type == 2) InteractionChannel.SMS_OUTGOING else InteractionChannel.SMS_INCOMING

                        results.add(
                            ContactInteractionItem(
                                id = "sms_$id",
                                channel = channel,
                                timestamp = date,
                                previewText = body.take(120),
                                isSpamOrBlocked = false
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Ignore exception safely
            }
        }

        // 3. Check Corta Blocked Logs
        try {
            val blockedCalls = cortaRepository.getCallLogs().filter { it.phoneNumber in candidates && it.actionTaken == "BLOCK" }
            blockedCalls.take(3).forEach { entry ->
                results.add(
                    ContactInteractionItem(
                        id = "corta_call_${entry.id}",
                        channel = InteractionChannel.CALL_BLOCKED,
                        timestamp = entry.timestamp,
                        durationSeconds = entry.duration,
                        isSpamOrBlocked = true
                    )
                )
            }

            val blockedSms = cortaRepository.getBlockedSms().filter { it.sender in candidates }
            blockedSms.take(3).forEach { entry ->
                results.add(
                    ContactInteractionItem(
                        id = "corta_sms_${entry.id}",
                        channel = InteractionChannel.SMS_INCOMING,
                        timestamp = entry.timestamp,
                        previewText = entry.body.take(120),
                        isSpamOrBlocked = true
                    )
                )
            }
        } catch (e: Exception) {
            // Ignore
        }

        results.sortedByDescending { it.timestamp }.take(limit)
    }
}
