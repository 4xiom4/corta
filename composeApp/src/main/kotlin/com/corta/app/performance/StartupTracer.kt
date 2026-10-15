package com.corta.app.performance

import android.os.SystemClock
import timber.log.Timber

/**
 * Lightweight tracing helper for non-instrumented startup profiling.
 *
 * `android.os.Trace` requires a constant tag and is only active with
 * `startMethodTracing` or `am trace`. For the on-device case we use
 * [SystemClock.elapsedRealtime] deltas and a single `Timber.d` line per
 * section, which is enough to spot regressions without taking on the
 * overhead of the full ART profiler.
 */
object StartupTracer {

    private val t0: Long = SystemClock.elapsedRealtime()
    private val sections: MutableMap<String, Long> = mutableMapOf()

    fun mark(label: String) {
        if (sections.containsKey(label)) return
        sections[label] = SystemClock.elapsedRealtime() - t0
        Timber.tag(TAG).d("[startup] %-24s +%d ms", label, sections[label])
    }

    fun snapshot(): String = buildString {
        sections.toSortedMap().forEach { (label, ms) ->
            append("$label=${ms}ms ")
        }
    }.trim()

    private const val TAG = "Corta.Startup"
}
