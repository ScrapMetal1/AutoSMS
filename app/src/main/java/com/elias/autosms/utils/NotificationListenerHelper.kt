package com.elias.autosms.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.elias.autosms.service.SmsNotificationListenerService

object NotificationListenerHelper {

    fun isEnabled(context: Context): Boolean {
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(context)
        return enabled.contains(context.packageName)
    }

    fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            // FLAG_ACTIVITY_NEW_TASK so this works when called from a non-Activity context.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    // Toggling the binding off/on after a settings change forces the system to
    // rebind so onNotificationPosted starts firing again immediately.
    fun rebind(context: Context) {
        val component = ComponentName(context, SmsNotificationListenerService::class.java)
        val pm = context.packageManager
        pm.setComponentEnabledSetting(
                component,
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
        )
        pm.setComponentEnabledSetting(
                component,
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
        )
    }
}
