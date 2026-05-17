package com.ziiqiio.simplejournalingapp.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "journal_prefs")

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromPreference(value: String?): ThemeMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SYSTEM
    }
}

class AppPreferences(private val context: Context) {

    private object Keys {
        val ACCOUNT_EMAIL = stringPreferencesKey("account_email")
        val JOURNAL_FOLDER_ID = stringPreferencesKey("journal_folder_id")
        // Cached doc IDs keyed by YYYY-MM, stored as JSON-ish map blob "k1=v1;k2=v2"
        val MONTHLY_DOC_IDS = stringPreferencesKey("monthly_doc_ids")
        val APPEND_DIRECTION = stringPreferencesKey("append_direction") // "newest_bottom" | "newest_top"
        val OVERRIDE_DOC_ID = stringPreferencesKey("override_doc_id") // user-supplied single Doc ID
        val OVERRIDE_DOC_TITLE = stringPreferencesKey("override_doc_title")
        val LAST_SYNC_MESSAGE = stringPreferencesKey("last_sync_message")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val accountEmail: Flow<String?> = context.dataStore.data.map { it[Keys.ACCOUNT_EMAIL] }
    val journalFolderId: Flow<String?> = context.dataStore.data.map { it[Keys.JOURNAL_FOLDER_ID] }
    val appendDirection: Flow<String> = context.dataStore.data.map { it[Keys.APPEND_DIRECTION] ?: "newest_bottom" }
    val overrideDocId: Flow<String?> = context.dataStore.data.map { it[Keys.OVERRIDE_DOC_ID] }
    val overrideDocTitle: Flow<String?> = context.dataStore.data.map { it[Keys.OVERRIDE_DOC_TITLE] }
    val lastSyncMessage: Flow<String?> = context.dataStore.data.map { it[Keys.LAST_SYNC_MESSAGE] }
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { ThemeMode.fromPreference(it[Keys.THEME_MODE]) }

    suspend fun setAccountEmail(email: String?) = context.dataStore.edit { p ->
        if (email == null) p.remove(Keys.ACCOUNT_EMAIL) else p[Keys.ACCOUNT_EMAIL] = email
    }

    suspend fun setJournalFolderId(id: String?) = context.dataStore.edit { p ->
        if (id == null) p.remove(Keys.JOURNAL_FOLDER_ID) else p[Keys.JOURNAL_FOLDER_ID] = id
    }

    suspend fun setAppendDirection(direction: String) = context.dataStore.edit { p ->
        p[Keys.APPEND_DIRECTION] = direction
    }

    suspend fun setOverrideDocId(id: String?) = context.dataStore.edit { p ->
        if (id.isNullOrBlank()) p.remove(Keys.OVERRIDE_DOC_ID) else p[Keys.OVERRIDE_DOC_ID] = id
    }

    suspend fun setOverrideDoc(id: String?, title: String?) = context.dataStore.edit { p ->
        if (id.isNullOrBlank()) {
            p.remove(Keys.OVERRIDE_DOC_ID)
            p.remove(Keys.OVERRIDE_DOC_TITLE)
        } else {
            p[Keys.OVERRIDE_DOC_ID] = id
            if (title.isNullOrBlank()) p.remove(Keys.OVERRIDE_DOC_TITLE) else p[Keys.OVERRIDE_DOC_TITLE] = title
        }
    }

    suspend fun setLastSyncMessage(message: String?) = context.dataStore.edit { p ->
        if (message.isNullOrBlank()) p.remove(Keys.LAST_SYNC_MESSAGE) else p[Keys.LAST_SYNC_MESSAGE] = message
    }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { p ->
        p[Keys.THEME_MODE] = mode.name.lowercase()
    }

    suspend fun getMonthlyDocId(yearMonth: String): String? {
        val prefs = currentPrefs()
        return parseMap(prefs[Keys.MONTHLY_DOC_IDS])[yearMonth]
    }

    suspend fun setMonthlyDocId(yearMonth: String, docId: String) = context.dataStore.edit { p ->
        val map = parseMap(p[Keys.MONTHLY_DOC_IDS]).toMutableMap()
        map[yearMonth] = docId
        p[Keys.MONTHLY_DOC_IDS] = serializeMap(map)
    }

    private suspend fun currentPrefs(): Preferences = context.dataStore.data.first()

    private fun parseMap(blob: String?): Map<String, String> {
        if (blob.isNullOrBlank()) return emptyMap()
        return blob.split(';').mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) null else part.substring(0, eq) to part.substring(eq + 1)
        }.toMap()
    }

    private fun serializeMap(map: Map<String, String>): String =
        map.entries.joinToString(";") { "${it.key}=${it.value}" }
}
