package com.corta.app.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.corta.app.R

/**
 * Notifica al usuario la acción tomada por el screening de llamadas cuando la UI principal no se muestra.
 */
object CallAlertNotifier {
    private const val CHANNEL_ID = "call_screening_actions"

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.call_actions_channel),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.call_actions_channel_desc)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permission != PackageManager.PERMISSION_GRANTED) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun notifyAction(context: Context, phoneNumber: String, action: CallAction) {
        if (!canNotify(context)) return
        ensureChannel(context)

        val meta = com.corta.domain.ChileanTelecomClassifier.classify(phoneNumber)
        val formattedNum = com.corta.domain.PhoneNumberUtils.formatDisplayNumber(phoneNumber)

        val (title, body) = when {
            action == CallAction.BLOCKED && (meta.type == com.corta.domain.ChileanNumberType.SUBTEL_TELEMARKETING_809 || phoneNumber.contains("809")) -> {
                "Corta: Telemarketing 809 Bloqueado" to "$formattedNum · Campaña comercial masiva interceptada"
            }
            action == CallAction.BLOCKED && (phoneNumber.isBlank() || phoneNumber.lowercase().contains("privado") || phoneNumber.lowercase().contains("desconocido")) -> {
                "Corta: Número Privado Bloqueado" to "Llamada sin identificador rechazada automáticamente"
            }
            action == CallAction.BLOCKED -> {
                "Corta: Llamada Spam Bloqueada" to "$formattedNum · Silenciada en <2ms"
            }
            action == CallAction.MUTED -> {
                "Corta: Llamada Sospechosa Silenciada" to "$formattedNum · Timbre atenuado"
            }
            else -> {
                "Corta: Alerta de Llamada" to "$formattedNum · Número no registrado"
            }
        }

        val openAppIntent = Intent(context, com.corta.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            phoneNumber.hashCode(),
            openAppIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(title)
            .setContentText(body)
            .setSubText("¡Corta la hace corta!")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify(phoneNumber.hashCode(), notification)
    }
}

enum class CallAction { BLOCKED, MUTED, WARNED }

