package com.elias.autosms.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.elias.autosms.databinding.ActivityAutoReplySetupBinding
import com.elias.autosms.utils.NotificationListenerHelper

class AutoReplySetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAutoReplySetupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAutoReplySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.buttonOpenSettings.setOnClickListener {
            NotificationListenerHelper.openSettings(this)
        }
    }

    override fun onResume() {
        super.onResume()
        // Whenever the user returns from Settings, reflect the new state.
        if (NotificationListenerHelper.isEnabled(this)) {
            // Force a rebind so the listener starts receiving callbacks immediately.
            NotificationListenerHelper.rebind(this)
            binding.textStatus.visibility = View.VISIBLE
            binding.buttonOpenSettings.isEnabled = false
        } else {
            binding.textStatus.visibility = View.GONE
            binding.buttonOpenSettings.isEnabled = true
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
