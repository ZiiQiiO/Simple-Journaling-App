package com.ziiqiio.simplejournalingapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDao {

    // ----- entries -----
    @Insert
    suspend fun insertEntry(entry: JournalEntry): Long

    @Update
    suspend fun updateEntry(entry: JournalEntry)

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("SELECT * FROM journal_entries ORDER BY timestamp DESC")
    fun observeAllEntries(): Flow<List<JournalEntry>>

    @Query("SELECT * FROM journal_entries WHERE status IN ('PENDING','FAILED') ORDER BY timestamp ASC")
    suspend fun pendingEntries(): List<JournalEntry>

    @Query("SELECT COUNT(*) FROM journal_entries WHERE status IN ('PENDING','FAILED')")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    suspend fun getEntry(id: Long): JournalEntry?

    // ----- tags -----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTag(tag: SavedTag)

    @Query("SELECT * FROM saved_tags ORDER BY lastUsed DESC")
    fun observeTags(): Flow<List<SavedTag>>

    @Query("SELECT * FROM saved_tags ORDER BY lastUsed DESC")
    suspend fun getTags(): List<SavedTag>

    // ----- recent prompts -----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentPrompt(rp: RecentPrompt)

    @Query("SELECT promptId FROM recent_prompts ORDER BY usedAt DESC LIMIT :n")
    suspend fun recentPromptIds(n: Int): List<Int>

    @Query("DELETE FROM recent_prompts WHERE promptId NOT IN (SELECT promptId FROM recent_prompts ORDER BY usedAt DESC LIMIT :keep)")
    suspend fun pruneRecentPrompts(keep: Int)

    // ----- draft -----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDraft(draft: Draft)

    @Query("SELECT * FROM draft WHERE id = 0 LIMIT 1")
    suspend fun getDraft(): Draft?

    @Query("DELETE FROM draft")
    suspend fun clearDraft()
}
