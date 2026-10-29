package com.threetech.catpuzzle.data.billing

import com.threetech.catpuzzle.domain.billing.AdsRemovedRepository
import com.threetech.catpuzzle.domain.billing.BillingFailure
import com.threetech.catpuzzle.domain.billing.BillingGateway
import com.threetech.catpuzzle.domain.billing.CatalogState
import com.threetech.catpuzzle.domain.billing.EntitlementPolicy
import com.threetech.catpuzzle.domain.billing.ProductKind
import com.threetech.catpuzzle.domain.billing.PurchaseEvent
import com.threetech.catpuzzle.domain.billing.PurchaseOutcome
import com.threetech.catpuzzle.domain.billing.PurchaseProcessor
import com.threetech.catpuzzle.domain.billing.PurchaseStatus
import com.threetech.catpuzzle.domain.billing.ShopProduct
import com.threetech.catpuzzle.domain.billing.StorePurchase
import com.threetech.catpuzzle.domain.billing.StoreResult
import com.threetech.catpuzzle.domain.repository.ShopRepository
import com.threetech.catpuzzle.domain.time.TimeProvider
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * App-scoped billing orchestration. Pure Kotlin on top of [BillingGateway] (so it is unit-tested with a fake gateway);
 * provided as a singleton by di/BillingModule with an app-lifetime [scope].
 *
 * Purchase processing and syncs share one mutex: the same purchase is never processed twice at once, and the
 * entitlement cache is written from a consistent view. Entitlement is applied from the FULL Play query on every
 * successful sync; a failed query never touches the cache (so a flaky network cannot turn ads on for a paying user).
 */
