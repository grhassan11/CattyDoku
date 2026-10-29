package com.threetech.catpuzzle.domain.billing

import com.threetech.catpuzzle.domain.model.Booster

enum class ProductKind {
    /** Auto-renewing subscription (Play type "subs"). Acknowledge. */
    SUBSCRIPTION,

    /** One-time, owned forever (Play type "inapp", never consumed). Acknowledge. */
    NON_CONSUMABLE,

    /** One-time, bought again and again (Play type "inapp"). Grant inventory, then consume. */
    CONSUMABLE,
}

/**
 * THE one place that knows Play Console product ids. Create exactly these in Play Console.
 * Never rename an id after release. A consumable's grant is [booster] x [amount]; the number in the id is the amount
 * (pinned by a unit test), so changing a pack means a NEW product id.
 */
enum class ShopProduct(
    val id: String,
    val kind: ProductKind,
    /** Booster credited by a consumable pack; null for the ads-removal products. */
    val booster: Booster? = null,
    /** Units credited by a consumable pack; 0 for the ads-removal products. */
    val amount: Int = 0,
) {
    REMOVE_ADS_MONTHLY("remove_ads_monthly", ProductKind.SUBSCRIPTION),
    REMOVE_ADS_FOREVER("remove_ads_forever", ProductKind.NON_CONSUMABLE),

    CAT_PACK_5("cat_pack_5", ProductKind.CONSUMABLE, Booster.CAT, 5),
    CAT_PACK_20("cat_pack_20", ProductKind.CONSUMABLE, Booster.CAT, 20),
    CAT_PACK_50("cat_pack_50", ProductKind.CONSUMABLE, Booster.CAT, 50),

    HINT_PACK_5("hint_pack_5", ProductKind.CONSUMABLE, Booster.HINT, 5),
    HINT_PACK_20("hint_pack_20", ProductKind.CONSUMABLE, Booster.HINT, 20),
    HINT_PACK_50("hint_pack_50", ProductKind.CONSUMABLE, Booster.HINT, 50),

    MOUSE_PACK_5("mouse_pack_5", ProductKind.CONSUMABLE, Booster.MOUSE, 5),
    MOUSE_PACK_20("mouse_pack_20", ProductKind.CONSUMABLE, Booster.MOUSE, 20),
    MOUSE_PACK_50("mouse_pack_50", ProductKind.CONSUMABLE, Booster.MOUSE, 50),
    ;

    /** Ads-removal products (subscription + forever). */
    val isAdsRemoval: Boolean get() = kind != ProductKind.CONSUMABLE

    companion object {
        /** Base plan id expected in Play Console for the monthly subscription (informational). */
        const val MONTHLY_BASE_PLAN_ID = "monthly"

        private val byId = entries.associateBy { it.id }

        fun fromId(id: String): ShopProduct? = byId[id]

        fun ofKind(kind: ProductKind): List<ShopProduct> = entries.filter { it.kind == kind }

        /** The three packs of [booster], ascending amount (5, 20, 50). */
        fun packsOf(booster: Booster): List<ShopProduct> =
            entries.filter { it.booster == booster }.sortedBy { it.amount }
    }
}
