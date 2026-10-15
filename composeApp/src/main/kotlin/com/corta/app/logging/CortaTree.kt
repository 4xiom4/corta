package com.corta.app.logging

import android.util.Log
import com.corta.app.BuildConfig
import timber.log.Timber

/**
 * Release tree that forwards only warnings and errors to logcat and tags every line
 * with the originating module. Debug builds get [Timber.DebugTree] planted alongside
 * via [CortaLogger.plant].
 *
 * Centralising tag policy here makes it easy to swap to Crashlytics / Sentry later
 * without touching every call site.
 */
class CortaTree : Timber.Tree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean {
        if (BuildConfig.DEBUG) return true
        return priority >= Log.WARN
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (t != null) {
            Log.println(priority, tag, "$message\n${Log.getStackTraceString(t)}")
        } else {
            Log.println(priority, tag, message)
        }
    }
}
