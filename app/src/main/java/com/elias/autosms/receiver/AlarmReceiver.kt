package com.elias.autosms.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import com.elias.autosms.worker.SmsWorker

/**
 * Gets woken up by AlarmManager when it's time to send an SMS. We don't send from here
 * — we hand off to WorkManager so we still get retries and the same SmsWorker logic.
 * The alarm is just for when to run; the actual send + reschedule-next-alarm happens in the worker.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getLongExtra("scheduleId", -1)
        if (scheduleId == -1L) {
            Log.e("AlarmReceiver", "Alarm fired with no scheduleId, ignoring")
            return
        }

        val contactName = intent.getStringExtra("contactName") ?: ""
        val phoneNumber = intent.getStringExtra("phoneNumber") ?: ""
        val scheduledTime = intent.getLongExtra("scheduled_time", 0)

        Log.d("AlarmReceiver", "Alarm fired for schedule $scheduleId ($contactName)")

        // no delay — the alarm already did the timing; we just kick off the send job.
        val inputData = workDataOf(
                "scheduleId" to scheduleId,
                "contactName" to contactName,
                "phoneNumber" to phoneNumber,
                "scheduled_time" to scheduledTime,
        )

        val workRequest = OneTimeWorkRequestBuilder<SmsWorker>()
                .setInputData(inputData)
                .build()

        val workName = "sms_work_$scheduleId"
        androidx.work.WorkManager.getInstance(context)
                .enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, workRequest)
    }
}
