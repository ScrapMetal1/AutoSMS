package com.elias.autosms.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SmsSchedule::class],
    version = 2,
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

        fun getDatabase(context: Context): SmsScheduleDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SmsScheduleDatabase::class.java,
                    "sms_schedule_database"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}