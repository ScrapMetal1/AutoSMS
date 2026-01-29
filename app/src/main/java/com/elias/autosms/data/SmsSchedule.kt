package com.elias.autosms.data

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Entity(tableName = "sms_schedules")
@Parcelize
data class SmsSchedule(
        @PrimaryKey(autoGenerate = true) val id: Long = 0,
        val contactName: String,
        val phoneNumber: String,
        val message: String,
        val hour: Int,
        val minute: Int,
        val frequency: String = FREQUENCY_DAILY,
        val period: Int = 1,
        val periodUnit: String = UNIT_DAYS,
        val isEnabled: Boolean = true,
        val isRecurring: Boolean = false,
        val startDate: Long = System.currentTimeMillis(),
        val createdAt: Long = System.currentTimeMillis()
) : Parcelable {

    // Formats the time in 12-hour format with AM/PM for UI display
    fun getFormattedTime(): String {
        val hourStr =
                when {
                    hour == 0 -> "12" // Midnight
                    hour > 12 -> "${hour - 12}" // Afternoon/evening
                    hour == 12 -> "12" // Noon
                    else -> "$hour" // Morning
                }
        val minuteStr = if (minute < 10) "0$minute" else "$minute"
        val amPm = if (hour < 12) "AM" else "PM"
        return "$hourStr:$minuteStr $amPm"
    }

    companion object {

        const val FREQUENCY_HOURLY = "Hourly"
        const val FREQUENCY_DAILY = "Daily"
        const val FREQUENCY_WEEKLY = "Weekly"
        const val FREQUENCY_MONTHLY = "Monthly"
        const val FREQUENCY_CUSTOM = "Custom"
        const val UNIT_HOURS = "Hours"
        const val UNIT_DAYS = "Days"
    }
}
