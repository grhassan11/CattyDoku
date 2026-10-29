package com.threetech.catpuzzle.domain.billing

import com.threetech.catpuzzle.domain.model.Booster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopProductTest {

    @Test
    fun idsAreUniqueAndExactlyThePlayConsoleList() {
        val ids = ShopProduct.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(
            setOf(
                "remove_ads_monthly", "remove_ads_forever",
                "cat_pack_5", "cat_pack_20", "cat_pack_50",
                "hint_pack_5", "hint_pack_20", "hint_pack_50",
                "mouse_pack_5", "mouse_pack_20", "mouse_pack_50",
            ),
            ids.toSet(),
        )
    }

    @Test
    fun grantAmountMatchesTheNumberInTheId() {
        for (p in ShopProduct.ofKind(ProductKind.CONSUMABLE)) {
            assertEquals(p.id, p.id.substringAfterLast('_').toInt(), p.amount)
            assertNotNull(p.id, p.booster)
        }
    }

    @Test
    fun grantTargetsTheBoosterNamedInTheId() {
        assertEquals(Booster.CAT, ShopProduct.fromId("cat_pack_20")?.booster)
        assertEquals(Booster.HINT, ShopProduct.fromId("hint_pack_50")?.booster)
        assertEquals(Booster.MOUSE, ShopProduct.fromId("mouse_pack_5")?.booster)
        assertEquals(5, ShopProduct.fromId("cat_pack_5")?.amount)
        assertEquals(20, ShopProduct.fromId("hint_pack_20")?.amount)
        assertEquals(50, ShopProduct.fromId("mouse_pack_50")?.amount)
    }

    @Test
    fun kindsMatchThePlayProductTypes() {
        assertEquals(ProductKind.SUBSCRIPTION, ShopProduct.REMOVE_ADS_MONTHLY.kind)
        assertEquals(ProductKind.NON_CONSUMABLE, ShopProduct.REMOVE_ADS_FOREVER.kind)
        assertEquals(9, ShopProduct.ofKind(ProductKind.CONSUMABLE).size)
        assertTrue(ShopProduct.REMOVE_ADS_MONTHLY.isAdsRemoval)
        assertTrue(ShopProduct.REMOVE_ADS_FOREVER.isAdsRemoval)
        assertEquals(0, ShopProduct.REMOVE_ADS_FOREVER.amount)
        assertNull(ShopProduct.REMOVE_ADS_FOREVER.booster)
    }

    @Test
    fun packsOfAreAscending5_20_50() {
        for (b in Booster.entries) {
            assertEquals(listOf(5, 20, 50), ShopProduct.packsOf(b).map { it.amount })
        }
    }

    @Test
    fun unknownIdIsNull() {
        assertNull(ShopProduct.fromId("gold_pack_1"))
        assertNull(ShopProduct.fromId(""))
    }
}
