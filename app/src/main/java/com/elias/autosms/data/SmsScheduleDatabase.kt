package com.elias.autosms.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
        entities = [
            SmsSchedule::class,
            AutoReplyRule::class,
            AutoReplyHistory::class,
            ContextDocument::class
        ],
        version = 10,
        exportSchema = false
)
abstract class SmsScheduleDatabase : RoomDatabase() {

    abstract fun smsScheduleDao(): SmsScheduleDao
    abstract fun autoReplyRuleDao(): AutoReplyRuleDao
    abstract fun autoReplyHistoryDao(): AutoReplyHistoryDao
    abstract fun contextDocumentDao(): ContextDocumentDao

    companion object {
        @Volatile private var INSTANCE: SmsScheduleDatabase? = null

        // adds the "send late if missed" columns — existing schedules default to on with 2-hour cutoff
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sms_schedules ADD COLUMN sendIfMissed INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE sms_schedules ADD COLUMN missedCutoffMinutes INTEGER NOT NULL DEFAULT ${SmsSchedule.DEFAULT_CUTOFF_MINUTES}")
            }
        }

        // Adds AI auto-reply tables. Empty on first creation, populated when
        // the user starts adding rules from the new premium feature.
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                        "CREATE TABLE IF NOT EXISTS auto_reply_rules (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "displayName TEXT NOT NULL, " +
                                "phoneNumber TEXT NOT NULL, " +
                                "systemPrompt TEXT NOT NULL, " +
                                "isEnabled INTEGER NOT NULL DEFAULT 1, " +
                                "createdAt INTEGER NOT NULL)"
                )
                db.execSQL(
                        "CREATE TABLE IF NOT EXISTS auto_reply_history (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "ruleId INTEGER, " +
                                "phoneNumber TEXT NOT NULL, " +
                                "inboundText TEXT NOT NULL, " +
                                "replyText TEXT, " +
                                "status TEXT NOT NULL, " +
                                "errorMessage TEXT, " +
                                "timestamp INTEGER NOT NULL)"
                )
            }
        }

        // Adds the context_documents table for AI prompt grounding.
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                        "CREATE TABLE IF NOT EXISTS context_documents (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "title TEXT NOT NULL, " +
                                "content TEXT NOT NULL, " +
                                "isEnabled INTEGER NOT NULL DEFAULT 1, " +
                                "createdAt INTEGER NOT NULL, " +
                                "updatedAt INTEGER NOT NULL)"
                )
            }
        }

        fun getDatabase(context: Context): SmsScheduleDatabase {
            return INSTANCE
                    ?: synchronized(this) {
                        val instance =
                                Room.databaseBuilder(
                                                context.applicationContext,
                                                SmsScheduleDatabase::class.java,
                                                "sms_schedule_database"
                                        )
                                        .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                                        .fallbackToDestructiveMigration()
                                        .build()
                        INSTANCE = instance
                        instance
                    }
        }
    }
}
