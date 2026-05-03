package com.elias.autosms.billing

sealed class EntitlementState {
    // Billing client hasn't returned a result yet — UI should treat as locked
    // and the listener service should not generate replies.
    data object Unknown : EntitlementState()

    // Active paid subscription (includes the introductory free-trial phase
    // when configured as a free-trial offer in the Play Console — the trial
    // shows up as an active subscription in our purchase list).
    data object Subscribed : EntitlementState()

    // No active subscription. The user must subscribe before AI replies fire.
    data object NotSubscribed : EntitlementState()

    val isEntitled: Boolean get() = this is Subscribed
}
