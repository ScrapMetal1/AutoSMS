package com.elias.autosms

import android.app.Application
import com.elias.autosms.data.SmsScheduleDatabase

class AutoSMSApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize the database to warm it up
        SmsScheduleDatabase.getDatabase(this)
    }
}
