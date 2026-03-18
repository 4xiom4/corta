package com.corta.app.services

import android.app.Service
import android.content.ContentValues
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Telephony
import android.util.Log
import com.corta.domain.CortaRepository
import com.corta.domain.FilterAction
import com.corta.domain.SpamEvaluator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SmsReceiver : BroadcastReceiver(), KoinComponent {

    private val repository: CortaRepository by inject()
    private val evaluator: SpamEvaluator by inject()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
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
                        Log.d("SmsReceiver", "Received SMS from $sender. Action: $action")

                        when (action) {
                            FilterAction.BLOCK -> {
                                repository.logBlockedSms(sender, body, timestamp)
                                repository.logSmsAction(sender, timestamp, action, body)
                                SmsSpamNotifier.notifySpamBlocked(context, sender)
                                Log.w("SmsReceiver", "Blocked SMS from $sender")
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
                                Log.d("SmsReceiver", "Muted SMS inserted as read for $sender")
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
                                SmsSpamNotifier.notifySpamWarn(context, sender)
                                Log.d("SmsReceiver", "Warn SMS inserted for $sender")
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
                                Log.d("SmsReceiver", "Allowed SMS inserted for $sender")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SmsReceiver", "Error processing SMS", e)
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
}

class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Handle incoming MMS
    }
}

class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent): IBinder? = null
}
