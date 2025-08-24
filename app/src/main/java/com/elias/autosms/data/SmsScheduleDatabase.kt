package com.elias.autosms.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SmsSchedule::class],
    version = 4,
    exportSchema = false
)
abstract class SmsScheduleDatabase : RoomDatabase() {

    abstract fun smsScheduleDao(): SmsScheduleDao

    companion object {
        @Volatile
        private var INSTANCE: SmsScheduleDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new columns for AI message support
                database.execSQL("ALTER TABLE sms_schedules ADD COLUMN isAiGenerated INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE sms_schedules ADD COLUMN messageType TEXT NOT NULL DEFAULT 'custom'")
                database.execSQL("ALTER TABLE sms_schedules ADD COLUMN messageContext TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add createdAt column required by the entity if it doesn't exist
                val cursor = database.query("PRAGMA table_info(`sms_schedules`)")
                var hasCreatedAt = false
                try {
                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (nameIndex >= 0 && cursor.getString(nameIndex) == "createdAt") {
                            hasCreatedAt = true
                            break
                        }
                    }
                } finally {
                    cursor.close()
                }

                if (!hasCreatedAt) {
                    database.execSQL("ALTER TABLE sms_schedules ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add regenerateDaily column (defaults to 1/true)
                val cursor = database.query("PRAGMA table_info(`sms_schedules`)")
                var hasRegenerateDaily = false
                try {
                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (nameIndex >= 0 && cursor.getString(nameIndex) == "regenerateDaily") {
                            hasRegenerateDaily = true
                            break
                        }
                    }
                } finally {
                    cursor.close()
                }
                if (!hasRegenerateDaily) {
                    database.execSQL("ALTER TABLE sms_schedules ADD COLUMN regenerateDaily INTEGER NOT NULL DEFAULT 1")
                }
            }
        }

        fun getDatabase(context: Context): SmsScheduleDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SmsScheduleDatabase::class.java,
                    "sms_schedule_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}