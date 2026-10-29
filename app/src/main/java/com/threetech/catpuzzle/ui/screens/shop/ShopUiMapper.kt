package com.threetech.catpuzzle.ui.screens.shop

import com.threetech.catpuzzle.domain.billing.CatalogState
import com.threetech.catpuzzle.domain.billing.Entitlement
import com.threetech.catpuzzle.domain.billing.ShopProduct
import com.threetech.catpuzzle.domain.model.Booster

/** Pure domain -> UI mapping for the Shop (unit-tested). Prices come ONLY from [CatalogState.Ready]; nothing is hard-coded. */
object ShopUiMapper {

    /** Section order on screen. */
    val SECTION_ORDER: List<Booster> = listOf(Booster.CAT, Booster.HINT, Booster.MOUSE)

    fun initial(): ShopUiState = map(CatalogState.Loading, Entitlement.NONE, emptyMap(), busy = false)

    fun map(
        catalog: CatalogState,
        entitlement: Entitlement,
        balances: Map<Booster, Int>,
        busy: Boolean,
    ): ShopUiState {
        val status = when (catalog) {
            CatalogState.Loading -> CatalogStatus.LOADING
            is CatalogState.Ready -> CatalogStatus.READY
            is CatalogState.Unavailable -> CatalogStatus.UNAVAILABLE
        }
        fun price(product: ShopProduct): String? = (catalog as? CatalogState.Ready)?.prices?.get(product.id)?.formattedPrice
        fun purchasable(product: ShopProduct): Boolean = price(product) != null && !busy

        val monthlyOwnership = when {
            entitlement.monthlyActive -> OfferOwnership.ACTIVE
            entitlement.foreverOwned -> OfferOwnership.NOT_NEEDED
            else -> OfferOwnership.AVAILABLE
        }
        val foreverOwnership = if (entitlement.foreverOwned) OfferOwnership.OWNED else OfferOwnership.AVAILABLE

        return ShopUiState(
            catalog = status,
            monthly = AdsOfferUi(
                productId = ShopProduct.REMOVE_ADS_MONTHLY.id,
                price = price(ShopProduct.REMOVE_ADS_MONTHLY),
                ownership = monthlyOwnership,
                buyEnabled = monthlyOwnership == OfferOwnership.AVAILABLE && purchasable(ShopProduct.REMOVE_ADS_MONTHLY),
            ),
            forever = AdsOfferUi(
                productId = ShopProduct.REMOVE_ADS_FOREVER.id,
                price = price(ShopProduct.REMOVE_ADS_FOREVER),
                ownership = foreverOwnership,
                buyEnabled = foreverOwnership == OfferOwnership.AVAILABLE && purchasable(ShopProduct.REMOVE_ADS_FOREVER),
            ),
            sections = SECTION_ORDER.map { booster ->
                PackSectionUi(
                    booster = booster,
                    balance = balances[booster] ?: 0,
                    packs = ShopProduct.packsOf(booster).map { product ->
                        PackUi(
                            productId = product.id,
                            amount = product.amount,
                            badge = badgeFor(product.amount),
                            price = price(product),
                            buyEnabled = purchasable(product),
                        )
                    },
                )
            },
            busy = busy,
        )
    }

    /** Tier badge: the middle pack is "Popular", the largest "Best value". */
    fun badgeFor(amount: Int): PackBadge = when (amount) {
        20 -> PackBadge.POPULAR
        50 -> PackBadge.BEST_VALUE
        else -> PackBadge.NONE
    }

    /** Booster for a navigation arg ("cat" / "hint" / "mouse"); null for anything else. */
    fun boosterFromArg(arg: String?): Booster? = Booster.entries.firstOrNull { it.name.equals(arg, ignoreCase = true) }

    fun argFor(booster: Booster): String = booster.name.lowercase()
}
