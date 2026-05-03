package com.elias.autosms

import android.app.Application
import android.util.Log
import com.elias.autosms.billing.BillingManager
import com.elias.autosms.data.SmsScheduleDatabase
import com.google.android.material.color.DynamicColors
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class AutoSMSApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Warm the database so the first UI query isn't on the critical path.
        SmsScheduleDatabase.getDatabase(this)

        // Material You: on Android 12+ this swaps the static brand palette for
        // the user's wallpaper-derived colors. No-op on older devices.
        DynamicColors.applyToActivitiesIfAvailable(this)

        initializeFirebase()

        // Construct the billing client eagerly so the entitlement state is
        // resolved by the time the user reaches a premium screen.
        BillingManager.get(this)
    }

    private fun initializeFirebase() {
        // FirebaseApp.initializeApp returns null when google-services.json is
        // missing or the plugin didn't run. We log + continue so the rest of
        // the scheduler keeps working; AI replies are gated behind this anyway.
        val app = try {
            FirebaseApp.initializeApp(this)
        } catch (t: Throwable) {
            Log.w(TAG, "Firebase init threw — google-services.json missing?", t)
            null
        }
        if (app == null) {
            Log.w(TAG, "Firebase not initialized — AI auto-reply will be disabled.")
            return
        }
        try {
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        } catch (t: Throwable) {
            Log.w(TAG, "App Check init failed", t)
        }
    }

    companion object {
        private const val TAG = "AutoSMSApplication"
    }
}
