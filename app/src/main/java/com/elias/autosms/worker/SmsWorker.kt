package com.elias.autosms.worker

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elias.autosms.data.SmsScheduleDatabase
import com.elias.autosms.utils.ChatGptService
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

            // Validate phone number
            if (phoneNumber.isEmpty()) {
                Log.e("SmsWorker", "Invalid phone number")
                return Result.failure()
            }

            // Get the message to send (generate new AI message if needed)
            val messageToSend = if (schedule.isAiGenerated) {
                generateAiMessage(schedule)
            } else {
                schedule.message
            }

            if (messageToSend.isEmpty()) {
                Log.e("SmsWorker", "Empty message")
                return Result.failure()
            }

            // Send SMS
            sendSms(phoneNumber, messageToSend)
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

    private suspend fun generateAiMessage(schedule: com.elias.autosms.data.SmsSchedule): String {
        return try {
            val chatGptService = ChatGptService(applicationContext)
            
            if (!chatGptService.hasApiKey()) {
                Log.w("SmsWorker", "No API key configured, using fallback message")
                return schedule.message
            }

            val result = if (schedule.messageContext.isNotEmpty()) {
                chatGptService.generateMessageWithContext(
                    schedule.contactName,
                    schedule.messageContext,
                    100
                )
            } else {
                chatGptService.generateRandomMessage(
                    schedule.contactName,
                    schedule.messageType,
                    100
                )
            }

            if (result.isSuccess) {
                result.getOrNull() ?: schedule.message
            } else {
                Log.w("SmsWorker", "Failed to generate AI message: ${result.exceptionOrNull()?.message}")
                schedule.message // Fallback to original message
            }
        } catch (e: Exception) {
            Log.e("SmsWorker", "Error generating AI message", e)
            schedule.message // Fallback to original message
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