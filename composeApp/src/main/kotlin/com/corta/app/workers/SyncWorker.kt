package com.corta.app.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.corta.data.network.SubtelApiClient
import com.corta.domain.CortaRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val repository: CortaRepository by inject()
    private val apiClient = SubtelApiClient()

    override suspend fun doWork(): Result {
        val enabled = inputData.getBoolean(KEY_SUBTEL_ENABLED, true)
        return try {
            Log.d("SyncWorker", "Starting SUBTEL sync. enabled=$enabled")
            repository.syncSubtelRules(enabled = enabled, apiClient = apiClient)
            Log.d("SyncWorker", "Finished SUBTEL sync.")
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error syncing SUBTEL list", e)
            Result.retry()
        }
    }

    companion object {
        const val KEY_SUBTEL_ENABLED = "key_subtel_enabled"

        fun inputData(enabled: Boolean): Data {
            return Data.Builder()
                .putBoolean(KEY_SUBTEL_ENABLED, enabled)
                .build()
        }
    }
}
