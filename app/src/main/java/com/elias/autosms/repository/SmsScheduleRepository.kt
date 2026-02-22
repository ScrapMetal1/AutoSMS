package com.elias.autosms.repository

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import com.elias.autosms.data.SmsSchedule
import com.elias.autosms.data.SmsScheduleDatabase
import com.elias.autosms.utils.SmsScheduleManager
import com.elias.autosms.worker.SmsWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmsScheduleRepository(private val context: Context) {

    // lazy initialization to avoid blocking main thread during startup
    private val database by lazy { SmsScheduleDatabase.getDatabase(context) }
    private val smsScheduleDao by lazy { database.smsScheduleDao() }
    private val smsScheduleManager by lazy { SmsScheduleManager(context) }

    // Retrieve all SMS schedules for display in the UI
    fun getAllSchedules(): LiveData<List<SmsSchedule>> {
        return smsScheduleDao.getAllSchedules()
    }

    // Retrieve enabled schedules for background processing
    suspend fun getEnabledSchedules(): List<SmsSchedule> {
        return withContext(Dispatchers.IO) { smsScheduleDao.getEnabledSchedules() }
    }

    // Retrieve a specific schedule by ID
    suspend fun getScheduleById(id: Long): SmsSchedule? {
        return withContext(Dispatchers.IO) { smsScheduleDao.getScheduleById(id) }
    }

    // insert uses default allowCatchUp = false (strict start time)
    suspend fun insert(schedule: SmsSchedule): Long {
        return withContext(Dispatchers.IO) {
            val id = smsScheduleDao.insert(schedule)
            val newSchedule = schedule.copy(id = id)
            if (newSchedule.isEnabled) {
                smsScheduleManager.scheduleRepeatingWork(newSchedule)
            }
            id
        }
    }

    // Update an existing schedule and reschedule its work
    suspend fun update(schedule: SmsSchedule) {
        withContext(Dispatchers.IO) {
            smsScheduleDao.update(schedule)
            smsScheduleManager.cancelWork(schedule.id)
            if (schedule.isEnabled) {
                // Update: Allow CatchUp so we don't pause for the day just because of an edit
                smsScheduleManager.scheduleRepeatingWork(schedule, allowCatchUp = true)
            }
        }
    }

    // Delete a schedule and cancel its associated work
    suspend fun delete(schedule: SmsSchedule) {
        withContext(Dispatchers.IO) {
            smsScheduleDao.delete(schedule)
            smsScheduleManager.cancelWork(schedule.id)
        }
    }

    // Toggle a schedule's enabled state and update its work
    suspend fun updateEnabled(id: Long, enabled: Boolean) {
        withContext(Dispatchers.IO) {
            smsScheduleDao.updateEnabled(id, enabled)
            smsScheduleManager.cancelWork(id)
            if (enabled) {
                getScheduleById(id)?.let { schedule ->
                    // Toggle On: Allow CatchUp so it starts ASAP
                    smsScheduleManager.scheduleRepeatingWork(schedule, allowCatchUp = true)
                }
            }
        }
    }

    // Reschedule all enabled schedules (e.g., after device reboot).
    // Also checks for alarms that were missed while the phone was off and sends
    // catch-up messages when the schedule's sendIfMissed + cutoff settings allow it.
    suspend fun rescheduleAllEnabled() {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val enabledSchedules = getEnabledSchedules()

            enabledSchedules.forEach { schedule ->
                val storedAlarmTime = smsScheduleManager.getStoredAlarmTime(schedule.id)
                val alarmWasMissed = storedAlarmTime > 0 && storedAlarmTime < now

                if (alarmWasMissed) {
                    handleMissedAlarm(schedule, storedAlarmTime, now)
                } else {
                    smsScheduleManager.scheduleRepeatingWork(schedule, allowCatchUp = true)
                }
            }
        }
    }

    private suspend fun handleMissedAlarm(schedule: SmsSchedule, missedTime: Long, now: Long) {
        val diffMinutes = (now - missedTime) / 60_000
        val withinCutoff = schedule.missedCutoffMinutes == SmsSchedule.CUTOFF_NO_LIMIT ||
                diffMinutes <= schedule.missedCutoffMinutes
        val shouldCatchUp = schedule.sendIfMissed && withinCutoff

        Log.d("SmsScheduleRepository",
                "Missed alarm for ${schedule.id} (${schedule.contactName}): " +
                "${diffMinutes}min late, sendIfMissed=${schedule.sendIfMissed}, " +
                "cutoff=${schedule.missedCutoffMinutes}, catchUp=$shouldCatchUp")

        if (shouldCatchUp) {
            enqueueCatchUpWorker(schedule)
        }

        if (schedule.isRecurring) {
            smsScheduleManager.scheduleRepeatingWork(schedule, allowCatchUp = true)
        } else if (!shouldCatchUp) {
            // One-off that wasn't caught up: disable so it doesn't reschedule to tomorrow
            smsScheduleDao.updateEnabled(schedule.id, false)
            Log.d("SmsScheduleRepository",
                    "Disabled missed one-off schedule ${schedule.id}")
        }
        // One-off + caught up: don't reschedule — SmsWorker will disable after sending
    }

    private fun enqueueCatchUpWorker(schedule: SmsSchedule) {
        val inputData = workDataOf(
                "scheduleId" to schedule.id,
                "contactName" to schedule.contactName,
                "phoneNumber" to schedule.phoneNumber,
                // scheduled_time intentionally omitted (defaults to 0) so the worker
                // skips its own lateness check — we already verified the cutoff above
        )

        val workRequest = OneTimeWorkRequestBuilder<SmsWorker>()
                .setInputData(inputData)
                .build()

        // KEEP: if WorkManager already has a surviving job for this schedule
        // (e.g. alarm fired right before shutdown), don't duplicate it
        androidx.work.WorkManager.getInstance(context)
                .enqueueUniqueWork("sms_work_${schedule.id}", ExistingWorkPolicy.KEEP, workRequest)

        Log.d("SmsScheduleRepository",
                "Enqueued catch-up send for ${schedule.id} (${schedule.contactName})")
    }
}
