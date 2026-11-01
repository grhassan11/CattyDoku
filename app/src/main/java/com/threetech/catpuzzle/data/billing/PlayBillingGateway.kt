package com.threetech.catpuzzle.data.billing

import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.threetech.catpuzzle.BuildConfig
import com.threetech.catpuzzle.data.platform.CurrentActivityProvider
import com.threetech.catpuzzle.domain.billing.BillingFailure
import com.threetech.catpuzzle.domain.billing.BillingGateway
import com.threetech.catpuzzle.domain.billing.ProductKind
import com.threetech.catpuzzle.domain.billing.ProductPrice
import com.threetech.catpuzzle.domain.billing.ShopProduct
import com.threetech.catpuzzle.domain.billing.StorePurchase
import com.threetech.catpuzzle.domain.billing.StoreResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Google Play Billing (billing-ktx) behind [BillingGateway]. The only class that touches BillingClient / Activity.
 *
 * Connection manager: one BillingClient for the process, created lazily, with `enableAutoServiceReconnection()` (Play
 * re-binds after a dropped service on the next call) PLUS explicit [ensureConnected]: serialized, up to
 * [CONNECT_ATTEMPTS] tries with exponential backoff for transient failures, immediate give-up for permanent ones
 * (billing unavailable, developer error). Every operation goes through [call]: connect, run, and when the result is
 * SERVICE_DISCONNECTED reconnect and run once more.
 *
 * Pending purchases: enabled for one-time products (mandatory flag); the app never grants a PENDING purchase.
 * No obfuscated account / profile ids: the app has no accounts. Client-side only: purchases are NOT verified on a server.
 */
