package com.corta.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BootReceiver : BroadcastReceiver(), KoinComponent {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Boot completed - starting background services")
            
            // Iniciar servicios en segundo plano si es necesario
            try {
                // Inicializar repositorio y servicios aquí si es necesario
                // Por ejemplo, verificar si la app es default y activar servicios
            } catch (e: Exception) {
                Log.e("BootReceiver", "Error starting services on boot", e)
            }
        }
    }
}
