package com.corta.app.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object SmsSpamNotifier {
    private const val CHANNEL_SPAM_ALERTS = "sms_spam_alerts"
    private const val CHANNEL_GENERAL = "sms_general"

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Evitar recrear canales innecesariamente si ya existen con la misma configuración
        if (manager.getNotificationChannel(CHANNEL_SPAM_ALERTS) != null) return

        val prefs = context.getSharedPreferences("corta_prefs", Context.MODE_PRIVATE)
        val soundEnabled = prefs.getBoolean("sms_spam_sound_enabled", true)
        val vibrateEnabled = prefs.getBoolean("sms_spam_vibrate_enabled", true)

        val spamChannel = NotificationChannel(
            CHANNEL_SPAM_ALERTS,
            "Alertas SMS Spam",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alertas de mensajes sospechosos o bloqueados"
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
            "Mensajes SMS",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notificaciones de mensajes"
        }

        manager.createNotificationChannel(spamChannel)
        manager.createNotificationChannel(generalChannel)
    }

    fun notifySpamBlocked(context: Context, sender: String) {
        if (!canNotify(context)) {
            Log.d("SmsSpamNotifier", "Cannot notify: disabled or no permission")
            return
        }
        ensureChannels(context)
        
        try {
            val notification = NotificationCompat.Builder(context, CHANNEL_SPAM_ALERTS)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("SMS bloqueado")
                .setContentText("Se bloqueó un mensaje de $sender")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .build()
            
            NotificationManagerCompat.from(context).notify(sender.hashCode(), notification)
            Log.d("SmsSpamNotifier", "Sent notification for blocked SMS from $sender")
        } catch (e: SecurityException) {
            Log.e("SmsSpamNotifier", "Missing permission for notification", e)
        }
    }

    fun notifySpamWarn(context: Context, sender: String) {
        if (!canNotify(context)) return
        ensureChannels(context)
        
        try {
            val notification = NotificationCompat.Builder(context, CHANNEL_SPAM_ALERTS)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Posible spam por SMS")
                .setContentText("Se detectó un mensaje sospechoso de $sender")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .build()
            
            NotificationManagerCompat.from(context).notify(sender.hashCode() + 1, notification)
            Log.d("SmsSpamNotifier", "Sent notification for suspicious SMS from $sender")
        } catch (e: SecurityException) {
            Log.e("SmsSpamNotifier", "Missing permission for notification", e)
        }
    }

    private fun canNotify(context: Context): Boolean {
        val enabled = context
            .getSharedPreferences("corta_prefs", Context.MODE_PRIVATE)
            .getBoolean("sms_spam_notifications_enabled", true)
        
        if (!enabled) return false

        // En Android 13+ (Tiramisu), el permiso POST_NOTIFICATIONS es obligatorio para mostrar notificaciones.
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
