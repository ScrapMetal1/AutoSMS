package com.elias.autosms.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.WorkManager
import com.elias.autosms.data.SmsSchedule
import com.elias.autosms.receiver.AlarmReceiver
import java.util.*

class SmsScheduleManager(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val alarmPrefs = context.getSharedPreferences("sms_alarm_times", Context.MODE_PRIVATE)

    /**
     * Schedules the next send using setAlarmClock().
     *
     * We tried setExactAndAllowWhileIdle() but that still dies after ~10 days: Android
     * moves the app to the "Restricted" standby bucket if the user hasn't opened it,
     * and restricted apps can't fire exact alarms at all. setAlarmClock() is the only
     * alarm type that's fully exempt from Doze, standby buckets, and battery restrictions.
     * The trade-off is a small alarm icon in the status bar, which is fine for an SMS
     * scheduling app — it tells the user something is scheduled.
     */
    fun scheduleRepeatingWork(
            schedule: SmsSchedule,
            isRescheduleForNextInterval: Boolean = false,
            allowCatchUp: Boolean = false
    ) {
        // figure out when the next occurrence should be
        val initialDelay =
                if (!isRescheduleForNextInterval &&
                                !allowCatchUp &&
                                (schedule.frequency == SmsSchedule.FREQUENCY_HOURLY ||
                                        (schedule.frequency == SmsSchedule.FREQUENCY_CUSTOM &&
                                                schedule.periodUnit == SmsSchedule.UNIT_HOURS))
                ) {
                    // when the user first sets an hourly schedule we wait for the next "clean" daily time, not mid-hour
                    try {
                        calculateInitialDelay(
                                schedule.copy(frequency = SmsSchedule.FREQUENCY_DAILY)
                        )
                    } catch (e: Exception) {
                        calculateInitialDelay(schedule)
                    }
                } else {
                    calculateInitialDelay(schedule)
                }

        val triggerAtMillis = System.currentTimeMillis() + initialDelay

        // this is what AlarmReceiver gets when the alarm goes off
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("scheduleId", schedule.id)
            putExtra("contactName", schedule.contactName)
            putExtra("phoneNumber", schedule.phoneNumber)
            putExtra("scheduled_time", triggerAtMillis)
        }

        val pendingIntent = PendingIntent.getBroadcast(
                context,
                schedule.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "show" intent — tapping the alarm icon in the status bar opens the app
        val showIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, com.elias.autosms.ui.MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // setAlarmClock is the last resort option if nothing elsee works: it is  exempt from Doze, standby buckets, and all battery restrictions. setExactAndAllowWhileIdle dies after ~10 days in the restricted bucket; this doesn't.
        
        val alarmInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent)
        alarmManager.setAlarmClock(alarmInfo, pendingIntent)

        alarmPrefs.edit().putLong("alarm_${schedule.id}", triggerAtMillis).apply()

        Log.d(
                "SmsScheduleManager",
                "Alarm set for ${schedule.contactName} at ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(triggerAtMillis))} (Reschedule: $isRescheduleForNextInterval)"
        )
    }

    // kill the alarm and any WorkManager job that might already be queued for this schedule
    fun cancelWork(scheduleId: Long) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
                context,
                scheduleId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)

        // in case the alarm already fired and enqueued work, cancel that too
        WorkManager.getInstance(context).cancelUniqueWork("sms_work_$scheduleId")

        alarmPrefs.edit().remove("alarm_$scheduleId").apply()

        Log.d("SmsScheduleManager", "Cancelled alarm + work for schedule ID: $scheduleId")
    }

    fun getStoredAlarmTime(scheduleId: Long): Long {
        return alarmPrefs.getLong("alarm_$scheduleId", 0)
    }

    // how long from now until the next time this schedule should fire
    fun calculateInitialDelay(schedule: SmsSchedule): Long {
        val now = Calendar.getInstance()

        // start from the user's chosen start date + time, then step forward until we're past "now"
        val anchor = Calendar.getInstance()
        val paramsCal = Calendar.getInstance()
        paramsCal.timeInMillis = schedule.startDate

        anchor.set(Calendar.YEAR, paramsCal.get(Calendar.YEAR))
        anchor.set(Calendar.MONTH, paramsCal.get(Calendar.MONTH))
        anchor.set(Calendar.DAY_OF_MONTH, paramsCal.get(Calendar.DAY_OF_MONTH))
        anchor.set(Calendar.HOUR_OF_DAY, schedule.hour)
        anchor.set(Calendar.MINUTE, schedule.minute)
        anchor.set(Calendar.SECOND, 0)
        anchor.set(Calendar.MILLISECOND, 0)

        // need this for monthly — e.g. 31st in Feb becomes 28th
        val originalDayOfMonth = anchor.get(Calendar.DAY_OF_MONTH)

        // if the anchor is way in the past, jump forward in big steps so we don't loop forever
        if (anchor.timeInMillis < now.timeInMillis) {
            val diffMillis = now.timeInMillis - anchor.timeInMillis

            // under-shoot a bit so the loop below can handle DST and month-length edge cases
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
                        // months have different lengths so we approximate with 28 days
                        val approxMonths = diffMillis / (86400000L * 28)
                        if (approxMonths > 1) {
                            anchor.add(Calendar.MONTH, (approxMonths - 1).toInt())
                            // then fix the day so we don't end up with invalid 31st etc
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

        // step forward until we're past "now" — that's our next run time
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
                        anchor.add(Calendar.MONTH, 1)
                        // clamp day so we don't end up on the 31st in February
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
                    else -> anchor.add(Calendar.DAY_OF_YEAR, 1)
                }
            } else {
                // one-off: if we're past the time today, next run is tomorrow
                anchor.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        return anchor.timeInMillis - now.timeInMillis
    }
}
