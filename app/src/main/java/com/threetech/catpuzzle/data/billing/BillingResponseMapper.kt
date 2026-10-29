package com.threetech.catpuzzle.data.billing

import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.Purchase
import com.threetech.catpuzzle.domain.billing.BillingFailure
import com.threetech.catpuzzle.domain.billing.PurchaseStatus

/** Pure mapping from Play Billing ints to domain types (no BillingClient instance needed, unit-tested). */
internal object BillingResponseMapper {

    /** Play response code -> failure. [BillingResponseCode.OK] is not a failure: callers branch on it first. */
    fun failureOf(responseCode: Int): BillingFailure = when (responseCode) {
        BillingResponseCode.USER_CANCELED -> BillingFailure.USER_CANCELED
        BillingResponseCode.ITEM_ALREADY_OWNED -> BillingFailure.ALREADY_OWNED
        BillingResponseCode.SERVICE_DISCONNECTED -> BillingFailure.SERVICE_DISCONNECTED
        BillingResponseCode.SERVICE_UNAVAILABLE, BillingResponseCode.SERVICE_TIMEOUT -> BillingFailure.SERVICE_UNAVAILABLE
        BillingResponseCode.BILLING_UNAVAILABLE, BillingResponseCode.FEATURE_NOT_SUPPORTED -> BillingFailure.BILLING_UNAVAILABLE
        BillingResponseCode.NETWORK_ERROR -> BillingFailure.NETWORK
        BillingResponseCode.DEVELOPER_ERROR -> BillingFailure.DEVELOPER_ERROR
        BillingResponseCode.ITEM_UNAVAILABLE -> BillingFailure.ITEM_UNAVAILABLE
        else -> BillingFailure.ERROR // ERROR, ITEM_NOT_OWNED, unknown future codes
    }

    fun statusOf(purchaseState: Int): PurchaseStatus = when (purchaseState) {
        Purchase.PurchaseState.PURCHASED -> PurchaseStatus.PURCHASED
        Purchase.PurchaseState.PENDING -> PurchaseStatus.PENDING
        else -> PurchaseStatus.UNSPECIFIED
    }

    /** Failures worth another connection attempt. The rest will not change by retrying. */
    fun isRetryableConnect(responseCode: Int): Boolean = when (failureOf(responseCode)) {
        BillingFailure.SERVICE_DISCONNECTED, BillingFailure.SERVICE_UNAVAILABLE, BillingFailure.NETWORK, BillingFailure.ERROR -> true
        else -> false
    }
}
