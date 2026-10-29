package com.threetech.catpuzzle.domain.billing

import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.domain.repository.PurchaseGrantRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Scriptable [BillingGateway]. Results are set per test; every call is recorded. */
class FakeBillingGateway : BillingGateway {
    val updates = MutableSharedFlow<StoreResult<List<StorePurchase>>>(extraBufferCapacity = 16)
    override val purchaseUpdates: Flow<StoreResult<List<StorePurchase>>> = updates

    var productsResult: StoreResult<Map<String, ProductPrice>> = StoreResult.Success(emptyMap())
    var purchasesResult: StoreResult<List<StorePurchase>> = StoreResult.Success(emptyList())
    var consumeResult: StoreResult<Unit> = StoreResult.Success(Unit)
    var acknowledgeResult: StoreResult<Unit> = StoreResult.Success(Unit)

    /** Queue of results for successive [launchPurchase] calls; empty queue = Success. */
    val launchResults = ArrayDeque<StoreResult<Unit>>()

    val launched = mutableListOf<String>()
    val consumed = mutableListOf<String>()
    val acknowledged = mutableListOf<String>()
    var queryPurchasesCalls = 0
        private set
    var queryProductsCalls = 0
        private set

    override suspend fun queryProducts(): StoreResult<Map<String, ProductPrice>> {
        queryProductsCalls++
        return productsResult
    }

    override suspend fun queryPurchases(): StoreResult<List<StorePurchase>> {
        queryPurchasesCalls++
        return purchasesResult
    }

    override suspend fun launchPurchase(productId: String): StoreResult<Unit> {
        launched += productId
        return launchResults.removeFirstOrNull() ?: StoreResult.Success(Unit)
    }

    override suspend fun consume(purchaseToken: String): StoreResult<Unit> {
        consumed += purchaseToken
        return consumeResult
    }

    override suspend fun acknowledge(purchaseToken: String): StoreResult<Unit> {
        acknowledged += purchaseToken
        return acknowledgeResult
    }
}

/** In-memory [PurchaseGrantRepository]: token ledger + a plain inventory map, same contract as the Room one. */
class FakePurchaseGrants : PurchaseGrantRepository {
    val tokens = LinkedHashSet<String>()
    val stock = HashMap<Booster, Int>()
    var grantCalls = 0
        private set

    override suspend fun grantOnce(purchaseToken: String, productId: String, booster: Booster, amount: Int): Boolean {
        grantCalls++
        if (!tokens.add(purchaseToken)) return false
        stock[booster] = (stock[booster] ?: 0) + amount
        return true
    }

    fun count(booster: Booster): Int = stock[booster] ?: 0
}

/** [AdsRemovedRepository] with the real expiry rules over an in-memory entitlement. */
class FakeAdsRemovedRepository(
    initial: Entitlement = Entitlement.NONE,
    private val nowMs: () -> Long = { 1_000L },
) : AdsRemovedRepository {
    private val entitlementState = MutableStateFlow(initial)
    private val adsRemovedState = MutableStateFlow(EntitlementPolicy.isAdsRemoved(initial, nowMs()))

    override val entitlement: StateFlow<Entitlement> = entitlementState
    override val adsRemoved: StateFlow<Boolean> = adsRemovedState
    var applyCalls = 0
        private set

    override suspend fun apply(entitlement: Entitlement) {
        applyCalls++
        entitlementState.value = entitlement
        adsRemovedState.value = EntitlementPolicy.isAdsRemoved(entitlement, nowMs())
    }
}

/** Convenience: a purchase of one product. */
fun purchase(
    productId: String,
    token: String = "token_$productId",
    status: PurchaseStatus = PurchaseStatus.PURCHASED,
    acknowledged: Boolean = false,
): StorePurchase = StorePurchase(
    productIds = listOf(productId),
    purchaseToken = token,
    orderId = if (status == PurchaseStatus.PENDING) null else "GPA.$token",
    status = status,
    acknowledged = acknowledged,
)
