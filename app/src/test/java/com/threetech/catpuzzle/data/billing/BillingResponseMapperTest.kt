package com.threetech.catpuzzle.data.billing

import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.Purchase
import com.threetech.catpuzzle.domain.billing.BillingFailure
import com.threetech.catpuzzle.domain.billing.PurchaseStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure int mapping: no BillingClient instance, so it runs on the JVM. */
class BillingResponseMapperTest {

    @Test
    fun userCanceledAndAlreadyOwned() {
        assertEquals(BillingFailure.USER_CANCELED, BillingResponseMapper.failureOf(BillingResponseCode.USER_CANCELED))
        assertEquals(BillingFailure.ALREADY_OWNED, BillingResponseMapper.failureOf(BillingResponseCode.ITEM_ALREADY_OWNED))
    }

    @Test
    fun serviceProblems() {
        assertEquals(BillingFailure.SERVICE_DISCONNECTED, BillingResponseMapper.failureOf(BillingResponseCode.SERVICE_DISCONNECTED))
        assertEquals(BillingFailure.SERVICE_UNAVAILABLE, BillingResponseMapper.failureOf(BillingResponseCode.SERVICE_UNAVAILABLE))
        assertEquals(BillingFailure.BILLING_UNAVAILABLE, BillingResponseMapper.failureOf(BillingResponseCode.BILLING_UNAVAILABLE))
        assertEquals(BillingFailure.BILLING_UNAVAILABLE, BillingResponseMapper.failureOf(BillingResponseCode.FEATURE_NOT_SUPPORTED))
    }

    @Test
    fun networkDeveloperAndItemCodes() {
        assertEquals(BillingFailure.NETWORK, BillingResponseMapper.failureOf(BillingResponseCode.NETWORK_ERROR))
        assertEquals(BillingFailure.DEVELOPER_ERROR, BillingResponseMapper.failureOf(BillingResponseCode.DEVELOPER_ERROR))
        assertEquals(BillingFailure.ITEM_UNAVAILABLE, BillingResponseMapper.failureOf(BillingResponseCode.ITEM_UNAVAILABLE))
    }

    @Test
    fun everythingElseIsAGenericError() {
        assertEquals(BillingFailure.ERROR, BillingResponseMapper.failureOf(BillingResponseCode.ERROR))
        assertEquals(BillingFailure.ERROR, BillingResponseMapper.failureOf(BillingResponseCode.ITEM_NOT_OWNED))
        assertEquals(BillingFailure.ERROR, BillingResponseMapper.failureOf(9_999))
    }

    @Test
    fun purchaseStateMapping() {
        assertEquals(PurchaseStatus.PURCHASED, BillingResponseMapper.statusOf(Purchase.PurchaseState.PURCHASED))
        assertEquals(PurchaseStatus.PENDING, BillingResponseMapper.statusOf(Purchase.PurchaseState.PENDING))
        assertEquals(PurchaseStatus.UNSPECIFIED, BillingResponseMapper.statusOf(Purchase.PurchaseState.UNSPECIFIED_STATE))
        assertEquals(PurchaseStatus.UNSPECIFIED, BillingResponseMapper.statusOf(42))
    }

    @Test
    fun connectRetryOnlyForTransientCodes() {
        assertTrue(BillingResponseMapper.isRetryableConnect(BillingResponseCode.SERVICE_UNAVAILABLE))
        assertTrue(BillingResponseMapper.isRetryableConnect(BillingResponseCode.SERVICE_DISCONNECTED))
        assertTrue(BillingResponseMapper.isRetryableConnect(BillingResponseCode.NETWORK_ERROR))
        assertFalse(BillingResponseMapper.isRetryableConnect(BillingResponseCode.BILLING_UNAVAILABLE))
        assertFalse(BillingResponseMapper.isRetryableConnect(BillingResponseCode.DEVELOPER_ERROR))
        assertFalse(BillingResponseMapper.isRetryableConnect(BillingResponseCode.FEATURE_NOT_SUPPORTED))
    }

    @Test
    fun retryableFlagOnFailures() {
        assertTrue(BillingFailure.NETWORK.isRetryable)
        assertFalse(BillingFailure.USER_CANCELED.isRetryable)
        assertFalse(BillingFailure.DEVELOPER_ERROR.isRetryable)
    }
}
