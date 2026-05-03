package com.elias.autosms.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Singleton billing facade. Owns one [BillingClient] for the app's lifetime,
 * exposes the current [EntitlementState] as a [StateFlow], and offers a
 * [launchPurchaseFlow] entry point for the Premium screen.
 *
 * Premium is sold as a single subscription product; the free-trial period is
 * configured as an introductory offer on that product in the Play Console,
 * which means we don't need any app-side trial bookkeeping.
 *
 * The product ID is intentionally a placeholder — fill it in once you've
 * created the subscription in the Play Console.
 */
class BillingManager private constructor(context: Context) : PurchasesUpdatedListener {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _entitlement = MutableStateFlow<EntitlementState>(EntitlementState.Unknown)
    val entitlement: StateFlow<EntitlementState> = _entitlement.asStateFlow()

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

    private val client: BillingClient = BillingClient.newBuilder(appContext)
            .setListener(this)
            .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .build()

    init {
        connect()
    }

    private fun connect(attempt: Int = 0) {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing connected")
                    scope.launch {
                        loadProductDetails()
                        refreshEntitlement()
                    }
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                // Bounded backoff. Capped because Play Services should come back
                // quickly; if it doesn't, the user has bigger problems.
                scope.launch {
                    val delayMs = (1000L shl attempt.coerceAtMost(4))
                    delay(delayMs)
                    connect(attempt + 1)
                }
            }
        })
    }

    private suspend fun loadProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                        listOf(
                                QueryProductDetailsParams.Product.newBuilder()
                                        .setProductId(PREMIUM_SUBSCRIPTION_ID)
                                        .setProductType(BillingClient.ProductType.SUBS)
                                        .build()
                        )
                )
                .build()
        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            _productDetails.value = result.productDetailsList?.firstOrNull()
        } else {
            Log.w(TAG, "queryProductDetails failed: ${result.billingResult.debugMessage}")
        }
    }

    suspend fun refreshEntitlement() {
        val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        val result = client.queryPurchasesAsync(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            applyPurchases(result.purchasesList)
        } else {
            Log.w(TAG, "queryPurchases failed: ${result.billingResult.debugMessage}")
            _entitlement.value = EntitlementState.NotSubscribed
        }
    }

    private fun applyPurchases(purchases: List<Purchase>) {
        val active = purchases.firstOrNull { p ->
            p.products.contains(PREMIUM_SUBSCRIPTION_ID) &&
                    p.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        if (active != null) {
            _entitlement.value = EntitlementState.Subscribed
            if (!active.isAcknowledged) acknowledge(active)
        } else {
            _entitlement.value = EntitlementState.NotSubscribed
        }
    }

    private fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
        client.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "Acknowledge failed: ${result.debugMessage}")
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity): BillingResult? {
        val details = _productDetails.value ?: run {
            Log.w(TAG, "No product details cached — cannot start purchase")
            return null
        }
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: run {
            Log.w(TAG, "No offer token available for ${details.productId}")
            return null
        }
        val params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                        listOf(
                                BillingFlowParams.ProductDetailsParams.newBuilder()
                                        .setProductDetails(details)
                                        .setOfferToken(offerToken)
                                        .build()
                        )
                )
                .build()
        return client.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            applyPurchases(purchases)
        } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "Purchase cancelled")
        } else {
            Log.w(TAG, "Purchase update failed: ${result.debugMessage}")
        }
    }

    companion object {
        private const val TAG = "BillingManager"

        // Configure this in the Play Console (Subscriptions → create one with a
        // free-trial introductory offer). Replace with the real product ID before
        // shipping; queries will fail silently until you do.
        const val PREMIUM_SUBSCRIPTION_ID = "premium_monthly"

        @Volatile private var INSTANCE: BillingManager? = null

        fun get(context: Context): BillingManager =
                INSTANCE ?: synchronized(this) {
                    INSTANCE ?: BillingManager(context).also { INSTANCE = it }
                }
    }
}
