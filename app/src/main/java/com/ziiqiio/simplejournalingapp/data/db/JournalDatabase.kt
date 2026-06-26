package com.ziiqiio.simplejournalingapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

class SyncStatusConverters {
    @TypeConverter fun fromStatus(s: SyncStatus): String = s.name
    @TypeConverter fun toStatus(s: String): SyncStatus = SyncStatus.valueOf(s)
}

@Database(
    entities = [JournalEntry::class, SavedTag::class, RecentPrompt::class, Draft::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(SyncStatusConverters::class)
abstract class JournalDatabase : RoomDatabase() {
    abstract fun journalDao(): JournalDao

    companion object {
        @Volatile private var INSTANCE: JournalDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE journal_entries ADD COLUMN syncedDocId TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE journal_entries ADD COLUMN syncedTabId TEXT")
            }
        }

        fun get(context: Context): JournalDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                JournalDatabase::class.java,
                "journal.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { INSTANCE = it }
        }
    }
}
