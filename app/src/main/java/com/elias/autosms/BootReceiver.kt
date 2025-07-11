package com.elias.autosms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.AlarmManager
import android.app.PendingIntent
import java.util.Calendar

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Retrieve saved phone, message, and time from SharedPreferences
            val prefs = context.getSharedPreferences("sms_prefs", Context.MODE_PRIVATE)
            val phone = prefs.getString("phone", null) ?: return
            val message = prefs.getString("message", null) ?: return
            val hour = prefs.getInt("hour", 0)
            val minute = prefs.getInt("minute", 0)

            // Recreate the same calendar and PendingIntent
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
            }
            val sendIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = "com.elias.autosms.ACTION_SEND_SMS"
                putExtra("phone", phone)
                putExtra("message", message)
            }
            val pending = PendingIntent.getBroadcast(
                context, 0, sendIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmMgr.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
        }
    }
}