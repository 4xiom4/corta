package com.corta.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.koin.core.component.KoinComponent
import timber.log.Timber

class BootReceiver : BroadcastReceiver(), KoinComponent {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Timber.tag(TAG).d("Boot completed - starting background services")

            try {
                // Inicializar repositorio y servicios aquí si es necesario
                // Por ejemplo, verificar si la app es default y activar servicios
            } catch (e: Throwable) {
                Timber.tag(TAG).e(e, "Error starting services on boot")
            }
        }
    }

    private companion object {
        const val TAG = "Corta.Boot"
    }
}

