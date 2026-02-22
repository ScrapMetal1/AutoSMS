package com.elias.autosms.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SmsSchedule::class], version = 8, exportSchema = false)
abstract class SmsScheduleDatabase : RoomDatabase() {

    abstract fun smsScheduleDao(): SmsScheduleDao

    companion object {
        @Volatile private var INSTANCE: SmsScheduleDatabase? = null

        // adds the "send late if missed" columns — existing schedules default to on with 2-hour cutoff
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sms_schedules ADD COLUMN sendIfMissed INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE sms_schedules ADD COLUMN missedCutoffMinutes INTEGER NOT NULL DEFAULT ${SmsSchedule.DEFAULT_CUTOFF_MINUTES}")
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
                                        .addMigrations(MIGRATION_7_8)
                                        .fallbackToDestructiveMigration()
                                        .build()
                        INSTANCE = instance
                        instance
                    }
        }
    }
}
