package com.elias.autosms.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.elias.autosms.repository.SmsScheduleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
                        intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {

            Log.d("BootReceiver", "Device boot completed, rescheduling SMS tasks")

            // Use goAsync() to keep the BroadcastReceiver alive during async work
            val pendingResult = goAsync()

            // Use SupervisorJob so failures don't cancel sibling coroutines
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try {
                    val repository = SmsScheduleRepository(context.applicationContext)
                    repository.rescheduleAllEnabled()
                    Log.d("BootReceiver", "All enabled schedules rescheduled successfully")
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Error rescheduling SMS schedules", e)
                } finally {
                    // Always finish the pending result to prevent ANR
                    pendingResult.finish()
                }
            }
        }
    }
}
