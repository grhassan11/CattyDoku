package com.threetech.catpuzzle.ui.screens.shop

import androidx.compose.runtime.Immutable
import com.threetech.catpuzzle.domain.model.Booster

enum class PackBadge { NONE, POPULAR, BEST_VALUE }

/** What the price slots show when there is no price string. */
enum class CatalogStatus {
    /** Price query in flight: price slots show a placeholder, buy disabled. */
    LOADING,

    /** Prices are live (a single product can still be missing: its [PackUi.price] is null). */
    READY,

    /** Billing not ready / products not found: prices unavailable, buy disabled, "Try again" shown. */
    UNAVAILABLE,
}

/** Where a Remove Ads offer stands for this player. */
enum class OfferOwnership {
    AVAILABLE,

    /** Monthly subscription is active (Buy replaced by Manage). */
    ACTIVE,

    /** Forever is owned (Buy replaced by Owned). */
    OWNED,

    /** Monthly offer while Forever is owned: pointless, buy disabled. */
    NOT_NEEDED,
}

@Immutable
data class AdsOfferUi(
    val productId: String,
    /** Localized Play price, or null when not available (see [CatalogStatus]). */
    val price: String?,
    val ownership: OfferOwnership,
    val buyEnabled: Boolean,
)

@Immutable
data class PackUi(
    val productId: String,
    val amount: Int,
    val badge: PackBadge,
    val price: String?,
    val buyEnabled: Boolean,
)

@Immutable
data class PackSectionUi(
    val booster: Booster,
    /** Live inventory count (balance chip). */
    val balance: Int,
    val packs: List<PackUi>,
)

@Immutable
data class ShopUiState(
    val catalog: CatalogStatus,
    val monthly: AdsOfferUi,
    val forever: AdsOfferUi,
    val sections: List<PackSectionUi>,
    /** A buy / restore call is in flight: every buy + restore control is disabled. */
    val busy: Boolean,
)