class ShopRepositoryImpl(
    private val gateway: BillingGateway,
    private val processor: PurchaseProcessor,
    private val adsRemoved: AdsRemovedRepository,
    private val time: TimeProvider,
    private val scope: CoroutineScope,
) : ShopRepository {

    private val started = AtomicBoolean(false)
    private val mutex = Mutex()

    private val _catalog = MutableStateFlow<CatalogState>(CatalogState.Loading)
    override val catalog: StateFlow<CatalogState> = _catalog

    private val _events = MutableSharedFlow<PurchaseEvent>(
        extraBufferCapacity = EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: Flow<PurchaseEvent> = _events.asSharedFlow()

    override fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch { gateway.purchaseUpdates.collect { handleUpdate(it) } }
        scope.launch { syncWithRetry() }
    }

    override suspend fun loadCatalog() {
        if (_catalog.value !is CatalogState.Ready) _catalog.value = CatalogState.Loading
        when (val result = gateway.queryProducts()) {
            is StoreResult.Success ->
                _catalog.value =
                    if (result.value.isEmpty()) CatalogState.Unavailable(BillingFailure.ITEM_UNAVAILABLE)
                    else CatalogState.Ready(result.value)
            // Keep earlier prices when a refresh fails: they are still the localized Play prices.
            is StoreResult.Failure ->
                if (_catalog.value !is CatalogState.Ready) _catalog.value = CatalogState.Unavailable(result.failure)
        }
    }

    override suspend fun buy(productId: String): StoreResult<Unit> {
        val product = ShopProduct.fromId(productId) ?: return fail(BillingFailure.DEVELOPER_ERROR)
        val owned = adsRemoved.entitlement.value
        val alreadyOwned = when (product.kind) {
            ProductKind.NON_CONSUMABLE -> owned.foreverOwned
            ProductKind.SUBSCRIPTION -> owned.monthlyActive || owned.foreverOwned
            ProductKind.CONSUMABLE -> false
        }
        if (alreadyOwned) return fail(BillingFailure.ALREADY_OWNED)

        var result = gateway.launchPurchase(productId)
        // The gateway reconnects after a dropped service; one more try covers the call that hit the drop.
        if (result is StoreResult.Failure && result.failure == BillingFailure.SERVICE_DISCONNECTED) {
            result = gateway.launchPurchase(productId)
        }
        if (result is StoreResult.Failure) {
            emit(PurchaseEvent.Failed(result.failure))
            // Play says we own it but our cache disagrees: re-query so the entitlement / consumable is applied.
            if (result.failure == BillingFailure.ALREADY_OWNED) scope.launch { refresh() }
        }
        return result
    }

    override suspend fun restore() {
        when (val outcome = sync()) {
            is SyncOutcome.Failed -> emit(PurchaseEvent.Failed(outcome.failure))
            is SyncOutcome.Done ->
                // Specific events (pack granted / ads removal active) already told the story when there were any.
                if (outcome.events == 0) {
                    emit(if (outcome.ownedCount > 0) PurchaseEvent.Restored else PurchaseEvent.NothingToRestore)
                }
        }
    }

    override suspend fun refresh() {
        sync()
    }

    // ---------------------------------------------------------------- internals

    private sealed interface SyncOutcome {
        data class Done(val ownedCount: Int, val events: Int) : SyncOutcome
        data class Failed(val failure: BillingFailure) : SyncOutcome
    }

    /** Re-query Play, process every purchase, then apply the entitlement derived from the FULL list. */
    private suspend fun sync(): SyncOutcome = mutex.withLock {
        when (val query = gateway.queryPurchases()) {
            is StoreResult.Failure -> SyncOutcome.Failed(query.failure)
            is StoreResult.Success -> {
                val purchases = query.value
                val outcomes = processor.process(purchases)
                adsRemoved.apply(EntitlementPolicy.derive(purchases, time.nowMillis()))
                outcomes.forEach { emit(it.toEvent()) }
                SyncOutcome.Done(ownedCount = purchases.countRecognisedPurchased(), events = outcomes.size)
            }
        }
    }

    /** First sync at app start: a transient failure (service busy, no network yet) is retried a few times with backoff. */
    private suspend fun syncWithRetry() {
        var attempt = 0
        while (true) {
            val outcome = sync()
            if (outcome !is SyncOutcome.Failed || !outcome.failure.isRetryable || attempt >= RETRY_DELAYS_MS.size) return
            delay(RETRY_DELAYS_MS[attempt++])
        }
    }

    private suspend fun handleUpdate(update: StoreResult<List<StorePurchase>>) {
        when (update) {
            is StoreResult.Failure -> {
                emit(PurchaseEvent.Failed(update.failure))
                if (update.failure == BillingFailure.ALREADY_OWNED) refresh()
            }
            is StoreResult.Success -> mutex.withLock {
                val purchases = update.value
                val outcomes = processor.process(purchases)
                // An update lists only the changed purchases: never remove anything, only add (full sync corrects later).
                val derived = EntitlementPolicy.derive(purchases, time.nowMillis())
                if (derived.monthlyActive || derived.foreverOwned) {
                    adsRemoved.apply(EntitlementPolicy.merge(adsRemoved.entitlement.value, derived))
                }
                outcomes.forEach { emit(it.toEvent()) }
            }
        }
    }

    private fun fail(failure: BillingFailure): StoreResult<Unit> {
        emit(PurchaseEvent.Failed(failure))
        return StoreResult.Failure(failure)
    }

    private fun emit(event: PurchaseEvent) {
        _events.tryEmit(event)
    }

    private fun PurchaseOutcome.toEvent(): PurchaseEvent = when (this) {
        is PurchaseOutcome.PackGranted -> PurchaseEvent.PackGranted(product)
        is PurchaseOutcome.AdsRemovalActivated -> PurchaseEvent.AdsRemovalActive(product)
        is PurchaseOutcome.Pending -> PurchaseEvent.Pending(product)
    }

    private fun List<StorePurchase>.countRecognisedPurchased(): Int = count { p ->
        p.status == PurchaseStatus.PURCHASED && p.productIds.any { ShopProduct.fromId(it) != null }
    }

    private companion object {
        const val EVENT_BUFFER = 16
        val RETRY_DELAYS_MS = longArrayOf(5_000L, 20_000L, 60_000L)
    }
}
