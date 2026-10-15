package com.corta.app.services

import android.app.Service
import android.content.ContentValues
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Telephony
import com.corta.domain.CortaRepository
import com.corta.domain.FilterAction
import com.corta.domain.SpamEvaluator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

class SmsReceiver : BroadcastReceiver(), KoinComponent {

    private val repository: CortaRepository by inject()
    private val evaluator: SpamEvaluator by inject()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            RuntimeStatusLogger.logSnapshot(context, "SmsReceiver")
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isEmpty()) return
            val pendingResult = goAsync()

            scope.launch {
                try {
                    // Consolidate multipart SMS by sender + timestamp window (single broadcast)
                    val groupedMessages = messages.groupBy { sms ->
                        val sender = sms.originatingAddress ?: ""
                        val timestampBucket = sms.timestampMillis / 1000L
                        "$sender-$timestampBucket"
                    }

                    groupedMessages.values.forEach { parts ->
                        val sender = parts.firstOrNull()?.originatingAddress ?: return@forEach
                        val body = parts.joinToString(separator = "") { it.messageBody.orEmpty() }
                        val timestamp = parts.maxOfOrNull { it.timestampMillis } ?: System.currentTimeMillis()

                        val action = evaluator.evaluateSms(sender, body)
                        Timber.tag(TAG).d("Received SMS from %s. Action: %s", sender, action)

                        when (action) {
                            FilterAction.BLOCK -> {
                                repository.logBlockedSms(sender, body, timestamp)
                                repository.logSmsAction(sender, timestamp, action, body)
                                repository.queueSpamReport(sender, "SMS", "AUTO_BLOCK")
                                SmsSpamNotifier.notifySpamBlocked(context, sender)
                                Timber.tag(TAG).w("Blocked SMS from %s", sender)
                            }

                            FilterAction.MUTE -> {
                                insertInboxMessage(
                                    context = context,
                                    sender = sender,
                                    body = body,
                                    timestamp = timestamp,
                                    markRead = true
                                )
                                repository.logSmsAction(sender, timestamp, action, body)
                                repository.queueSpamReport(sender, "SMS", "AUTO_MUTE")
                                Timber.tag(TAG).d("Muted SMS inserted as read for %s", sender)
                            }

                            FilterAction.WARN -> {
                                insertInboxMessage(
                                    context = context,
                                    sender = sender,
                                    body = "[POSIBLE SPAM] $body",
                                    timestamp = timestamp,
                                    markRead = false
                                )
                                repository.logSmsAction(sender, timestamp, action, body)
                                repository.queueSpamReport(sender, "SMS", "AUTO_WARN")
                                SmsSpamNotifier.notifySpamWarn(context, sender)
                                Timber.tag(TAG).d("Warn SMS inserted for %s", sender)
                            }

                            FilterAction.ALLOW -> {
                                insertInboxMessage(
                                    context = context,
                                    sender = sender,
                                    body = body,
                                    timestamp = timestamp,
                                    markRead = false
                                )
                                repository.logSmsAction(sender, timestamp, action, body)
                                Timber.tag(TAG).d("Allowed SMS inserted for %s", sender)
                            }
                        }
                    }
                } catch (e: Throwable) {
                    Timber.tag(TAG).e(e, "Error processing SMS")
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun insertInboxMessage(
        context: Context,
        sender: String,
        body: String,
        timestamp: Long,
        markRead: Boolean
    ) {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, sender)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, timestamp)
            put(Telephony.Sms.READ, if (markRead) 1 else 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
        }
        context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
    }

    private companion object {
        const val TAG = "Corta.SmsReceiver"
    }
}

class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Handle incoming MMS
    }
}

class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent): IBinder? = null
}

