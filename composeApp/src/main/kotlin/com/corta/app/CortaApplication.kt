package com.corta.app

import android.app.Application
import android.content.Context
import android.util.Log
import com.corta.app.di.appModule
import com.corta.app.workers.SubtelSyncScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class CortaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        try {
            System.loadLibrary("sqlcipher")
            Log.d("CortaApplication", "SQLCipher loaded successfully")
        } catch (e: Exception) {
            Log.e("CortaApplication", "Failed to load SQLCipher", e)
        }
        
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@CortaApplication)
            modules(appModule)
        }

        val prefs = getSharedPreferences("corta_prefs", Context.MODE_PRIVATE)
        val subtelEnabled = prefs.getBoolean("use_subtel_rules", true)
        SubtelSyncScheduler.ensurePeriodic(this, subtelEnabled)
        SubtelSyncScheduler.enqueueImmediate(this, subtelEnabled)
    }
}
