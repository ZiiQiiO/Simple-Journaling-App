package com.ziiqiio.simplejournalingapp.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SyncStatus { PENDING, SYNCED, FAILED }

@Entity(tableName = "journal_entries")
data class JournalEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val formattedDate: String,
    val moodIndex: Int,
    val energyLevel: Int,
    val tags: String,
    val promptUsed: String?,
    val body: String,
    val status: SyncStatus = SyncStatus.PENDING,
    val syncedAt: Long? = null,
    val docMonthKey: String? = null,
    val syncedDocId: String? = null,
    val syncedTabId: String? = null
)

@Entity(tableName = "saved_tags")
data class SavedTag(
    @PrimaryKey val tagName: String,
    val lastUsed: Long
)

@Entity(tableName = "recent_prompts")
data class RecentPrompt(
    @PrimaryKey val promptId: Int,
    val usedAt: Long
)

@Entity(tableName = "draft")
data class Draft(
    @PrimaryKey val id: Int = 0,
    val moodIndex: Int = 2,
    val energyLevel: Int = 3,
    val tags: String = "",
    val promptText: String? = null,
    val body: String = "",
    val updatedAt: Long = 0L
)
