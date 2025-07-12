package com.elias.autosms.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.elias.autosms.data.SmsSchedule
import com.elias.autosms.repository.SmsScheduleRepository
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SmsScheduleRepository(application)

    val allSchedules: LiveData<List<SmsSchedule>> = repository.getAllSchedules()

    // Toggle a schedule's enabled state
    fun toggleSchedule(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.updateEnabled(id, enabled)
        }
    }

    // Delete a schedule
    fun deleteSchedule(id: Long) {
        viewModelScope.launch {
            repository.getScheduleById(id)?.let { schedule ->
                repository.delete(schedule)
            }
        }
    }
}