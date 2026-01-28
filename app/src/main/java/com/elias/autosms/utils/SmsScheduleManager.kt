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
    fun scheduleRepeatingWork(schedule: SmsSchedule, isRescheduleForNextInterval: Boolean = false) {
        val workName = "sms_work_${schedule.id}"

        // Calculate initial delay until next occurrence
        val initialDelay =
                if (!isRescheduleForNextInterval &&
                                (schedule.frequency == SmsSchedule.FREQUENCY_HOURLY ||
                                        (schedule.frequency == SmsSchedule.FREQUENCY_CUSTOM &&
                                                schedule.periodUnit == SmsSchedule.UNIT_HOURS))
                ) {
                    // Manual/UI Trigger for High-Frequency (Hourly) Schedule.
                    // Constraint: "Start repeating at the scheduled time" relative to Day.
                    // We DO NOT want to jump into the middle of an hourly sequence (e.g. 10am,
                    // 11am)
                    // if we missed the start. We want to wait for the next "clean" Daily start
                    // time.
                    // We temporarily treat it as DAILY to find the anchor point.
                    calculateInitialDelay(schedule.copy(frequency = SmsSchedule.FREQUENCY_DAILY))
                } else {
                    // Standard Logic: Worker logic (continue sequence) or Low-Frequency
                    // (Daily/Weekly)
                    calculateInitialDelay(schedule)
                }

        val scheduledTime = System.currentTimeMillis() + initialDelay

        // Create input data for the worker
        val inputData =
                workDataOf(
                        "scheduleId" to schedule.id,
                        "contactName" to schedule.contactName,
                        "phoneNumber" to schedule.phoneNumber,
                        "message" to schedule.message,
                        "scheduled_time" to scheduledTime,
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
                "Scheduled work for ${schedule.contactName} at ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(scheduledTime))} (Reschedule: $isRescheduleForNextInterval)"
        )
    }

    // Cancels a scheduled work by ID
    fun cancelWork(scheduleId: Long) {
        val workName = "sms_work_$scheduleId"
        workManager.cancelUniqueWork(workName)
        Log.d("SmsScheduleManager", "Cancelled work for schedule ID: $scheduleId")
    }

    // Calculates delay until the next occurrence of the scheduled time
    fun calculateInitialDelay(schedule: SmsSchedule): Long {
        val now = Calendar.getInstance()

        // We use the original creation date (and configured time) as the anchor
        // to prevent schedule drift (e.g. keeping "Mondays" as Mondays, or "XX:00" as "XX:00").
        val anchor = Calendar.getInstance()
        anchor.timeInMillis = schedule.createdAt
        anchor.set(Calendar.HOUR_OF_DAY, schedule.hour)
        anchor.set(Calendar.MINUTE, schedule.minute)
        anchor.set(Calendar.SECOND, 0)
        anchor.set(Calendar.MILLISECOND, 0)

        // Capture the original day of month for monthly calculations to prevent drift
        // (e.g. Jan 31 -> Feb 28 -> Mar 28 avoidance)
        val originalDayOfMonth = anchor.get(Calendar.DAY_OF_MONTH)

        // Iterate until we find the NEXT valid occurrence after 'now'
        while (anchor.timeInMillis <= now.timeInMillis) {
            if (schedule.isRecurring) {
                when (schedule.frequency) {
                    SmsSchedule.FREQUENCY_HOURLY -> {
                        anchor.add(Calendar.HOUR_OF_DAY, 1)
                    }
                    SmsSchedule.FREQUENCY_DAILY -> {
                        anchor.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    SmsSchedule.FREQUENCY_WEEKLY -> {
                        anchor.add(Calendar.DAY_OF_YEAR, 7)
                    }
                    SmsSchedule.FREQUENCY_MONTHLY -> {
                        // Add month(s)
                        anchor.add(Calendar.MONTH, 1)

                        // Handle Day-of-Month Drift (e.g. 31st -> 28th -> 31st)
                        val maxDay = anchor.getActualMaximum(Calendar.DAY_OF_MONTH)
                        val targetDay =
                                if (originalDayOfMonth > maxDay) maxDay else originalDayOfMonth
                        anchor.set(Calendar.DAY_OF_MONTH, targetDay)
                    }
                    SmsSchedule.FREQUENCY_CUSTOM -> {
                        val p = if (schedule.period < 1) 1 else schedule.period
                        if (schedule.periodUnit == SmsSchedule.UNIT_HOURS) {
                            anchor.add(Calendar.HOUR_OF_DAY, p)
                        } else {
                            anchor.add(Calendar.DAY_OF_YEAR, p)
                        }
                    }
                    else -> anchor.add(Calendar.DAY_OF_YEAR, 1) // Default to Daily
                }
            } else {
                // Non-recurring: Treat as "Next Occurrence of this Time" (Daily behavior)
                // If the time has passed today, we move to tomorrow.
                anchor.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        return anchor.timeInMillis - now.timeInMillis
    }
}
