package com.tm.meow.sudoku.puzzle.domain.billing

import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsEvent
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsUserProps
import com.tm.meow.sudoku.puzzle.domain.analytics.NoopAnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.repository.AppFlagsRepository
import com.tm.meow.sudoku.puzzle.domain.repository.PurchaseGrantRepository

/**
 * Turns Play purchases into effects. Rules (Play requires every purchase be consumed or acknowledged within 3 days):
 *  - PENDING: nothing is granted, nothing consumed / acknowledged. Reported as [PurchaseOutcome.Pending].
 *  - PURCHASED consumable: GRANT FIRST (atomic + idempotent by purchase token), THEN consume. A crash or a failed consume
 *    between the two leaves the purchase unconsumed; the next sync finds the token already granted (no double credit)
 *    and just consumes. A consume failure is not an error for the player: the credit is already in.
 *  - PURCHASED non-consumable / subscription: acknowledge when not yet acknowledged. The entitlement itself is applied
 *    by the caller from the full list ([EntitlementPolicy.derive]); an ack failure retries on the next sync.
 * Unknown product ids are ignored. Never throws except cancellation.
 */
class PurchaseProcessor(
    private val gateway: BillingGateway,
    private val grants: PurchaseGrantRepository,
    private val analytics: AnalyticsLogger = NoopAnalyticsLogger,
    /** Persists the sticky payer flag (regardless of analytics consent). Null = not persisted (tests). */
    private val flags: AppFlagsRepository? = null,
) {
    /** Pending purchase tokens already announced in this process: the "payment pending" message shows once per token. */
    private val announcedPending: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    suspend fun process(purchases: List<StorePurchase>): List<PurchaseOutcome> {
        val outcomes = ArrayList<PurchaseOutcome>()
        for (purchase in purchases) {
            for (productId in purchase.productIds) {
                val product = ShopProduct.fromId(productId) ?: continue
                when (purchase.status) {
                    PurchaseStatus.PENDING ->
                        if (announcedPending.add(purchase.purchaseToken)) outcomes += PurchaseOutcome.Pending(product)
                    PurchaseStatus.UNSPECIFIED -> Unit
                    PurchaseStatus.PURCHASED -> handlePurchased(product, purchase).also {
                        announcedPending.remove(purchase.purchaseToken) // resolved: forget it
                    }?.let {
                        outcomes += it
                        analytics.log(AnalyticsEvent.PurchaseSuccess(product.id)) // fresh grants / activations only
                        logRevenue(product, purchase)
                        markPayer()
                        analytics.setUserProperty(AnalyticsUserProps.PAYER, "1") // sticky in GA4; never cleared
                    }
                }
            }
        }
        return outcomes
    }

    /** GA4 `purchase` (revenue). Needs a numeric price (cached by the last catalog query) and the Play order id. */
    private fun logRevenue(product: ShopProduct, purchase: StorePurchase) {
        val price = gateway.cachedPrice(product.id) ?: return
        val value = price.value ?: return
        val orderId = purchase.orderId ?: return
        analytics.log(AnalyticsEvent.Purchase(product.id, value, price.currencyCode, orderId))
    }

    /** Sticky local payer flag, written whether or not analytics is allowed. Best effort. */
    private suspend fun markPayer() {
        val repo = flags ?: return
        try {
            repo.update { if (it.payer) it else it.copy(payer = true) }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (t: Throwable) {
            // best effort
        }
    }

    private suspend fun handlePurchased(product: ShopProduct, purchase: StorePurchase): PurchaseOutcome? =
        when (product.kind) {
            ProductKind.CONSUMABLE -> {
                val booster = requireNotNull(product.booster) { "consumable without booster: ${product.id}" }
                val credited = grants.grantOnce(purchase.purchaseToken, product.id, booster, product.amount)
                // Consume AFTER the credit is durable. Failure here is retried by the next sync.
                gateway.consume(purchase.purchaseToken)
                if (credited) PurchaseOutcome.PackGranted(product) else null
            }
            ProductKind.NON_CONSUMABLE, ProductKind.SUBSCRIPTION -> {
                if (purchase.acknowledged) {
                    null
                } else {
                    // Announce only a fresh (unacknowledged) purchase. The entitlement is already valid even if the ack
                    // call fails (it is retried by the next sync, and re-announced then).
                    gateway.acknowledge(purchase.purchaseToken)
                    PurchaseOutcome.AdsRemovalActivated(product)
                }
            }
        }
}
