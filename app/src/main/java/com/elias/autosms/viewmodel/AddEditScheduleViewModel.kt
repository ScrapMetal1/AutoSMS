package com.elias.autosms.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.elias.autosms.data.SmsSchedule
import com.elias.autosms.repository.SmsScheduleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// tells the UI whether save actually worked, so we don't show "Schedule saved" and close when the DB failed.
sealed class SaveResult {
    data class Success(val isEdit: Boolean) : SaveResult()
    data class Error(val message: String) : SaveResult()
}

class AddEditScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SmsScheduleRepository(application)

    // activity observes this and only finishes on Success; on Error we show a toast and stay put.
    private val _saveResult = MutableLiveData<SaveResult?>(null)
    val saveResult: LiveData<SaveResult?> = _saveResult

    fun insertSchedule(schedule: SmsSchedule) {
        viewModelScope.launch {
            try {
                repository.insert(schedule)
                withContext(Dispatchers.Main) {
                    _saveResult.value = SaveResult.Success(isEdit = false)
                }
            } catch (e: Exception) {
                // DB or WorkManager failed — let the UI show an error instead of pretending it worked.
                withContext(Dispatchers.Main) {
                    _saveResult.value = SaveResult.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    fun updateSchedule(schedule: SmsSchedule) {
        viewModelScope.launch {
            try {
                repository.update(schedule)
                withContext(Dispatchers.Main) {
                    _saveResult.value = SaveResult.Success(isEdit = true)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _saveResult.value = SaveResult.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    // clear after handling so we don't re-trigger the observer on config change (e.g. rotation).
    fun clearSaveResult() {
        _saveResult.value = null
    }
}