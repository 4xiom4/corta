package com.corta.app.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.corta.domain.CortaRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * One-off work that drains the [com.corta.db.CommunityReport] queue and
 * pushes pending spam reports to the gateway. Runs on a different cadence
 * from rule syncs so that a network blip on SUBTEL does not block report
 * submission.
 */
class ReportFlushWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val repository: CortaRepository by inject()

    override suspend fun doWork(): Result {
        return try {
            Timber.tag(TAG).d("Starting report flush.")
            repository.flushPendingSpamReports()
            Timber.tag(TAG).d("Finished report flush.")
            Result.success()
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Error flushing pending reports")
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "Corta.ReportsWorker"
    }
}
