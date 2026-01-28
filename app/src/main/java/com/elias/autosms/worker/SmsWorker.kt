package com.elias.autosms.worker

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elias.autosms.data.SmsScheduleDatabase
import java.util.*

class SmsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val scheduleId = inputData.getLong("scheduleId", -1)
        var smsSentSuccessfully = false

        // 1. Read the input (default to 0 if missing)
        val scheduledTime = inputData.getLong("scheduled_time", 0)

        val database = SmsScheduleDatabase.getDatabase(applicationContext)

        return try {
            val contactName = inputData.getString("contactName") ?: ""
            val phoneNumber = inputData.getString("phoneNumber") ?: ""

            Log.d("SmsWorker", "Attempting to send SMS to $contactName ($phoneNumber)")

            // Verify the schedule is still enabled
            val schedule = database.smsScheduleDao().getScheduleById(scheduleId)

            if (schedule == null || !schedule.isEnabled) {
                Log.d("SmsWorker", "Schedule $scheduleId is disabled or deleted, skipping")
                return Result.success()
            }

            // 2. The Validation Logic using Timestamp
            if (scheduledTime > 0) {
                val currentTime = System.currentTimeMillis()
                val diff = currentTime - scheduledTime

                // 2 hours in milliseconds = 7200000
                if (diff > 7200000) {
                    Log.w("SmsWorker", "Skipping SMS. Too late. Diff: ${diff / 60000} mins")
                    // Return success so we don't retry this specific stale job,
                    // but the 'finally' block will still schedule the NEXT recurring one.
                    return Result.success()
                }
            }

            // Validate phone number
            if (phoneNumber.isEmpty()) {
                Log.e("SmsWorker", "Invalid phone number")
                return Result.failure()
            }

            // Get the message to send
            val messageToSend = schedule.message

            if (messageToSend.isEmpty()) {
                Log.e("SmsWorker", "Empty message")
                return Result.failure()
            }

            // Send SMS
            sendSms(phoneNumber, messageToSend)
            Log.d("SmsWorker", "SMS sent successfully to $contactName")
            smsSentSuccessfully = true
            Result.success()
        } catch (e: SecurityException) {
            Log.e("SmsWorker", "SMS permission denied", e)
            Result.failure()
        } catch (e: Exception) {
            Log.e("SmsWorker", "Error sending SMS", e)
            Result.failure()
        } finally {
            // Re-fetch the schedule from DB to get the LATEST state
            // This prevents race conditions where the user changed isRecurring between
            // when we first fetched and now (e.g. recurring -> non-recurring edit)
            try {
                val currentSchedule = database.smsScheduleDao().getScheduleById(scheduleId)

                if (currentSchedule != null && currentSchedule.isEnabled) {
                    if (currentSchedule.isRecurring) {
                        // Recurring: schedule the next execution
                        Log.d("SmsWorker", "Rescheduling next execution for schedule $scheduleId")
                        val smsScheduleManager =
                                com.elias.autosms.utils.SmsScheduleManager(applicationContext)
                        smsScheduleManager.scheduleRepeatingWork(
                                currentSchedule,
                                isRescheduleForNextInterval = true
                        )
                    } else if (smsSentSuccessfully) {
                        // Non-recurring and sent successfully: disable to prevent re-sends
                        Log.d(
                                "SmsWorker",
                                "Disabling non-recurring schedule $scheduleId after successful send"
                        )
                        database.smsScheduleDao().updateEnabled(scheduleId, false)
                    }
                }
            } catch (e: Exception) {
                Log.e("SmsWorker", "Failed to handle post-send schedule update", e)
            }
        }
    }

    // Sends an SMS, handling both single and multipart messages
    private fun sendSms(phoneNumber: String, message: String) {
        try {
            val smsManager =
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        applicationContext.getSystemService(SmsManager::class.java)
                    } else {
                        @Suppress("DEPRECATION") SmsManager.getDefault()
                    }

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