@Singleton
class PlayBillingGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activities: CurrentActivityProvider,
) : BillingGateway {

    private val updates = MutableSharedFlow<StoreResult<List<StorePurchase>>>(
        extraBufferCapacity = UPDATE_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val purchaseUpdates: Flow<StoreResult<List<StorePurchase>>> = updates.asSharedFlow()

    private val listener = PurchasesUpdatedListener { result, purchases ->
        val code = result.responseCode
        val update: StoreResult<List<StorePurchase>> =
            if (code == BillingResponseCode.OK) {
                StoreResult.Success(purchases.orEmpty().map { it.toStorePurchase() })
            } else {
                logDebug("onPurchasesUpdated code=$code msg=${result.debugMessage}")
                StoreResult.Failure(BillingResponseMapper.failureOf(code))
            }
        updates.tryEmit(update)
    }

    private val client: BillingClient by lazy {
        BillingClient.newBuilder(context)
            .setListener(listener)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
    }

    private val connectMutex = Mutex()

    /** Latest ProductDetails per product id (from [queryProducts]); needed to launch a purchase. */
    private val details = ConcurrentHashMap<String, ProductDetails>()

    // ---------------------------------------------------------------- BillingGateway

    override suspend fun queryProducts(): StoreResult<Map<String, ProductPrice>> = call {
        val inapp = fetchDetails(ProductKind.CONSUMABLE, BillingClient.ProductType.INAPP)
        val subs = fetchDetails(ProductKind.SUBSCRIPTION, BillingClient.ProductType.SUBS)
        // Both queries failed: report the first failure. One failed: still show what we got.
        if (inapp is StoreResult.Failure && subs is StoreResult.Failure) return@call inapp
        val prices = LinkedHashMap<String, ProductPrice>()
        for (result in listOf(inapp, subs)) {
            if (result !is StoreResult.Success) continue
            for (d in result.value) {
                details[d.productId] = d
                val price = formattedPriceOf(d) ?: continue
                prices[d.productId] = ProductPrice(d.productId, price)
            }
        }
        StoreResult.Success(prices)
    }

    override suspend fun queryPurchases(): StoreResult<List<StorePurchase>> = call {
        val all = ArrayList<StorePurchase>()
        for (type in listOf(BillingClient.ProductType.INAPP, BillingClient.ProductType.SUBS)) {
            val result = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build())
            val code = result.billingResult.responseCode
            if (code != BillingResponseCode.OK) return@call failure(result.billingResult)
            all += result.purchasesList.orEmpty().map { it.toStorePurchase() }
        }
        StoreResult.Success(all)
    }

    override suspend fun launchPurchase(productId: String): StoreResult<Unit> = call {
        var d = details[productId]
        if (d == null) {
            // Catalog not loaded yet (or the product was missing): one fresh query before giving up.
            val q = ShopProduct.fromId(productId)?.let { fetchProductsOfType(it) }
            d = q?.firstOrNull { it.productId == productId }?.also { details[productId] = it }
        }
        if (d == null) return@call StoreResult.Failure(BillingFailure.ITEM_UNAVAILABLE)

        val activity = activities.current() ?: return@call StoreResult.Failure(BillingFailure.ERROR)
        val paramsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d)
        if (d.productType == BillingClient.ProductType.SUBS) {
            val offerToken = chooseSubscriptionOffer(d)?.offerToken
                ?: return@call StoreResult.Failure(BillingFailure.ITEM_UNAVAILABLE)
            paramsBuilder.setOfferToken(offerToken)
        } else {
            // Billing 8+: a one-time product can carry several purchase options / offers; the token picks one.
            // Single-offer products need none. The first option is the default (non-consumable + consumable packs).
            d.oneTimePurchaseOfferDetailsList?.takeIf { it.size > 1 }?.firstOrNull()?.offerToken
                ?.let { paramsBuilder.setOfferToken(it) }
        }
        val flow = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(paramsBuilder.build())).build()
        // launchBillingFlow must run on the UI thread.
        val result = withContext(Dispatchers.Main.immediate) { client.launchBillingFlow(activity, flow) }
        if (result.responseCode == BillingResponseCode.OK) StoreResult.Success(Unit) else failure(result)
    }

    override suspend fun consume(purchaseToken: String): StoreResult<Unit> = call {
        val result = client.consumePurchase(ConsumeParams.newBuilder().setPurchaseToken(purchaseToken).build())
        if (result.billingResult.responseCode == BillingResponseCode.OK) StoreResult.Success(Unit) else failure(result.billingResult)
    }

    override suspend fun acknowledge(purchaseToken: String): StoreResult<Unit> = call {
        val result = client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchaseToken).build())
        if (result.responseCode == BillingResponseCode.OK) StoreResult.Success(Unit) else failure(result)
    }

    // ---------------------------------------------------------------- product details

    private suspend fun fetchDetails(kind: ProductKind, type: String): StoreResult<List<ProductDetails>> {
        val products = ShopProduct.ofKind(kind).let { list ->
            // CONSUMABLE + NON_CONSUMABLE are both "inapp": query them together.
            if (type == BillingClient.ProductType.INAPP) list + ShopProduct.ofKind(ProductKind.NON_CONSUMABLE) else list
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                products.map {
                    QueryProductDetailsParams.Product.newBuilder().setProductId(it.id).setProductType(type).build()
                },
            )
            .build()
        val result = client.queryProductDetails(params)
        val code = result.billingResult.responseCode
        return if (code == BillingResponseCode.OK) {
            StoreResult.Success(result.productDetailsList.orEmpty())
        } else {
            logDebug("queryProductDetails($type) code=$code msg=${result.billingResult.debugMessage}")
            failure(result.billingResult)
        }
    }

    private suspend fun fetchProductsOfType(product: ShopProduct): List<ProductDetails>? {
        val type = if (product.kind == ProductKind.SUBSCRIPTION) BillingClient.ProductType.SUBS else BillingClient.ProductType.INAPP
        val result = fetchDetails(product.kind, type)
        return (result as? StoreResult.Success)?.value
    }

    /** Base plan without a promo offer first (no free-trial / intro price in the headline), else the first offer. */
    private fun chooseSubscriptionOffer(d: ProductDetails): ProductDetails.SubscriptionOfferDetails? {
        val offers = d.subscriptionOfferDetails.orEmpty()
        return offers.firstOrNull { it.basePlanId == ShopProduct.MONTHLY_BASE_PLAN_ID && it.offerId == null }
            ?: offers.firstOrNull { it.offerId == null }
            ?: offers.firstOrNull()
    }

    /** Localized price string exactly as Play formats it (subscription: the recurring phase of the chosen offer). */
    private fun formattedPriceOf(d: ProductDetails): String? =
        if (d.productType == BillingClient.ProductType.SUBS) {
            chooseSubscriptionOffer(d)?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
        } else {
            (d.oneTimePurchaseOfferDetails ?: d.oneTimePurchaseOfferDetailsList?.firstOrNull())?.formattedPrice
        }

    // ---------------------------------------------------------------- connection

    /** Connect (if needed), run [op]; on SERVICE_DISCONNECTED reconnect and run it once more. Never throws. */
    private suspend fun <T> call(op: suspend () -> StoreResult<T>): StoreResult<T> {
        try {
            ensureConnected()?.let { return StoreResult.Failure(it) }
            var result = op()
            if (result is StoreResult.Failure && result.failure == BillingFailure.SERVICE_DISCONNECTED) {
                ensureConnected()?.let { return StoreResult.Failure(it) }
                result = op()
            }
            return result
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "billing call failed", t)
            return StoreResult.Failure(BillingFailure.ERROR)
        }
    }

    /** Null when ready, else why not. Serialized so concurrent callers share one connection attempt. */
    private suspend fun ensureConnected(): BillingFailure? {
        if (client.isReady) return null
        return connectMutex.withLock {
            if (client.isReady) return@withLock null
            var last = BillingResponseCode.SERVICE_UNAVAILABLE
            for (attempt in 0 until CONNECT_ATTEMPTS) {
                // A connect started elsewhere (auto reconnect) may be in flight: give it a moment first.
                if (client.connectionState == BillingClient.ConnectionState.CONNECTING) {
                    withTimeoutOrNull(CONNECTING_WAIT_MS) { while (!client.isReady) delay(POLL_MS) }
                    if (client.isReady) return@withLock null
                }
                last = startConnection()
                if (last == BillingResponseCode.OK) return@withLock null
                if (!BillingResponseMapper.isRetryableConnect(last)) break
                delay(BACKOFF_BASE_MS shl attempt)
            }
            logDebug("connect failed code=$last")
            BillingResponseMapper.failureOf(last)
        }
    }

    private suspend fun startConnection(): Int = suspendCancellableCoroutine { cont ->
        client.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (cont.isActive) cont.resume(billingResult.responseCode)
                }

                override fun onBillingServiceDisconnected() {
                    // Auto reconnection is on. Only unblock a setup that is still waiting.
                    if (cont.isActive) cont.resume(BillingResponseCode.SERVICE_DISCONNECTED)
                }
            },
        )
    }

    // ---------------------------------------------------------------- helpers

    private fun failure(result: BillingResult): StoreResult.Failure =
        StoreResult.Failure(BillingResponseMapper.failureOf(result.responseCode))

    private fun Purchase.toStorePurchase() = StorePurchase(
        productIds = products,
        purchaseToken = purchaseToken,
        orderId = orderId,
        status = BillingResponseMapper.statusOf(purchaseState),
        acknowledged = isAcknowledged,
        autoRenewing = isAutoRenewing,
    )

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    private companion object {
        const val TAG = "PlayBilling"
        const val UPDATE_BUFFER = 16
        const val CONNECT_ATTEMPTS = 3
        const val BACKOFF_BASE_MS = 500L
        const val CONNECTING_WAIT_MS = 4_000L
        const val POLL_MS = 100L
    }
}
