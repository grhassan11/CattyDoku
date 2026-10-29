package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.billing.AdsRemovedRepository
import com.threetech.catpuzzle.domain.billing.CatalogState
import com.threetech.catpuzzle.domain.billing.Entitlement
import com.threetech.catpuzzle.domain.billing.PurchaseEvent
import com.threetech.catpuzzle.domain.billing.StoreResult
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.domain.repository.InventoryRepository
import com.threetech.catpuzzle.domain.repository.ShopRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/*
 * Shop use cases. Thin on purpose: the rules live in PurchaseProcessor / EntitlementPolicy / ShopRepositoryImpl.
 * The Shop ViewModel injects only these.
 */

/** The single "ads removed" flag (active monthly OR forever owned). */
class ObserveAdsRemoved(private val repository: AdsRemovedRepository) {
    operator fun invoke(): StateFlow<Boolean> = repository.adsRemoved
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
