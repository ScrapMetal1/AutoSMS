package com.elias.autosms.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface SmsScheduleDao {

    @Query("SELECT * FROM sms_schedules ORDER BY hour, minute")
    fun getAllSchedules(): LiveData<List<SmsSchedule>>

    @Query("SELECT * FROM sms_schedules WHERE isEnabled = 1")
    suspend fun getEnabledSchedules(): List<SmsSchedule>

    @Query("SELECT * FROM sms_schedules WHERE id = :id")
    suspend fun getScheduleById(id: Long): SmsSchedule?

    @Insert
    suspend fun insert(schedule: SmsSchedule): Long

    @Update
    suspend fun update(schedule: SmsSchedule)

    @Delete
    suspend fun delete(schedule: SmsSchedule)

    @Query("DELETE FROM sms_schedules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE sms_schedules SET isEnabled = :enabled WHERE id = :id")
    suspend fun updateEnabled(id: Long, enabled: Boolean)
}