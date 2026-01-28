package com.elias.autosms.repository

import android.content.Context
import androidx.lifecycle.LiveData
import com.elias.autosms.data.SmsSchedule
import com.elias.autosms.data.SmsScheduleDatabase
import com.elias.autosms.utils.SmsScheduleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmsScheduleRepository(context: Context) {

    private val database = SmsScheduleDatabase.getDatabase(context)
    private val smsScheduleDao = database.smsScheduleDao()
    private val smsScheduleManager = SmsScheduleManager(context)

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

    // Insert a new schedule and schedule its work if enabled
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
            // Fetch the old schedule to compare time settings
            val oldSchedule = smsScheduleDao.getScheduleById(schedule.id)

            smsScheduleDao.update(schedule)
            smsScheduleManager.cancelWork(schedule.id)

            if (schedule.isEnabled) {
                // If the schedule is NOT recurring, check if it's in the past
                if (!schedule.isRecurring) {
                    val delay = smsScheduleManager.calculateInitialDelay(schedule)
                    if (delay < 0) {
                        // It is in the past. Check if the user changed the time.
                        // If the time is the same as before, we assume the user is just disabling
                        // repetition
                        // on an already completed event, so we should NOT re-send (duplicate).
                        if (oldSchedule != null &&
                                        oldSchedule.hour == schedule.hour &&
                                        oldSchedule.minute == schedule.minute
                        ) {
                            android.util.Log.d(
                                    "SmsRepo",
                                    "Disabling past non-recurring schedule to prevent duplicate send."
                            )
                            smsScheduleDao.updateEnabled(schedule.id, false)
                            return@withContext
                        }
                    }
                }

                smsScheduleManager.scheduleRepeatingWork(schedule)
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
                    smsScheduleManager.scheduleRepeatingWork(schedule)
                }
            }
        }
    }

    // Reschedule all enabled schedules (e.g., after device reboot)
    suspend fun rescheduleAllEnabled() {
        withContext(Dispatchers.IO) {
            val enabledSchedules = getEnabledSchedules()
            enabledSchedules.forEach { schedule ->
                smsScheduleManager.scheduleRepeatingWork(schedule)
            }
        }
    }
}
