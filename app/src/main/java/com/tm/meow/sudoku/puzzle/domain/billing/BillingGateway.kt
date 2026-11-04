package com.tm.meow.sudoku.puzzle.domain.billing

import kotlinx.coroutines.flow.Flow

/**
 * The Play Billing boundary. Everything Play-specific (BillingClient, Activity, ProductDetails, response codes) lives
 * behind this; the impl is data/billing/PlayBillingGateway. Connection handling (connect, backoff, reconnect + one retry
 * on SERVICE_DISCONNECTED) is the impl's job: callers just get a [StoreResult]. Never throws (except cancellation).
 */
interface BillingGateway {
    /** Purchases reported by Play's PurchasesUpdatedListener (app-initiated or not). Failures include USER_CANCELED. */
    val purchaseUpdates: Flow<StoreResult<List<StorePurchase>>>

    /** Localized prices for every [ShopProduct] Play knows. Also caches the details needed by [launchPurchase]. */
    suspend fun queryProducts(): StoreResult<Map<String, ProductPrice>>

    /** Price details cached by the last [queryProducts] (null when not loaded). Analytics revenue only. */
    fun cachedPrice(productId: String): ProductPrice? = null

    /** Owned purchases, subscriptions + in-app together. Failure if EITHER query fails (never a partial list). */
    suspend fun queryPurchases(): StoreResult<List<StorePurchase>>

    /** Opens the Play purchase sheet on the current foreground Activity. Success = sheet launched (result arrives on [purchaseUpdates]). */
    suspend fun launchPurchase(productId: String): StoreResult<Unit>

    suspend fun consume(purchaseToken: String): StoreResult<Unit>

    suspend fun acknowledge(purchaseToken: String): StoreResult<Unit>
}
