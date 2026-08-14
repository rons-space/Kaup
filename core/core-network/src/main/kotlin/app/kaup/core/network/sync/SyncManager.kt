package app.kaup.core.network.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.kaup.shared.domain.sync.SyncBackend
import app.kaup.shared.models.sync.SyncResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class ProcessingMode {
    STANDALONE, ASSISTED, SERVER_FIRST
}

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncBackend: SyncBackend
) {
    private val workManager = WorkManager.getInstance(context)
    private val syncWorkName = "KaupPeriodicSync"

    fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            syncWorkName,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun triggerImmediateSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        workManager.enqueue(request)
    }

    /**
     * Server-First processing: the caller waits for the server to acknowledge
     * before treating the write as confirmed. Returns false when the backend
     * is not configured, so the caller falls back to a local write rather than
     * reporting a confirmation that never happened (ADR-010, #220).
     */
    suspend fun pushNowServerFirst(): Boolean {
        if (!syncBackend.isConfigured()) return false
        // TODO(#175): pass the pending batch once sync_queue exists.
        return try {
            syncBackend.pushRecords(emptyList()) is SyncResult.Success
        } catch (e: Exception) {
            false
        }
    }
}
