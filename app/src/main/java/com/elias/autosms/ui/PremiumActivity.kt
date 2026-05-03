package com.elias.autosms.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.elias.autosms.billing.EntitlementState
import com.elias.autosms.databinding.ActivityPremiumBinding
import com.elias.autosms.viewmodel.PremiumViewModel
import com.elias.autosms.viewmodel.PremiumViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PremiumActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPremiumBinding
    private lateinit var viewModel: PremiumViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPremiumBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val factory = PremiumViewModelFactory(application)
        viewModel = ViewModelProvider(this, factory)[PremiumViewModel::class.java]

        binding.buttonSubscribe.setOnClickListener { viewModel.subscribe(this) }
        binding.buttonRestore.setOnClickListener { viewModel.refresh() }

        observeState()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.entitlement.collectLatest { state ->
                val active = state == EntitlementState.Subscribed
                binding.textActiveBanner.visibility = if (active) View.VISIBLE else View.GONE
                binding.buttonSubscribe.isEnabled = !active
            }
        }
        lifecycleScope.launch {
            viewModel.product.collectLatest { details ->
                // The first offer is the introductory free-trial offer when
                // configured in the Play Console — its first phase is the trial,
                // its second phase is the recurring price. We just surface the
                // recurring price here; the Play sheet shows full trial terms.
                val phase = details?.subscriptionOfferDetails
                        ?.firstOrNull()
                        ?.pricingPhases
                        ?.pricingPhaseList
                        ?.lastOrNull()
                binding.textPriceSummary.text = phase?.formattedPrice
                        ?.let { "$it / billing period" }
                        .orEmpty()
            }
        }
    }
}
