package com.corta.app.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.corta.app.ui.InCallActivity
import com.corta.domain.SpamEvaluator
import org.koin.android.ext.android.inject
import android.provider.ContactsContract
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.corta.app.R
import timber.log.Timber

class CortaInCallService : InCallService() {
    private val spamEvaluator: SpamEvaluator by inject()
    private var ringtone: Ringtone? = null

    companion object {
        private const val TAG = "Corta.InCallService"
        private const val CALL_ALERT_CHANNEL_ID = "incoming_call_alerts"
        private const val CALL_ALERT_NOTIFICATION_ID = 3001
    }

    override fun onCreate() {
        super.onCreate()
        CallManager.setService(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        CallManager.setService(null)
    }

    @Suppress("DEPRECATION")
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val phoneNumber = call.details.handle?.schemeSpecificPart ?: "Unknown"
        val spamAction = spamEvaluator.evaluateCall(phoneNumber).name

        // Intentar obtener el nombre del contacto
        val contactName = getContactName(phoneNumber)
        val displayName = contactName ?: phoneNumber

        Timber.tag(TAG).d("Call added: %s (%s), State: %d", phoneNumber, displayName, call.state)

        CallManager.updateCall(call)

        if (call.state == Call.STATE_RINGING) {
            startRingtone()
        }
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                if (state != Call.STATE_RINGING) {
                    stopRingtone()
                }
            }
        })

        // Only launch UI if the call is not already disconnected
        if (call.state != Call.STATE_DISCONNECTED) {
            val intent = Intent(this, InCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("EXTRA_PHONE_NUMBER", displayName)
                // También pasamos el número original por si se necesita
                putExtra("EXTRA_RAW_PHONE_NUMBER", phoneNumber)
                putExtra("EXTRA_SPAM_ACTION", spamAction)
            }
            try {
                startActivity(intent)
                dismissIncomingCallFallback()
            } catch (e: Throwable) {
                Timber.tag(TAG).e(e, "Unable to open in-call UI, using fallback notification")
                showIncomingCallFallback(displayName, phoneNumber, spamAction)
            }
        }
    }

    private fun getContactName(phoneNumber: String): String? {
        var name: String? = null
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = cursor.getString(nameIndex)
                }
            }
        }
        return name
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Timber.tag(TAG).d("Call removed")
        stopRingtone()
        dismissIncomingCallFallback()
        if (CallManager.currentCall == call) {
            CallManager.clearCall()
        }
    }

    private fun showIncomingCallFallback(displayName: String, phoneNumber: String, spamAction: String) {
        ensureCallAlertChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            if (permission != PackageManager.PERMISSION_GRANTED) {
                Timber.tag(TAG).w("POST_NOTIFICATIONS not granted; fallback notification skipped")
                return
            }
        }

        val intent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EXTRA_PHONE_NUMBER", displayName)
            putExtra("EXTRA_RAW_PHONE_NUMBER", phoneNumber)
            putExtra("EXTRA_SPAM_ACTION", spamAction)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CALL_ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(getString(R.string.incoming_call_notification_title))
            .setContentText(displayName)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .build()

        NotificationManagerCompat.from(this).notify(CALL_ALERT_NOTIFICATION_ID, notification)
    }

    private fun dismissIncomingCallFallback() {
        NotificationManagerCompat.from(this).cancel(CALL_ALERT_NOTIFICATION_ID)
    }

    private fun ensureCallAlertChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CALL_ALERT_CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CALL_ALERT_CHANNEL_ID,
            getString(R.string.call_alert_channel),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.call_alert_channel_desc)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .build()
            )
        }
        manager.createNotificationChannel(channel)
    }

    @Deprecated("Deprecated in Java", replaceWith = ReplaceWith("onCallAudioStateChanged(audioState)"))
    @Suppress("DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        CallManager.onAudioStateChanged(audioState)
    }

    private fun startRingtone() {
        try {
            stopRingtone()
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(this, ringtoneUri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    isLooping = true
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .build()
                }
                play()
            }
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Unable to start ringtone")
        }
    }

    private fun stopRingtone() {
        try {
            ringtone?.stop()
            ringtone = null
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Unable to stop ringtone")
        }
    }
}

