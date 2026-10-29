package com.threetech.catpuzzle.ui.screens.shop

import com.threetech.catpuzzle.domain.billing.BillingFailure
import com.threetech.catpuzzle.domain.billing.CatalogState
import com.threetech.catpuzzle.domain.billing.Entitlement
import com.threetech.catpuzzle.domain.billing.ProductPrice
import com.threetech.catpuzzle.domain.billing.ShopProduct
import com.threetech.catpuzzle.domain.model.Booster
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
    ) = ShopUiMapper.map(catalog, entitlement, balances, busy)

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
        assertEquals(OfferOwnership.ACTIVE, s.monthly.ownership)
        assertEquals(OfferOwnership.OWNED, s.forever.ownership)
    }

    @Test
    fun navArgRoundTrip() {
        for (b in Booster.entries) assertEquals(b, ShopUiMapper.boosterFromArg(ShopUiMapper.argFor(b)))
        assertNull(ShopUiMapper.boosterFromArg(null))
        assertNull(ShopUiMapper.boosterFromArg("gold"))
    }
}
