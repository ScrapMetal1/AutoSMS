package com.elias.autosms.utils

import android.content.Context
import android.util.Log
import androidx.work.*
import com.elias.autosms.data.SmsSchedule
import com.elias.autosms.worker.SmsWorker
import java.util.*
import java.util.concurrent.TimeUnit

class SmsScheduleManager(private val context: Context) {

    private val workManager = WorkManager.getInstance(context)

    // Schedules a daily recurring SMS work request
    fun scheduleRepeatingWork(schedule: SmsSchedule) {
        val workName = "sms_work_${schedule.id}"

        // Calculate initial delay until next occurrence
        val initialDelay = calculateInitialDelay(schedule.hour, schedule.minute)

        // Create input data for the worker
        val inputData =
                workDataOf(
                        "scheduleId" to schedule.id,
                        "contactName" to schedule.contactName,
                        "phoneNumber" to schedule.phoneNumber,
                        "message" to schedule.message,
                        "hour" to schedule.hour,
                        "minute" to schedule.minute
                )

        // Create one-time work request for the next occurrence
        val workRequest =
                OneTimeWorkRequestBuilder<SmsWorker>()
                        .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                        .setInputData(inputData)
                        .setConstraints(
                                Constraints.Builder()
                                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                                        .setRequiresBatteryNotLow(false)
                                        .build()
                        )
                        .build()

        // Schedule the work with REPLACE policy to avoid duplicates
        // We use OneTimeWork instead of PeriodicWork to ensure exact timing
        // and avoid drift. The Worker itself will schedule the next run.
        workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, workRequest)

        Log.d(
                "SmsScheduleManager",
                "Scheduled work for ${schedule.contactName} at ${schedule.getFormattedTime()}"
        )
    }

    // Cancels a scheduled work by ID
    fun cancelWork(scheduleId: Long) {
        val workName = "sms_work_$scheduleId"
        workManager.cancelUniqueWork(workName)
        Log.d("SmsScheduleManager", "Cancelled work for schedule ID: $scheduleId")
    }

    // Calculates delay until the next occurrence of the scheduled time
    private fun calculateInitialDelay(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target =
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

        // If target time has passed today, schedule for tomorrow
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        return target.timeInMillis - now.timeInMillis
    }
}