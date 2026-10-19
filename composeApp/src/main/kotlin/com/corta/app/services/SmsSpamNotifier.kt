package com.corta.app.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.corta.app.R
import timber.log.Timber

object SmsSpamNotifier {
    private const val TAG = "Corta.SmsNotifier"
    private const val CHANNEL_SPAM_ALERTS = "sms_spam_alerts"
    private const val CHANNEL_GENERAL = "sms_general"

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Avoid recreating channels unnecessarily if they already exist
        if (manager.getNotificationChannel(CHANNEL_SPAM_ALERTS) != null) return

        val prefs = context.getSharedPreferences("corta_prefs", Context.MODE_PRIVATE)
        val soundEnabled = prefs.getBoolean("sms_spam_sound_enabled", true)
        val vibrateEnabled = prefs.getBoolean("sms_spam_vibrate_enabled", true)

        val spamChannel = NotificationChannel(
            CHANNEL_SPAM_ALERTS,
            context.getString(R.string.channel_spam_alerts),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_spam_alerts_desc)
            enableVibration(vibrateEnabled)
            if (soundEnabled) {
                setSound(
                    android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
            } else {
                setSound(null, null)
            }
        }

        val generalChannel = NotificationChannel(
            CHANNEL_GENERAL,
            context.getString(R.string.channel_sms_general),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_sms_general_desc)
        }

        manager.createNotificationChannel(spamChannel)
        manager.createNotificationChannel(generalChannel)
    }

    fun notifySpamBlocked(context: Context, sender: String) {
        if (!canNotify(context)) {
            Timber.tag(TAG).d("Cannot notify: disabled or no permission")
            return
        }
        ensureChannels(context)

        try {
            val notificationId = sender.hashCode()
            val openAppIntent = Intent(context, com.corta.app.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EXTRA_TARGET_TAB", 0)
                putExtra("EXTRA_SMS_SENDER", sender)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Acción interactiva: No es spam (desmarca y añade excepción en segundo plano)
            val allowIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_ALLOW_SMS
                putExtra(NotificationActionReceiver.EXTRA_SMS_SENDER, sender)
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            val allowPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 10,
                allowIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_SPAM_ALERTS)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(context.getString(R.string.sms_blocked_title))
                .setContentText(context.getString(R.string.sms_blocked_desc, sender))
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_menu_view, "Ver mensaje", pendingIntent)
                .addAction(android.R.drawable.ic_menu_revert, "No es spam", allowPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(notificationId, notification)
            Timber.tag(TAG).d("Sent notification for blocked SMS from %s", sender)
        } catch (e: SecurityException) {
            Timber.tag(TAG).e(e, "Missing permission for notification")
        }
    }

    fun notifySpamWarn(context: Context, sender: String) {
        if (!canNotify(context)) return
        ensureChannels(context)

        try {
            val notificationId = sender.hashCode() + 1
            val openAppIntent = Intent(context, com.corta.app.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EXTRA_TARGET_TAB", 0)
                putExtra("EXTRA_SMS_SENDER", sender)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val allowIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_ALLOW_SMS
                putExtra(NotificationActionReceiver.EXTRA_SMS_SENDER, sender)
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            val allowPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 10,
                allowIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val blockIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_BLOCK_SMS
                putExtra(NotificationActionReceiver.EXTRA_SMS_SENDER, sender)
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            val blockPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 20,
                blockIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_SPAM_ALERTS)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(context.getString(R.string.sms_spam_warn_title))
                .setContentText(context.getString(R.string.sms_spam_warn_desc, sender))
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_menu_view, "Ver", pendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Bloquear", blockPendingIntent)
                .addAction(android.R.drawable.ic_menu_revert, "No es spam", allowPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(notificationId, notification)
            Timber.tag(TAG).d("Sent notification for suspicious SMS from %s", sender)
        } catch (e: SecurityException) {
            Timber.tag(TAG).e(e, "Missing permission for notification")
        }
    }

    private fun canNotify(context: Context): Boolean {
        val enabled = context
            .getSharedPreferences("corta_prefs", Context.MODE_PRIVATE)
            .getBoolean("sms_spam_notifications_enabled", true)

        if (!enabled) return false

        // On Android 13+ (Tiramisu), the POST_NOTIFICATIONS permission is
        // mandatory to display notifications.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            return permission == PackageManager.PERMISSION_GRANTED
        }

        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}

