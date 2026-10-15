package com.corta.app.workers

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules the three sync workers (SUBTEL, collaborative, reports) with
 * the WorkManager constraints they need. Each worker is enqueued as a
 * unique work so a slow SUBTEL refresh never blocks the report flush and
 * vice-versa.
 */
object SubtelSyncScheduler {
    private const val PERIODIC_SUBTEL = "corta_periodic_subtel"
    private const val PERIODIC_COLLAB = "corta_periodic_collab"
    private const val PERIODIC_REPORTS = "corta_periodic_reports"
    private const val IMMEDIATE_SUBTEL = "corta_immediate_subtel"
    private const val IMMEDIATE_COLLAB = "corta_immediate_collab"
    private const val IMMEDIATE_REPORTS = "corta_immediate_reports"

    private const val PERIODIC_INTERVAL_HOURS = 12L
    private const val BACKOFF_SECONDS = 30L

    fun enqueueImmediate(context: Context, enabled: Boolean) {
        val networkConstraints = networkConstraints()
        val workManager = WorkManager.getInstance(context)

        workManager.enqueueUniqueWork(
            IMMEDIATE_SUBTEL,
            ExistingWorkPolicy.REPLACE,
            oneTimeSubtel(enabled, networkConstraints)
        )
        workManager.enqueueUniqueWork(
            IMMEDIATE_COLLAB,
            ExistingWorkPolicy.REPLACE,
            oneTimeCollab(networkConstraints)
        )
        workManager.enqueueUniqueWork(
            IMMEDIATE_REPORTS,
            ExistingWorkPolicy.REPLACE,
            oneTimeReports(networkConstraints)
        )
    }

    fun ensurePeriodic(context: Context, enabled: Boolean) {
        val networkConstraints = networkConstraints()
        val workManager = WorkManager.getInstance(context)

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SUBTEL,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicSubtel(enabled, networkConstraints)
        )
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_COLLAB,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicCollab(networkConstraints)
        )
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_REPORTS,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicReports(networkConstraints)
        )
    }

    private fun networkConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private fun oneTimeSubtel(enabled: Boolean, constraints: Constraints) =
        OneTimeWorkRequestBuilder<SubtelSyncWorker>()
            .setInputData(SubtelSyncWorker.inputData(enabled))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

    private fun oneTimeCollab(constraints: Constraints) =
        OneTimeWorkRequestBuilder<CollaborativeSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

    private fun oneTimeReports(constraints: Constraints) =
        OneTimeWorkRequestBuilder<ReportFlushWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

    private fun periodicSubtel(enabled: Boolean, constraints: Constraints) =
        PeriodicWorkRequestBuilder<SubtelSyncWorker>(PERIODIC_INTERVAL_HOURS, TimeUnit.HOURS)
            .setInputData(SubtelSyncWorker.inputData(enabled))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

    private fun periodicCollab(constraints: Constraints) =
        PeriodicWorkRequestBuilder<CollaborativeSyncWorker>(PERIODIC_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

    private fun periodicReports(constraints: Constraints) =
        PeriodicWorkRequestBuilder<ReportFlushWorker>(PERIODIC_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
}
