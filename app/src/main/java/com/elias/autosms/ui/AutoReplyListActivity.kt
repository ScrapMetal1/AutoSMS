package com.elias.autosms.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.elias.autosms.BuildConfig
import com.elias.autosms.R
import com.elias.autosms.billing.BillingManager
import com.elias.autosms.billing.EntitlementState
import com.elias.autosms.databinding.ActivityAutoReplyListBinding
import com.elias.autosms.repository.ContextDocumentRepository
import com.elias.autosms.ui.adapter.AutoReplyRuleAdapter
import com.elias.autosms.utils.NotificationListenerHelper
import com.elias.autosms.viewmodel.AutoReplyListViewModel
import com.elias.autosms.viewmodel.AutoReplyListViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AutoReplyListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAutoReplyListBinding
    private lateinit var viewModel: AutoReplyListViewModel
    private lateinit var adapter: AutoReplyRuleAdapter
    private val documentsRepo by lazy { ContextDocumentRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAutoReplyListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val factory = AutoReplyListViewModelFactory(application)
        viewModel = ViewModelProvider(this, factory)[AutoReplyListViewModel::class.java]

        adapter = AutoReplyRuleAdapter(
                onToggleClick = { rule, enabled -> viewModel.setEnabled(rule, enabled) },
                onEditClick = { rule ->
                    val intent = Intent(this, AddEditAutoReplyRuleActivity::class.java)
                    intent.putExtra(AddEditAutoReplyRuleActivity.EXTRA_RULE, rule)
                    startActivity(intent)
                },
                onDeleteClick = { rule ->
                    AlertDialog.Builder(this)
                            .setMessage(R.string.auto_reply_delete_confirm)
                            .setPositiveButton(R.string.delete_button) { _, _ -> viewModel.delete(rule) }
                            .setNegativeButton(R.string.cancel_button, null)
                            .show()
                }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setHasFixedSize(false)

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddEditAutoReplyRuleActivity::class.java))
        }

        // The two action cards replace the old overflow menu so the most-used
        // navigation is visible the moment the screen opens.
        binding.cardSetupStatus.setOnClickListener {
            startActivity(Intent(this, AutoReplySetupActivity::class.java))
        }
        binding.cardDocuments.setOnClickListener {
            startActivity(Intent(this, ContextDocumentListActivity::class.java))
        }

        viewModel.rules.observe(this) { rules ->
            adapter.submitList(rules)
            binding.emptyView.visibility =
                    if (rules.isEmpty()) View.VISIBLE else View.GONE
        }

        // Live count for the Documents card. Observed once and re-fetched
        // onResume so changes made on the documents screen reflect when the
        // user comes back.
        observeEntitlement()
    }

    override fun onResume() {
        super.onResume()
        refreshSetupStatus()
        refreshDocumentsCount()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    /** Setup status card flips between needs-action (tertiary) and ok (primary). */
    private fun refreshSetupStatus() {
        val granted = NotificationListenerHelper.isEnabled(this)
        if (granted) {
            binding.iconSetupStatus.setImageResource(R.drawable.ic_check_circle)
            binding.textSetupStatusTitle.setText(R.string.auto_reply_action_setup_ok)
            binding.textSetupStatusSub.setText(R.string.auto_reply_action_setup_sub_ok)
            binding.cardSetupStatus.setCardBackgroundColor(
                    com.google.android.material.color.MaterialColors.getColor(
                            binding.root,
                            com.google.android.material.R.attr.colorPrimaryContainer
                    )
            )
            val onContainer = com.google.android.material.color.MaterialColors.getColor(
                    binding.root,
                    com.google.android.material.R.attr.colorOnPrimaryContainer
            )
            binding.iconSetupStatus.setColorFilter(onContainer)
            binding.textSetupStatusTitle.setTextColor(onContainer)
            binding.textSetupStatusSub.setTextColor(onContainer)
        } else {
            binding.iconSetupStatus.setImageResource(R.drawable.ic_lock)
            binding.textSetupStatusTitle.setText(R.string.auto_reply_action_setup_needed)
            binding.textSetupStatusSub.setText(R.string.auto_reply_action_setup_sub_needed)
            binding.cardSetupStatus.setCardBackgroundColor(
                    com.google.android.material.color.MaterialColors.getColor(
                            binding.root,
                            com.google.android.material.R.attr.colorTertiaryContainer
                    )
            )
            val onContainer = com.google.android.material.color.MaterialColors.getColor(
                    binding.root,
                    com.google.android.material.R.attr.colorOnTertiaryContainer
            )
            binding.iconSetupStatus.setColorFilter(onContainer)
            binding.textSetupStatusTitle.setTextColor(onContainer)
            binding.textSetupStatusSub.setTextColor(onContainer)
        }
    }

    private fun refreshDocumentsCount() {
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) { documentsRepo.getEnabled().size }
            binding.textDocumentsCount.text = when (count) {
                0 -> getString(R.string.auto_reply_documents_count_zero)
                1 -> getString(R.string.auto_reply_documents_count_one)
                else -> getString(R.string.auto_reply_documents_count_many, count)
            }
        }
    }

    // Premium gate. Debug builds skip so the AI flow can be tested before
    // billing is wired up in Play Console.
    private fun observeEntitlement() {
        if (BuildConfig.BYPASS_PREMIUM) return
        lifecycleScope.launch {
            BillingManager.get(this@AutoReplyListActivity).entitlement.collectLatest { state ->
                if (state == EntitlementState.NotSubscribed) {
                    startActivity(Intent(this@AutoReplyListActivity, PremiumActivity::class.java))
                    finish()
                }
            }
        }
    }
}
