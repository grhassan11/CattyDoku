package com.tm.meow.sudoku.puzzle.ui.screens.shop

import com.tm.meow.sudoku.puzzle.domain.billing.BillingFailure
import com.tm.meow.sudoku.puzzle.domain.billing.CatalogState
import com.tm.meow.sudoku.puzzle.domain.billing.Entitlement
import com.tm.meow.sudoku.puzzle.domain.billing.ProductPrice
import com.tm.meow.sudoku.puzzle.domain.billing.ShopProduct
import com.tm.meow.sudoku.puzzle.domain.model.Booster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopUiMapperTest {

    private fun ready(vararg p: Pair<ShopProduct, String>) =
        CatalogState.Ready(p.associate { (product, price) -> product.id to ProductPrice(product.id, price) })

    private val allPriced = ready(*ShopProduct.entries.map { it to "P-${it.id}" }.toTypedArray())

    private fun map(
        catalog: CatalogState = allPriced,
        entitlement: Entitlement = Entitlement.NONE,
        balances: Map<Booster, Int> = emptyMap(),
        busy: Boolean = false,
        restoring: Boolean = false,
        adsExposed: Boolean = true,
    ) = ShopUiMapper.map(catalog, entitlement, balances, busy, restoring, adsExposed)

    @Test
    fun pricesAreExactlyThePlayStrings() {
        val s = map()
        assertEquals(CatalogStatus.READY, s.catalog)
        assertEquals("P-remove_ads_monthly", s.monthly.price)
        assertEquals("P-remove_ads_forever", s.forever.price)
        assertEquals(listOf("P-cat_pack_5", "P-cat_pack_20", "P-cat_pack_50"), s.sections[0].packs.map { it.price })
    }

    @Test
    fun loadingShowsNoPricesAndDisablesEveryBuy() {
        val s = map(catalog = CatalogState.Loading)
        assertEquals(CatalogStatus.LOADING, s.catalog)
        assertNull(s.monthly.price)
        assertFalse(s.monthly.buyEnabled || s.forever.buyEnabled)
        assertTrue(s.sections.flatMap { it.packs }.all { it.price == null && !it.buyEnabled })
    }

    @Test
    fun unavailableDisablesEveryBuy() {
        val s = map(catalog = CatalogState.Unavailable(BillingFailure.BILLING_UNAVAILABLE))
        assertEquals(CatalogStatus.UNAVAILABLE, s.catalog)
        assertFalse(s.monthly.buyEnabled || s.forever.buyEnabled)
        assertTrue(s.sections.flatMap { it.packs }.none { it.buyEnabled })
    }

    @Test
    fun aMissingProductDisablesOnlyItself() {
        val s = map(catalog = ready(ShopProduct.CAT_PACK_5 to "$1", ShopProduct.REMOVE_ADS_FOREVER to "$9"))
        val cat = s.sections.first { it.booster == Booster.CAT }.packs
        assertTrue(cat[0].buyEnabled)
        assertFalse(cat[1].buyEnabled)
        assertNull(cat[1].price)
        assertTrue(s.forever.buyEnabled)
        assertFalse(s.monthly.buyEnabled)
    }

    @Test
    fun busyDisablesBuying() {
        val s = map(busy = true)
        assertTrue(s.busy)
        assertFalse(s.monthly.buyEnabled || s.forever.buyEnabled)
        assertTrue(s.sections.flatMap { it.packs }.none { it.buyEnabled })
    }

    @Test
    fun restoringIsSeparateFromBusy() {
        assertFalse(map(busy = true).restoring)
        val s = map(busy = true, restoring = true)
        assertTrue(s.restoring && s.busy)
        assertTrue(s.sections.flatMap { it.packs }.none { it.buyEnabled })
    }

    @Test
    fun threeSectionsInOrderWithBadgesAndLiveBalances() {
        val s = map(balances = mapOf(Booster.CAT to 7, Booster.HINT to 3))
        assertEquals(listOf(Booster.CAT, Booster.HINT, Booster.MOUSE), s.sections.map { it.booster })
        assertEquals(listOf(7, 3, 0), s.sections.map { it.balance })
        for (section in s.sections) {
            assertEquals(listOf(5, 20, 50), section.packs.map { it.amount })
            assertEquals(listOf(PackBadge.NONE, PackBadge.POPULAR, PackBadge.BEST_VALUE), section.packs.map { it.badge })
        }
    }

    @Test
    fun activeMonthlyShowsActiveAndForeverStaysBuyable() {
        val s = map(entitlement = Entitlement(monthlyActive = true, verifiedAtMs = 1L))
        assertEquals(OfferOwnership.ACTIVE, s.monthly.ownership)
        assertFalse(s.monthly.buyEnabled)
        assertEquals(OfferOwnership.AVAILABLE, s.forever.ownership)
        assertTrue("upgrade to forever is allowed", s.forever.buyEnabled)
    }

    @Test
    fun foreverOwnedShowsOwnedAndDisablesBothOffers() {
        val s = map(entitlement = Entitlement(foreverOwned = true, verifiedAtMs = 1L))
        assertEquals(OfferOwnership.OWNED, s.forever.ownership)
        assertEquals(OfferOwnership.NOT_NEEDED, s.monthly.ownership)
        assertFalse(s.forever.buyEnabled || s.monthly.buyEnabled)
    }

    @Test
    fun foreverOwnedAndMonthlyStillActiveShowsBothAsHeld() {
        val s = map(entitlement = Entitlement(monthlyActive = true, foreverOwned = true, verifiedAtMs = 1L))
        assertEquals(OfferOwnership.NOT_NEEDED, s.monthly.ownership)
        assertTrue(s.monthly.canManage) // live sub can still be cancelled
        assertFalse(s.monthly.buyEnabled)
        assertEquals(OfferOwnership.OWNED, s.forever.ownership)
    }

    @Test
    fun removeAdsCardHiddenBeforeAdsAppear() {
        val s = map(adsExposed = false)
        assertFalse(s.showRemoveAds)
        // packs are unaffected by the gate
        assertEquals(3, s.sections.size)
        assertTrue(s.sections.flatMap { it.packs }.all { it.buyEnabled })
    }

    @Test
    fun removeAdsCardShownOncePastAdFreeLevels() {
        assertTrue(map(adsExposed = true).showRemoveAds)
    }

    @Test
    fun existingBuyersKeepTheCardBelowTheGate() {
        assertTrue(map(adsExposed = false, entitlement = Entitlement(foreverOwned = true, verifiedAtMs = 1L)).showRemoveAds)
        assertTrue(map(adsExposed = false, entitlement = Entitlement(monthlyActive = true, verifiedAtMs = 1L)).showRemoveAds)
    }

    @Test
    fun initialStateHidesTheCard() {
        assertFalse(ShopUiMapper.initial().showRemoveAds)
    }

    @Test
    fun navArgRoundTrip() {
        for (b in Booster.entries) assertEquals(b, ShopUiMapper.boosterFromArg(ShopUiMapper.argFor(b)))
        assertNull(ShopUiMapper.boosterFromArg(null))
        assertNull(ShopUiMapper.boosterFromArg("gold"))
    }
}
