package com.threetech.catpuzzle.domain.billing

/**
 * What the player owns on the ads-removal side. [verifiedAtMs] = wall clock of the last SUCCESSFUL Play query that
 * produced it (0 = never verified). "Ads removed" = monthlyActive OR foreverOwned (see [EntitlementPolicy.isAdsRemoved]).
 */
data class Entitlement(
    val monthlyActive: Boolean = false,
    val foreverOwned: Boolean = false,
    val verifiedAtMs: Long = 0L,
) {
    companion object {
        val NONE = Entitlement()
    }
}

/**
 * Pure entitlement rules. Source of truth = Play query ([derive]); the DataStore cache only answers before / without
 * billing:
 *  - forever owned: trusted indefinitely until a successful query says otherwise (refund / revoke is rare).
 *  - monthly: trusted for [MONTHLY_CACHE_TTL_MS] after the last successful verification (sub period is a month; one
 *    week offline grace), then treated as not active until Play confirms again.
 */
object EntitlementPolicy {
    const val MONTHLY_CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000

    /** Tolerated backwards clock step (NTP / manual) before a cached monthly entitlement is distrusted. */
    const val CLOCK_SKEW_TOLERANCE_MS = 5L * 60 * 1000

    /** Entitlement from a FULL purchase list. Only PURCHASED counts: PENDING / unspecified never grant. */
    fun derive(purchases: List<StorePurchase>, nowMs: Long): Entitlement {
        var monthly = false
        var forever = false
        for (p in purchases) {
            if (p.status != PurchaseStatus.PURCHASED) continue
            for (id in p.productIds) {
                when (ShopProduct.fromId(id)) {
                    ShopProduct.REMOVE_ADS_MONTHLY -> monthly = true
                    ShopProduct.REMOVE_ADS_FOREVER -> forever = true
                    else -> Unit
                }
            }
        }
        return Entitlement(monthlyActive = monthly, foreverOwned = forever, verifiedAtMs = nowMs)
    }

    /** Add a partial update (a purchase event) to what is already known: never removes, refreshes the stamp. */
    fun merge(current: Entitlement, update: Entitlement): Entitlement = Entitlement(
        monthlyActive = current.monthlyActive || update.monthlyActive,
        foreverOwned = current.foreverOwned || update.foreverOwned,
        verifiedAtMs = maxOf(current.verifiedAtMs, update.verifiedAtMs),
    )

    /** True when ads must be suppressed right now, given the (possibly cached) entitlement. */
    fun isAdsRemoved(e: Entitlement, nowMs: Long): Boolean {
        if (e.foreverOwned) return true
        if (!e.monthlyActive) return false
        val age = nowMs - e.verifiedAtMs
        return age in -CLOCK_SKEW_TOLERANCE_MS..MONTHLY_CACHE_TTL_MS
    }
}
