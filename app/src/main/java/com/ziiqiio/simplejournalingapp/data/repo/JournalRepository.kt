package com.ziiqiio.simplejournalingapp.data.repo

import android.content.Context
import com.ziiqiio.simplejournalingapp.data.db.Draft
import com.ziiqiio.simplejournalingapp.data.db.JournalDao
import com.ziiqiio.simplejournalingapp.data.db.JournalEntry
import com.ziiqiio.simplejournalingapp.data.db.SavedTag
import com.ziiqiio.simplejournalingapp.data.db.SyncStatus
import com.ziiqiio.simplejournalingapp.data.google.JournalFormatter
import com.ziiqiio.simplejournalingapp.data.prefs.AppPreferences
import com.ziiqiio.simplejournalingapp.sync.SyncWorker
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId

class JournalRepository(
    private val context: Context,
    private val dao: JournalDao,
    private val prefs: AppPreferences
) {

    val entries: Flow<List<JournalEntry>> = dao.observeAllEntries()
    val tags: Flow<List<SavedTag>> = dao.observeTags()
    val pendingCount: Flow<Int> = dao.observePendingCount()

    suspend fun saveEntry(
        moodIndex: Int,
        energyLevel: Int,
        tagsCsv: String,
        promptUsed: String?,
        body: String
    ): Long {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val formatted = JournalFormatter.formatDateHeading(now, zone)

        val entry = JournalEntry(
            timestamp = now,
            formattedDate = formatted,
            moodIndex = moodIndex,
            energyLevel = energyLevel,
            tags = tagsCsv,
            promptUsed = promptUsed,
            body = body,
            status = SyncStatus.PENDING,
            syncedAt = null
        )
        val id = dao.insertEntry(entry)

        // Bump tag last-used.
        tagsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { name ->
            dao.upsertTag(SavedTag(name, now))
        }

        dao.clearDraft()
        SyncWorker.enqueue(context)
        return id
    }

    suspend fun dismissEntry(id: Long) {
        dao.deleteEntry(id)
    }

    suspend fun dismissSyncedEntries() {
        dao.deleteSyncedEntries()
    }

    suspend fun getEntry(id: Long): JournalEntry? = dao.getEntry(id)

    suspend fun ensureTag(name: String) {
        if (name.isBlank()) return
        dao.upsertTag(SavedTag(name.trim(), System.currentTimeMillis()))
    }

    suspend fun deleteTag(name: String) {
        if (name.isBlank()) return
        dao.deleteTag(name.trim())
    }

    suspend fun seedDefaultTagsIfNeeded() {
        val existingTags = dao.getTags().map { it.tagName.lowercase() }.toSet()
        val missingDefaults = defaultTags.filter { it.lowercase() !in existingTags }
        if (missingDefaults.isEmpty()) return

        val now = System.currentTimeMillis()
        missingDefaults.forEachIndexed { index, tag ->
            dao.upsertTag(SavedTag(tag, now - index))
        }
    }

    // ----- draft -----
    suspend fun saveDraft(draft: Draft) = dao.saveDraft(draft.copy(updatedAt = System.currentTimeMillis()))
    suspend fun getDraft(): Draft? = dao.getDraft()
    suspend fun clearDraft() = dao.clearDraft()

    fun retryNow() = SyncWorker.enqueue(context)

    private companion object {
        val defaultTags = listOf(
            "Gratitude",
            "Idea",
            "Goal",
            "Reflection",
            "Event"
        )
    }
}
