package com.tm.meow.sudoku.puzzle.domain.usecase

import com.tm.meow.sudoku.puzzle.domain.ads.AdFreePolicy
import com.tm.meow.sudoku.puzzle.domain.ads.AdPolicyConfig
import com.tm.meow.sudoku.puzzle.domain.billing.AdsRemovedRepository
import com.tm.meow.sudoku.puzzle.domain.billing.CatalogState
import com.tm.meow.sudoku.puzzle.domain.billing.Entitlement
import com.tm.meow.sudoku.puzzle.domain.billing.PurchaseEvent
import com.tm.meow.sudoku.puzzle.domain.billing.StoreResult
import com.tm.meow.sudoku.puzzle.domain.model.Booster
import com.tm.meow.sudoku.puzzle.domain.repository.AppFlagsRepository
import com.tm.meow.sudoku.puzzle.domain.repository.InventoryRepository
import com.tm.meow.sudoku.puzzle.domain.repository.ShopRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/*
 * Shop use cases. Thin on purpose: the rules live in PurchaseProcessor / EntitlementPolicy / ShopRepositoryImpl.
 * The Shop ViewModel injects only these.
 */

/** The single "ads removed" flag (active monthly OR forever owned). */
class ObserveAdsRemoved(private val repository: AdsRemovedRepository) {
    operator fun invoke(): StateFlow<Boolean> = repository.adsRemoved
}

/**
 * True once ads can appear for this player: highest level reached (flags.lastPlayedLevel, forward-only) is past the
 * ad-free levels, same rule and same Remote Config value as the ad formats ([AdFreePolicy] + [AdPolicyConfig.adFreeThroughLevel]).
 * Gates the Remove Ads offers (Home badge, Store card) so they never appear before the first ad. Ownership is NOT checked here.
 */
class ObserveAdsExposure(
    private val flags: AppFlagsRepository,
    private val config: AdPolicyConfig,
) {
    operator fun invoke(): Flow<Boolean> =
        flags.flags.map { AdFreePolicy.adsAllowed(it.lastPlayedLevel, config.adFreeThroughLevel) }.distinctUntilChanged()
}

/** Raw entitlement for the Shop card ("Active" / "Owned"). */
class ObserveEntitlement(private val repository: AdsRemovedRepository) {
    operator fun invoke(): StateFlow<Entitlement> = repository.entitlement
}

/** Live Play prices / billing availability. */
class ObserveShopCatalog(private val shop: ShopRepository) {
    operator fun invoke(): StateFlow<CatalogState> = shop.catalog
}

/** Query prices (and quietly re-sync purchases). Safe to call on Shop open and on Retry. */
class LoadShopCatalog(private val shop: ShopRepository) {
    suspend operator fun invoke() {
        shop.loadCatalog()
        shop.refresh()
    }
}

/** Start the Play purchase sheet for a product id. Failures are also emitted as events. */
class BuyProduct(private val shop: ShopRepository) {
    suspend operator fun invoke(productId: String): StoreResult<Unit> = shop.buy(productId)
}

/** Restore purchases (Shop top bar + footer). */
class RestorePurchases(private val shop: ShopRepository) {
    suspend operator fun invoke() = shop.restore()
}

/** One-shot user-visible billing outcomes. */
class ObservePurchaseEvents(private val shop: ShopRepository) {
    operator fun invoke(): Flow<PurchaseEvent> = shop.events
}

/** Live booster stock for the Shop section balance chips (inventory is the source of truth). */
class ObserveBoosterBalances(private val inventory: InventoryRepository) {
    operator fun invoke(): Flow<Map<Booster, Int>> = inventory.observeAll()
}
