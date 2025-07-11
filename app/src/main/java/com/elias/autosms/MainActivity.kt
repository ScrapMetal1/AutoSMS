package com.elias.autosms

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TimePicker
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

class MainActivity : AppCompatActivity() {
    private lateinit var etPhone: EditText
    private lateinit var etMessage: EditText
    private lateinit var timePicker: TimePicker
    private lateinit var btnSchedule: Button

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Log.w("MainActivity", "Notification permission denied")
        }
    }

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) scheduleSms()
        else Log.w("MainActivity", "SMS permission denied")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etPhone = findViewById(R.id.etPhoneNumber)
        etMessage = findViewById(R.id.etMessage)
        timePicker = findViewById(R.id.timePicker)
        btnSchedule = findViewById(R.id.btnSchedule)

        btnSchedule.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
                smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
            } else {
                scheduleSms()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun scheduleSms() {
        val phone = etPhone.text.toString()
        val message = etMessage.text.toString()

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, timePicker.hour)
            set(Calendar.MINUTE, timePicker.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        val triggerTime = calendar.timeInMillis

        val intent = Intent(this, AlarmReceiver::class.java).apply {
            action = "com.elias.autosms.ACTION_SEND_SMS"
            putExtra("phone", phone)
            putExtra("message", message)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmMgr = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmMgr.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent
        )

//        val alarmMgr = getSystemService(Context.ALARM_SERVICE) as AlarmManager
//        alarmMgr.setRepeating(
//            AlarmManager.RTC_WAKEUP,
//            triggerTime,
//            AlarmManager.INTERVAL_DAY,
//            pendingIntent
//        )

        Log.d("MainActivity", "Alarm scheduled for: ${calendar.time}")

        // Optional debug notification
        val channelId = "debug_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Debug", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Alarm Scheduled")
            .setContentText("SMS set for: ${calendar.time}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(1001, notification)
    }

}
