package com.elias.autosms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "onReceive triggered")

        showNotification(context, "Alarm Triggered", "Intent received")

        if (intent.action == "com.elias.autosms.ACTION_SEND_SMS") {
            val phone = intent.getStringExtra("phone") ?: return
            val message = intent.getStringExtra("message") ?: return

            SmsManager.getDefault().sendTextMessage(phone, null, message, null, null)
            Log.d("AlarmReceiver", "SMS sent to $phone")

            showNotification(context, "SMS Sent", "Message sent to $phone")
        } else {
            Log.e("AlarmReceiver", "Unexpected intent action: ${intent.action}")
        }
    }

    private fun showNotification(context: Context, title: String, content: String) {
        val channelId = "alarm_channel"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Alarm Events", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
