package com.threetech.catpuzzle.domain.repository

import com.threetech.catpuzzle.domain.model.Booster

/** Idempotent credit of a consumable pack, keyed by Play purchase token. */
interface PurchaseGrantRepository {
    /**
     * In ONE atomic transaction: record [purchaseToken] and add [amount] of [booster] to inventory, unless the token
     * was already recorded. Returns true when it credited now, false when the token was already granted (nothing changed).
     * A crash can never leave "inventory added, token not recorded" or the reverse.
     */
    suspend fun grantOnce(purchaseToken: String, productId: String, booster: Booster, amount: Int): Boolean
}
