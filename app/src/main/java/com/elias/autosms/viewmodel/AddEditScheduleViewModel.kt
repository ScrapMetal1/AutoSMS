package com.elias.autosms.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elias.autosms.data.SmsSchedule
import com.elias.autosms.repository.SmsScheduleRepository
import kotlinx.coroutines.launch

class AddEditScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SmsScheduleRepository(application)

    // Insert a new SMS schedule
    fun insertSchedule(schedule: SmsSchedule) {
        viewModelScope.launch {
            repository.insert(schedule)
        }
    }

    // Update an existing SMS schedule
    fun updateSchedule(schedule: SmsSchedule) {
        viewModelScope.launch {
            repository.update(schedule)
        }
    }
}