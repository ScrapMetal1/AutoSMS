package com.elias.autosms.worker

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elias.autosms.data.SmsScheduleDatabase
import java.util.*

class SmsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val scheduleId = inputData.getLong("scheduleId", -1)
            val contactName = inputData.getString("contactName") ?: ""
            val phoneNumber = inputData.getString("phoneNumber") ?: ""
            val message = inputData.getString("message") ?: ""
            val hour = inputData.getInt("hour", 0)
            val minute = inputData.getInt("minute", 0)

            Log.d("SmsWorker", "Attempting to send SMS to $contactName ($phoneNumber)")

            // Verify the schedule is still enabled
            val database = SmsScheduleDatabase.getDatabase(applicationContext)
            val schedule = database.smsScheduleDao().getScheduleById(scheduleId)

            if (schedule == null || !schedule.isEnabled) {
                Log.d("SmsWorker", "Schedule $scheduleId is disabled or deleted, skipping")
                return Result.success()
            }

            // Check if it's within a 5-minute window of the scheduled time
            val now = Calendar.getInstance()
            val currentHour = now.get(Calendar.HOUR_OF_DAY)
            val currentMinute = now.get(Calendar.MINUTE)

            val timeDiff = Math.abs((currentHour * 60 + currentMinute) - (hour * 60 + minute))
            if (timeDiff > 5) {
                Log.d("SmsWorker", "Not the right time to send SMS, time difference: $timeDiff minutes")
                return Result.success()
            }

            // Validate phone number and message
            if (phoneNumber.isEmpty()) {
                Log.e("SmsWorker", "Invalid phone number")
                return Result.failure()
            }
            if (message.isEmpty()) {
                Log.e("SmsWorker", "Empty message")
                return Result.failure()
            }

            // Send SMS
            sendSms(phoneNumber, message)
            Log.d("SmsWorker", "SMS sent successfully to $contactName")
            Result.success()
        } catch (e: SecurityException) {
            Log.e("SmsWorker", "SMS permission denied", e)
            Result.failure()
        } catch (e: Exception) {
            Log.e("SmsWorker", "Error sending SMS", e)
            Result.failure()
        }
    }

    // Sends an SMS, handling both single and multipart messages
    private fun sendSms(phoneNumber: String, message: String) {
        try {
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)

            if (parts.size == 1) {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            }
        } catch (e: Exception) {
            Log.e("SmsWorker", "Failed to send SMS to $phoneNumber", e)
            throw e
        }
    }
}