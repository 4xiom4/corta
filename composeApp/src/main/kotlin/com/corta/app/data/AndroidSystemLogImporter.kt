package com.corta.app.data

import android.content.Context
import android.provider.CallLog
import android.provider.Telephony
import com.corta.domain.CortaRepository
import com.corta.domain.FilterAction
import timber.log.Timber

/**
 * Android-only import bridge for historical CallLog and SMS Inbox data.
 *
 * These functions live in :composeApp (not :shared/commonMain) because they
 * depend on the platform providers [android.provider.CallLog] and
 * [android.provider.Telephony], which are not available in pure-JVM source
 * sets. The [CortaRepository] exposes only KMP-safe operations.
 *
 * The class is stateless; the [context] is provided per-call so the bridge can
 * be invoked from any [android.content.Context] the UI or a worker owns.
 */
class AndroidSystemLogImporter(
    private val repository: CortaRepository
) {

    fun importAll(context: Context) {
        importSystemCallLogs(context)
        importSystemSmsLogs(context)
    }

    fun importSystemCallLogs(context: Context) {
        try {
            val projection = arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.DATE,
                CallLog.Calls.TYPE,
                CallLog.Calls.DURATION
            )
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection, null, null, "${CallLog.Calls.DATE} DESC"
            )
            cursor?.use { c ->
                val numberIdx = c.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIdx = c.getColumnIndex(CallLog.Calls.DATE)
                val typeIdx = c.getColumnIndex(CallLog.Calls.TYPE)
                val durIdx = c.getColumnIndex(CallLog.Calls.DURATION)
                var importedCount = 0
                while (c.moveToNext()) {
                    val number = c.getString(numberIdx) ?: ""
                    val timestamp = c.getLong(dateIdx)
                    val type = c.getInt(typeIdx)
                    val duration = c.getLong(durIdx)
                    val action = when (type) {
                        CallLog.Calls.MISSED_TYPE,
                        CallLog.Calls.INCOMING_TYPE,
                        CallLog.Calls.OUTGOING_TYPE -> FilterAction.ALLOW
                        else -> FilterAction.ALLOW
                    }
                    repository.logCall(number, timestamp, action, duration)
                    importedCount++
                }
                Timber.tag(TAG).d("Imported $importedCount system call logs")
            }
        } catch (e: SecurityException) {
            Timber.tag(TAG).w(e, "READ_CALL_LOG not granted; skipping import")
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Error importing system call logs")
        }
    }

    fun importSystemSmsLogs(context: Context) {
        try {
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE
            )
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection, null, null, "${Telephony.Sms.DATE} DESC"
            )
            cursor?.use { c ->
                val addressIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = c.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = c.getColumnIndex(Telephony.Sms.DATE)
                val typeIdx = c.getColumnIndex(Telephony.Sms.TYPE)
                var importedCount = 0
                while (c.moveToNext()) {
                    val sender = c.getString(addressIdx) ?: ""
                    val body = c.getString(bodyIdx) ?: ""
                    val timestamp = c.getLong(dateIdx)
                    val type = c.getInt(typeIdx)
                    if (type == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                        val preview = if (body.length <= 120) body else body.take(120)
                        repository.logSmsAction(sender, timestamp, FilterAction.ALLOW, body = preview)
                        importedCount++
                    }
                }
                Timber.tag(TAG).d("Imported $importedCount system SMS logs")
            }
        } catch (e: SecurityException) {
            Timber.tag(TAG).w(e, "READ_SMS not granted; skipping import")
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Error importing system SMS logs")
        }
    }
}

private const val TAG = "Corta.SystemLogImporter"
