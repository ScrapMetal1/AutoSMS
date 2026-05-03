package com.elias.autosms.viewmodel

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.elias.autosms.billing.BillingManager
import com.elias.autosms.billing.EntitlementState
import com.android.billingclient.api.ProductDetails
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PremiumViewModel(application: Application) : AndroidViewModel(application) {

    private val billing = BillingManager.get(application)

    val entitlement: StateFlow<EntitlementState> = billing.entitlement
    val product: StateFlow<ProductDetails?> = billing.productDetails

    fun subscribe(activity: Activity) {
        billing.launchPurchaseFlow(activity)
    }

    fun refresh() {
        viewModelScope.launch { billing.refreshEntitlement() }
    }
}

class PremiumViewModelFactory(private val app: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PremiumViewModel(app) as T
}
