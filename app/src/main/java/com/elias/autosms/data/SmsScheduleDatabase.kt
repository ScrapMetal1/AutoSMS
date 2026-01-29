package com.elias.autosms.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SmsSchedule::class], version = 7, exportSchema = false)
abstract class SmsScheduleDatabase : RoomDatabase() {

    abstract fun smsScheduleDao(): SmsScheduleDao

    companion object {
        @Volatile private var INSTANCE: SmsScheduleDatabase? = null

        fun getDatabase(context: Context): SmsScheduleDatabase {
            return INSTANCE
                    ?: synchronized(this) {
                        val instance =
                                Room.databaseBuilder(
                                                context.applicationContext,
                                                SmsScheduleDatabase::class.java,
                                                "sms_schedule_database"
                                        )
                                        .fallbackToDestructiveMigration()
                                        .build()
                        INSTANCE = instance
                        instance
                    }
        }
    }
}
