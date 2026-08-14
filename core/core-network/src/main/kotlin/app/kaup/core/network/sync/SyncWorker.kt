package app.kaup.core.network.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.kaup.shared.domain.sync.SyncBackend
import app.kaup.shared.models.sync.SyncResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncBackend: SyncBackend
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // TODO(#175): read the batch from sync_queue and persist the last
            // pull watermark. The table does not exist yet, so this pushes an
            // empty batch and pulls from the epoch.
            val result = syncBackend.pushRecords(emptyList())
            syncBackend.pullUpdates(Instant.fromEpochMilliseconds(0))

            when (result) {
                is SyncResult.Success -> Result.success()
                // Tier 0 always reports failure and that is correct behaviour,
                // not an error to retry or notify about (#221). Retrying an
                // unconfigured backend would burn the retry budget and fire the
                // sync failure notification on a store that never opted into
                // syncing.
                is SyncResult.Failure -> if (syncBackend.isConfigured()) Result.retry() else Result.success()
                is SyncResult.PartialSuccess -> Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
