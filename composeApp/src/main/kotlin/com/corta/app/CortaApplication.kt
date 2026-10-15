package com.corta.app

import android.app.Application
import android.content.Context
import com.corta.app.di.DatabaseInitException
import com.corta.app.di.appModule
import com.corta.app.logging.CortaLogger
import com.corta.app.performance.StartupTracer
import com.corta.app.services.RuntimeStatusLogger
import com.corta.app.workers.SubtelSyncScheduler
import com.corta.domain.CortaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import timber.log.Timber

class CortaApplication : Application(), KoinComponent {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository: CortaRepository by inject()

    private companion object {
        const val TAG_APP = "Corta.Application"
    }

    override fun onCreate() {
        setupCrashHandler()
        super.onCreate()

        StartupTracer.mark("super.onCreate")
        // En Android Studio, BuildConfig se genera en el namespace de la app
        CortaLogger.install(isDebug = true) // Fallback simple para debug
        StartupTracer.mark("logger.installed")

        // Ensure SQLCipher is loaded early and once
        try {
            System.loadLibrary("sqlcipher")
            Timber.tag(TAG_APP).d("SQLCipher loaded successfully")
        } catch (e: Throwable) {
            Timber.tag(TAG_APP).e(e, "CRITICAL: Failed to load SQLCipher. Database operations will fail.")
        }
        StartupTracer.mark("sqlcipher.loaded")

        try {
            startKoin {
                androidLogger(Level.ERROR)
                androidContext(this@CortaApplication)
                modules(appModule)
            }
            StartupTracer.mark("koin.started")
        } catch (e: Exception) {
            Timber.tag(TAG_APP).e(e, "Failed to initialize Koin")
        }

        val prefs = getSharedPreferences("corta_prefs", Context.MODE_PRIVATE)
        val subtelEnabled = prefs.getBoolean("use_subtel_rules", true)

        // Periodic + immediate SUBTEL / collaborative / report workers are
        // independent jobs, so any of them can fail and retry without
        // blocking the others. See [SubtelSyncScheduler] for cadence.
        appScope.launch {
            StartupTracer.mark("scope.launched")
            try {
                repository.seedDefaultRulesIfEmpty()
                StartupTracer.mark("repo.warmed")
                SubtelSyncScheduler.ensurePeriodic(this@CortaApplication, subtelEnabled)
                SubtelSyncScheduler.enqueueImmediate(this@CortaApplication, subtelEnabled)
                StartupTracer.mark("subtel.enqueued")
            } catch (e: DatabaseInitException) {
                Timber.tag(TAG_APP).e(e, "Local database unavailable; sync skipped")
            } catch (e: Throwable) {
                Timber.tag(TAG_APP).e(e, "Failed to enqueue sync workers")
            }
            RuntimeStatusLogger.logSnapshot(this@CortaApplication, "AppStart")
            Timber.tag(TAG_APP).i("Startup trace: %s", StartupTracer.snapshot())
        }
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Timber.tag("Corta.Crash").e(throwable, "FATAL CRASH on thread ${thread.name}")
            // Aquí podríamos enviar a Crashlytics o similar en producción
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
