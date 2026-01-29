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

        // We use the startDate (user selected date) and configured time as the anchor
        // to prevent schedule drift and support future scheduling.
        val anchor = Calendar.getInstance()
        // Use startDate for the date part
        val paramsCal = Calendar.getInstance()
        paramsCal.timeInMillis = schedule.startDate

        anchor.set(Calendar.YEAR, paramsCal.get(Calendar.YEAR))
        anchor.set(Calendar.MONTH, paramsCal.get(Calendar.MONTH))
        anchor.set(Calendar.DAY_OF_MONTH, paramsCal.get(Calendar.DAY_OF_MONTH))

        // Use hour/minute from schedule
        anchor.set(Calendar.HOUR_OF_DAY, schedule.hour)
        anchor.set(Calendar.MINUTE, schedule.minute)
        anchor.set(Calendar.SECOND, 0)
        anchor.set(Calendar.MILLISECOND, 0)

        // Capture the original day of month for monthly calculations to prevent drift
        val originalDayOfMonth = anchor.get(Calendar.DAY_OF_MONTH)

        // Optimization: Fast-forward 'anchor' if it is far in the past
        if (anchor.timeInMillis < now.timeInMillis) {
            val diffMillis = now.timeInMillis - anchor.timeInMillis

            // Heuristic jumps based on frequency to get close to 'now'
            // We under-shoot slightly to let the exact loop handle the final precision and edge
            // cases (DST, month lengths)
            if (schedule.isRecurring) {
                when (schedule.frequency) {
                    SmsSchedule.FREQUENCY_HOURLY -> {
                        val hours = diffMillis / 3600000L
                        if (hours > 1) anchor.add(Calendar.HOUR_OF_DAY, (hours - 1).toInt())
                    }
                    SmsSchedule.FREQUENCY_DAILY -> {
                        val days = diffMillis / 86400000L
                        if (days > 1) anchor.add(Calendar.DAY_OF_YEAR, (days - 1).toInt())
                    }
                    SmsSchedule.FREQUENCY_WEEKLY -> {
                        val weeks = diffMillis / (86400000L * 7)
                        if (weeks > 1) anchor.add(Calendar.WEEK_OF_YEAR, (weeks - 1).toInt())
                    }
                    SmsSchedule.FREQUENCY_MONTHLY -> {
                        // Month calculations are tricky with lengths, we estimate 28 days to be
                        // safe
                        val approxMonths = diffMillis / (86400000L * 28)
                        if (approxMonths > 1) {
                            anchor.add(Calendar.MONTH, (approxMonths - 1).toInt())
                            // Re-apply day-of-month fix after jump
                            val maxDay = anchor.getActualMaximum(Calendar.DAY_OF_MONTH)
                            val targetDay =
                                    if (originalDayOfMonth > maxDay) maxDay else originalDayOfMonth
                            anchor.set(Calendar.DAY_OF_MONTH, targetDay)
                        }
                    }
                    SmsSchedule.FREQUENCY_CUSTOM -> {
                        val p = if (schedule.period < 1) 1 else schedule.period
                        if (schedule.periodUnit == SmsSchedule.UNIT_HOURS) {
                            val periodMs = p * 3600000L
                            val cycles = diffMillis / periodMs
                            if (cycles > 1)
                                    anchor.add(Calendar.HOUR_OF_DAY, ((cycles - 1) * p).toInt())
                        } else {
                            val periodMs = p * 86400000L
                            val cycles = diffMillis / periodMs
                            if (cycles > 1)
                                    anchor.add(Calendar.DAY_OF_YEAR, ((cycles - 1) * p).toInt())
                        }
                    }
                }
            }
        }

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
