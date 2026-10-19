package com.corta.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.corta.domain.CortaRepository
import com.corta.domain.FilterAction
import com.corta.domain.PhoneNumberUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * BroadcastReceiver para procesar acciones directas desde notificaciones
 * ("No es spam", "Bloquear", etc.) en segundo plano sin obligar a abrir la app.
 */
class NotificationActionReceiver : BroadcastReceiver(), KoinComponent {

    private val cortaRepository: CortaRepository by inject()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val ACTION_ALLOW_SMS = "com.corta.app.ACTION_ALLOW_SMS"
        const val ACTION_BLOCK_SMS = "com.corta.app.ACTION_BLOCK_SMS"
        const val ACTION_ALLOW_CALL = "com.corta.app.ACTION_ALLOW_CALL"
        const val ACTION_BLOCK_CALL = "com.corta.app.ACTION_BLOCK_CALL"

        const val EXTRA_SMS_SENDER = "EXTRA_SMS_SENDER"
        const val EXTRA_CALL_NUMBER = "EXTRA_CALL_NUMBER"
        const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        // Cancelar la notificación inmediatamente para respuesta visual instantánea
        if (notificationId != -1) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }

        val pendingResult = goAsync()

        scope.launch {
            try {
                when (action) {
                    ACTION_ALLOW_SMS -> {
                        val sender = intent.getStringExtra(EXTRA_SMS_SENDER) ?: return@launch
                        val normalized = PhoneNumberUtils.normalize(sender)
                        cortaRepository.deleteRule(normalized)
                        cortaRepository.addRule(
                            pattern = normalized,
                            action = FilterAction.ALLOW,
                            isRegex = false,
                            alias = "Desmarcado como spam",
                            source = "USER"
                        )
                        showToast(context, "Remitente $sender marcado como seguro")
                        Timber.d("SMS sender %s marked as ALLOW from notification", sender)
                    }

                    ACTION_BLOCK_SMS -> {
                        val sender = intent.getStringExtra(EXTRA_SMS_SENDER) ?: return@launch
                        val normalized = PhoneNumberUtils.normalize(sender)
                        cortaRepository.addRule(
                            pattern = normalized,
                            action = FilterAction.BLOCK,
                            isRegex = false,
                            alias = "Bloqueado desde notificación",
                            source = "USER"
                        )
                        showToast(context, "Remitente $sender bloqueado")
                        Timber.d("SMS sender %s marked as BLOCK from notification", sender)
                    }

                    ACTION_ALLOW_CALL -> {
                        val number = intent.getStringExtra(EXTRA_CALL_NUMBER) ?: return@launch
                        val normalized = PhoneNumberUtils.normalize(number)
                        cortaRepository.deleteRule(normalized)
                        cortaRepository.addRule(
                            pattern = normalized,
                            action = FilterAction.ALLOW,
                            isRegex = false,
                            alias = "Desmarcado como spam",
                            source = "USER"
                        )
                        showToast(context, "Número $number marcado como seguro")
                        Timber.d("Call number %s marked as ALLOW from notification", number)
                    }

                    ACTION_BLOCK_CALL -> {
                        val number = intent.getStringExtra(EXTRA_CALL_NUMBER) ?: return@launch
                        val normalized = PhoneNumberUtils.normalize(number)
                        cortaRepository.addRule(
                            pattern = normalized,
                            action = FilterAction.BLOCK,
                            isRegex = false,
                            alias = "Bloqueado desde notificación",
                            source = "USER"
                        )
                        showToast(context, "Número $number bloqueado")
                        Timber.d("Call number %s marked as BLOCK from notification", number)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error executing notification action: %s", action)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}
