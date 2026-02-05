package com.elias.autosms.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.elias.autosms.data.SmsSchedule
import com.elias.autosms.repository.SmsScheduleRepository
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SmsScheduleRepository(application)
    private val sourceSchedules = repository.getAllSchedules()

    // Sort state
    enum class SortField {
        CREATED,
        TIME,
        START_DATE,
        ENABLED
    }
    enum class SortDirection {
        ASC,
        DESC
    }

    private val _sortField = MutableLiveData(SortField.CREATED)
    private val _sortDirection = MutableLiveData(SortDirection.DESC)

    val allSchedules =
            MediatorLiveData<List<SmsSchedule>>().apply {
                addSource(sourceSchedules) { updateSortedList() }
                addSource(_sortField) { updateSortedList() }
                addSource(_sortDirection) { updateSortedList() }
            }

    fun setSortOption(field: SortField, direction: SortDirection) {
        _sortField.value = field
        _sortDirection.value = direction
    }

    fun getSortField(): SortField = _sortField.value ?: SortField.CREATED
    fun getSortDirection(): SortDirection = _sortDirection.value ?: SortDirection.DESC

    private fun updateSortedList() {
        val list = sourceSchedules.value ?: emptyList()
        val field = _sortField.value ?: SortField.CREATED
        val direction = _sortDirection.value ?: SortDirection.DESC

        val sortedList =
                list.sortedWith(
                        Comparator { o1, o2 ->
                            val res =
                                    when (field) {
                                        SortField.CREATED -> o1.id.compareTo(o2.id)
                                        SortField.TIME -> {
                                            // Normalize to time of day for "Time" sort, or just
                                            // next execution?
                                            // User said "Time" (likely time of day) and "Start
                                            // Date" (likely date).
                                            // Assuming timeInMillis is the full next trigger.
                                            // Let's use the time components for "Time" and full
                                            // millis for "Start Date".
                                            // Actually, simple next execution is best for both
                                            // usually, but let's be specific.
                                            // Normalize to time of day for "Time" sort
                                            val t1 = o1.hour * 60 + o1.minute
                                            val t2 = o2.hour * 60 + o2.minute
                                            t1.compareTo(t2)
                                        }
                                        SortField.START_DATE -> o1.startDate.compareTo(o2.startDate)
                                        SortField.ENABLED -> o1.isEnabled.compareTo(o2.isEnabled)
                                    }
                            if (direction == SortDirection.ASC) res else -res
                        }
                )

        allSchedules.value = sortedList
    }

    // Toggle a schedule's enabled state
    fun toggleSchedule(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.updateEnabled(id, enabled) }
    }

    // Delete a schedule
    fun deleteSchedule(id: Long) {
        viewModelScope.launch {
            repository.getScheduleById(id)?.let { schedule -> repository.delete(schedule) }
        }
    }
}
