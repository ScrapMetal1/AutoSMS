package com.elias.autosms.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.elias.autosms.R
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

        bindSteps()

        binding.buttonOpenSettings.setOnClickListener {
            NotificationListenerHelper.openSettings(this)
        }
    }

    private fun bindSteps() {
        binding.step1.textStepNumber.text = "1"
        binding.step1.textStepTitle.setText(R.string.auto_reply_setup_step1)
        binding.step1.textStepSub.setText(R.string.auto_reply_setup_step1_sub)

        binding.step2.textStepNumber.text = "2"
        binding.step2.textStepTitle.setText(R.string.auto_reply_setup_step2)
        binding.step2.textStepSub.setText(R.string.auto_reply_setup_step2_sub)
    }

    override fun onResume() {
        super.onResume()
        // Whenever the user returns from Settings, reflect the new state.
        if (NotificationListenerHelper.isEnabled(this)) {
            // Force a rebind so the listener starts receiving callbacks immediately.
            NotificationListenerHelper.rebind(this)
            binding.iconStatus.setImageResource(R.drawable.ic_check_circle)
            binding.textStatus.setText(R.string.auto_reply_setup_status_done)
            binding.statusCard.setCardBackgroundColor(
                    com.google.android.material.color.MaterialColors.getColor(
                            binding.root,
                            com.google.android.material.R.attr.colorPrimaryContainer
                    )
            )
            binding.buttonOpenSettings.isEnabled = false
            binding.buttonOpenSettings.text = getString(R.string.auto_reply_setup_done)
        } else {
            binding.iconStatus.setImageResource(R.drawable.ic_lock)
            binding.textStatus.setText(R.string.auto_reply_setup_status_pending)
            binding.buttonOpenSettings.isEnabled = true
            binding.buttonOpenSettings.setText(R.string.auto_reply_setup_open)
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
