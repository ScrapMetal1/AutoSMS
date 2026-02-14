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

            // Use goAsync() to keep the BroadcastReceiver alive during async work
            val pendingResult = goAsync()

            // Use SupervisorJob so failures don't cancel sibling coroutines
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try {
                    val repository = SmsScheduleRepository(context.applicationContext)
                    repository.rescheduleAllEnabled()
                } catch (e: Exception) {
                    // used to swallow this; now we log so we can see why reschedule failed after a reboot.
                    Log.e("BootReceiver", "Failed to reschedule after boot", e)
                } finally {

                    // Always finish the pending result to prevent ANR
                    pendingResult.finish()
                }
            }
        }
    }
}
