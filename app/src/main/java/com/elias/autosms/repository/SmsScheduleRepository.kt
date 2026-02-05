package com.elias.autosms.repository

import android.content.Context
import androidx.lifecycle.LiveData
import com.elias.autosms.data.SmsSchedule
import com.elias.autosms.data.SmsScheduleDatabase
import com.elias.autosms.utils.SmsScheduleManager
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

    // Reschedule all enabled schedules (e.g., after device reboot)
    suspend fun rescheduleAllEnabled() {
        withContext(Dispatchers.IO) {
            val enabledSchedules = getEnabledSchedules()
            enabledSchedules.forEach { schedule ->
                // Reboot: Must Allow CatchUp to resume schedules correctly
                smsScheduleManager.scheduleRepeatingWork(schedule, allowCatchUp = true)
            }
        }
    }
}
