package com.ziiqiio.simplejournalingapp.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.ziiqiio.simplejournalingapp.JournalApp
import com.ziiqiio.simplejournalingapp.data.db.SyncStatus
import java.time.ZoneId

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as JournalApp
        val dao = app.container.db.journalDao()
        val docs = app.container.docsRepository
        val prefs = app.container.prefs
        val zone = ZoneId.systemDefault()

        val pending = dao.pendingEntries()
        if (pending.isEmpty()) {
            prefs.setLastSyncMessage("No pending entries to sync.")
            return Result.success()
        }

        // Need to be signed in to sync; if not, leave entries pending.
        if (app.container.auth.currentAccount() == null) {
            prefs.setLastSyncMessage("Sync paused: sign in with Google again.")
            return Result.retry()
        }

        var anyFailed = false
        var syncedCount = 0
        var lastError: Throwable? = null
        for (entry in pending) {
            try {
                val targetId = docs.resolveTargetDocId(entry, zone)
                docs.appendEntry(targetId, entry, zone)
                dao.updateEntry(
                    entry.copy(
                        status = SyncStatus.SYNCED,
                        syncedAt = System.currentTimeMillis(),
                        syncedDocId = targetId
                    )
                )
                syncedCount += 1
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to sync journal entry ${entry.id}", t)
                anyFailed = true
                lastError = t
                dao.updateEntry(entry.copy(status = SyncStatus.PENDING))
            }
        }
        return if (anyFailed) {
            val error = lastError
            val errorText = error?.friendlySyncMessage()
                ?: error?.message
                ?: error?.javaClass?.simpleName
                ?: "Unknown error"
            prefs.setLastSyncMessage("Synced $syncedCount of ${pending.size}; failed: $errorText")
            if (error.isGoogleApiSetupError()) Result.failure() else Result.retry()
        } else {
            prefs.setLastSyncMessage("Synced $syncedCount journal entr${if (syncedCount == 1) "y" else "ies"}.")
            Result.success()
        }
    }

    private fun Throwable?.friendlySyncMessage(): String? {
        val googleError = this as? GoogleJsonResponseException ?: return null
        if (!googleError.isApiDisabledError()) return null

        val serviceName = when {
            googleError.message?.contains("drive.googleapis.com", ignoreCase = true) == true ->
                "Google Drive API"
            googleError.message?.contains("docs.googleapis.com", ignoreCase = true) == true ->
                "Google Docs API"
            else -> "Google Drive and Docs APIs"
        }
        return "$serviceName is disabled for this app's Google Cloud project. Enable it in Google Cloud Console, wait a few minutes, then retry sync."
    }

    private fun Throwable?.isGoogleApiSetupError(): Boolean =
        (this as? GoogleJsonResponseException)?.isApiDisabledError() == true

    private fun GoogleJsonResponseException.isApiDisabledError(): Boolean {
        if (statusCode != 403) return false
        val text = message.orEmpty()
        return text.contains("SERVICE_DISABLED", ignoreCase = true) ||
            text.contains("accessNotConfigured", ignoreCase = true) ||
            text.contains("has not been used", ignoreCase = true) ||
            text.contains("is disabled", ignoreCase = true)
    }

    companion object {
        private const val TAG = "SyncWorker"
        const val UNIQUE_NAME = "journal_sync"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    java.time.Duration.ofSeconds(30)
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
