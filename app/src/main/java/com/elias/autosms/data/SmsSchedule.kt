package com.elias.autosms.data

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Entity(tableName = "sms_schedules")
@Parcelize
data class SmsSchedule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contactName: String,
    val phoneNumber: String,
    val message: String,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable {

    // Formats the time in 12-hour format with AM/PM for UI display
    fun getFormattedTime(): String {
        val hourStr = if (hour == 0) "12" else if (hour > 12) "${hour - 12}" else "$hour"
        val minuteStr = if (minute < 10) "0$minute" else "$minute"
        val amPm = if (hour < 12) "AM" else "PM"
        return "$hourStr:$minuteStr $amPm"
    }
}