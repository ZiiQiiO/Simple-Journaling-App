package com.ziiqiio.simplejournalingapp

import android.content.Context
import com.ziiqiio.simplejournalingapp.data.db.JournalDatabase
import com.ziiqiio.simplejournalingapp.data.google.GoogleAuthManager
import com.ziiqiio.simplejournalingapp.data.google.GoogleDocsRepository
import com.ziiqiio.simplejournalingapp.data.prefs.AppPreferences
import com.ziiqiio.simplejournalingapp.data.prompts.PromptRepository
import com.ziiqiio.simplejournalingapp.data.repo.JournalRepository

/**
 * Manual DI container — one instance per Application. Plenty for a
 * single-user app; avoids dragging in Hilt/Koin.
 */
class AppContainer(val context: Context) {
    val db: JournalDatabase by lazy { JournalDatabase.get(context) }
    val prefs: AppPreferences by lazy { AppPreferences(context) }
    val auth: GoogleAuthManager by lazy { GoogleAuthManager(context) }
    val docsRepository: GoogleDocsRepository by lazy {
        GoogleDocsRepository(context, auth, prefs)
    }
    val promptRepository: PromptRepository by lazy {
        PromptRepository(context, db.journalDao())
    }
    val journalRepository: JournalRepository by lazy {
        JournalRepository(context, db.journalDao(), prefs)
    }
}
