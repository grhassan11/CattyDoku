package com.threetech.catpuzzle.domain.billing

import com.threetech.catpuzzle.domain.analytics.AnalyticsEvent
import com.threetech.catpuzzle.domain.analytics.AnalyticsLogger
import com.threetech.catpuzzle.domain.analytics.NoopAnalyticsLogger
import com.threetech.catpuzzle.domain.repository.PurchaseGrantRepository

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
) {
    suspend fun process(purchases: List<StorePurchase>): List<PurchaseOutcome> {
        val outcomes = ArrayList<PurchaseOutcome>()
        for (purchase in purchases) {
            for (productId in purchase.productIds) {
                val product = ShopProduct.fromId(productId) ?: continue
                when (purchase.status) {
                    PurchaseStatus.PENDING -> outcomes += PurchaseOutcome.Pending(product)
                    PurchaseStatus.UNSPECIFIED -> Unit
                    PurchaseStatus.PURCHASED -> handlePurchased(product, purchase)?.let {
                        outcomes += it
                        analytics.log(AnalyticsEvent.PurchaseSuccess(product.id)) // fresh grants / activations only
                    }
                }
            }
        }
        return outcomes
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
