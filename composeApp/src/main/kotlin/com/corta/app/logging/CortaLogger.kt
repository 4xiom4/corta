package com.corta.app.logging

import timber.log.Timber

/**
 * Single entry point for installing Corta's logging pipeline.
 *
 * Call this exactly once from [com.corta.app.CortaApplication.onCreate], before any
 * other component is allowed to log. In debug builds both [Timber.DebugTree] and
 * [CortaTree] are planted; in release only [CortaTree] (which filters below WARN).
 */
object CortaLogger {
    @Volatile
    private var installed: Boolean = false

    fun install(isDebug: Boolean) {
        if (installed) return
        installed = true
        if (isDebug) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.plant(CortaTree())
    }
}
