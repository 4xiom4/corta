package com.corta.app.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.corta.data.network.SubtelApiClient
import com.corta.domain.CortaRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * One-off work that refreshes the local cache of SUBTEL rules. Triggered
 * on app start and every 12h via [SubtelSyncScheduler].
 */
class SubtelSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val repository: CortaRepository by inject()
    private val apiClient: SubtelApiClient by inject()

    override suspend fun doWork(): Result {
        val enabled = inputData.getBoolean(KEY_SUBTEL_ENABLED, true)
        return try {
            Timber.tag(TAG).d("Starting SUBTEL sync. enabled=%s", enabled)
            repository.syncSubtelRules(enabled = enabled, apiClient = apiClient)
            Timber.tag(TAG).d("Finished SUBTEL sync.")
            Result.success()
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Error syncing SUBTEL list")
            Result.retry()
        }
    }

    companion object {
        const val KEY_SUBTEL_ENABLED = "key_subtel_enabled"
        private const val TAG = "Corta.SyncWorker"

        fun inputData(enabled: Boolean): Data {
            return Data.Builder()
                .putBoolean(KEY_SUBTEL_ENABLED, enabled)
                .build()
        }
    }
}
