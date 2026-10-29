package com.threetech.catpuzzle.domain.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementPolicyTest {
    private val now = 10_000_000L

    @Test
    fun noPurchasesMeansNothing() {
        val e = EntitlementPolicy.derive(emptyList(), now)
        assertFalse(e.monthlyActive)
        assertFalse(e.foreverOwned)
        assertEquals(now, e.verifiedAtMs)
        assertFalse(EntitlementPolicy.isAdsRemoved(e, now))
    }

    @Test
    fun activeMonthlyRemovesAds() {
        val e = EntitlementPolicy.derive(listOf(purchase("remove_ads_monthly")), now)
        assertTrue(e.monthlyActive)
        assertFalse(e.foreverOwned)
        assertTrue(EntitlementPolicy.isAdsRemoved(e, now))
    }

    @Test
    fun foreverRemovesAds() {
        val e = EntitlementPolicy.derive(listOf(purchase("remove_ads_forever")), now)
        assertTrue(e.foreverOwned)
        assertTrue(EntitlementPolicy.isAdsRemoved(e, now))
    }

    @Test
    fun bothOwnedStillOneFlag() {
        val e = EntitlementPolicy.derive(
            listOf(purchase("remove_ads_monthly"), purchase("remove_ads_forever")),
            now,
        )
        assertTrue(e.monthlyActive && e.foreverOwned)
        assertTrue(EntitlementPolicy.isAdsRemoved(e, now))
    }

    @Test
    fun pendingNeverGrants() {
        val e = EntitlementPolicy.derive(
            listOf(
                purchase("remove_ads_forever", status = PurchaseStatus.PENDING),
                purchase("remove_ads_monthly", status = PurchaseStatus.UNSPECIFIED),
            ),
            now,
        )
        assertFalse(EntitlementPolicy.isAdsRemoved(e, now))
    }

    @Test
    fun packsAndUnknownProductsDoNotGrantAdsRemoval() {
        val e = EntitlementPolicy.derive(listOf(purchase("cat_pack_50"), purchase("mystery")), now)
        assertFalse(EntitlementPolicy.isAdsRemoved(e, now))
    }

    @Test
    fun foreverCacheNeverExpires() {
        val e = Entitlement(foreverOwned = true, verifiedAtMs = 1L)
        assertTrue(EntitlementPolicy.isAdsRemoved(e, now + 400L * 24 * 60 * 60 * 1000))
    }

    @Test
    fun monthlyCacheTrustedWithinTtlThenExpires() {
        val e = Entitlement(monthlyActive = true, verifiedAtMs = now)
        assertTrue(EntitlementPolicy.isAdsRemoved(e, now + EntitlementPolicy.MONTHLY_CACHE_TTL_MS))
        assertFalse(EntitlementPolicy.isAdsRemoved(e, now + EntitlementPolicy.MONTHLY_CACHE_TTL_MS + 1))
    }

    @Test
    fun monthlyNeverVerifiedIsNotTrusted() {
        assertFalse(EntitlementPolicy.isAdsRemoved(Entitlement(monthlyActive = true, verifiedAtMs = 0L), now))
    }

    @Test
    fun smallBackwardClockStepIsTolerated_bigOneIsNot() {
        val e = Entitlement(monthlyActive = true, verifiedAtMs = now)
        assertTrue(EntitlementPolicy.isAdsRemoved(e, now - 60_000L))
        assertFalse(EntitlementPolicy.isAdsRemoved(e, now - EntitlementPolicy.CLOCK_SKEW_TOLERANCE_MS - 1))
    }

    @Test
    fun mergeOnlyAddsAndKeepsNewestStamp() {
        val current = Entitlement(monthlyActive = true, foreverOwned = false, verifiedAtMs = 5L)
        val update = Entitlement(monthlyActive = false, foreverOwned = true, verifiedAtMs = 9L)
        val merged = EntitlementPolicy.merge(current, update)
        assertEquals(Entitlement(monthlyActive = true, foreverOwned = true, verifiedAtMs = 9L), merged)
        assertEquals(current, EntitlementPolicy.merge(current, Entitlement.NONE).copy(verifiedAtMs = 5L))
    }
}
