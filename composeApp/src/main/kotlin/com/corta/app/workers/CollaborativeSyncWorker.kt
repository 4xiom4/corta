package com.corta.app.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.corta.domain.CortaRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * One-off work that refreshes the curated + community rules. Triggered
 * on app start and every 12h. Runs independently from SUBTEL so a
 * SUBTEL failure cannot suppress a community refresh.
 */
class CollaborativeSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val repository: CortaRepository by inject()

    override suspend fun doWork(): Result {
        return try {
            Timber.tag(TAG).d("Starting collaborative sync.")
            repository.syncCollaborativeRules()
            Timber.tag(TAG).d("Finished collaborative sync.")
            Result.success()
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Error syncing collaborative rules")
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "Corta.CollabWorker"
    }
}
