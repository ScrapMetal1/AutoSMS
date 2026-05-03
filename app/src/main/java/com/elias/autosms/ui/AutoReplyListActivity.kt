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
import com.elias.autosms.R
import com.elias.autosms.billing.BillingManager
import com.elias.autosms.billing.EntitlementState
import com.elias.autosms.databinding.ActivityAutoReplyListBinding
import com.elias.autosms.ui.adapter.AutoReplyRuleAdapter
import com.elias.autosms.utils.NotificationListenerHelper
import com.elias.autosms.viewmodel.AutoReplyListViewModel
import com.elias.autosms.viewmodel.AutoReplyListViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AutoReplyListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAutoReplyListBinding
    private lateinit var viewModel: AutoReplyListViewModel
    private lateinit var adapter: AutoReplyRuleAdapter

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
        binding.recyclerView.setHasFixedSize(true)

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddEditAutoReplyRuleActivity::class.java))
        }

        binding.buttonOpenSetup.setOnClickListener {
            startActivity(Intent(this, AutoReplySetupActivity::class.java))
        }

        viewModel.rules.observe(this) { rules ->
            adapter.submitList(rules)
            binding.emptyView.visibility =
                    if (rules.isEmpty()) View.VISIBLE else View.GONE
        }

        observeEntitlement()
    }

    override fun onResume() {
        super.onResume()
        // Setup banner is shown whenever the listener service isn't bound.
        binding.cardSetupBanner.visibility =
                if (NotificationListenerHelper.isEnabled(this)) View.GONE else View.VISIBLE
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_auto_reply_list, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_context_documents -> {
                startActivity(Intent(this, ContextDocumentListActivity::class.java))
                true
            }
            R.id.action_setup -> {
                startActivity(Intent(this, AutoReplySetupActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Premium gate: if not entitled, send the user to the paywall instead of
    // letting them configure rules they can't actually run.
    private fun observeEntitlement() {
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
