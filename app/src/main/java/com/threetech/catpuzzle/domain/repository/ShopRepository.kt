package com.threetech.catpuzzle.domain.repository

import com.threetech.catpuzzle.domain.billing.CatalogState
import com.threetech.catpuzzle.domain.billing.PurchaseEvent
import com.threetech.catpuzzle.domain.billing.StoreResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The Shop + billing facade. App-scoped singleton: purchases that finish after the Shop closed are still processed. */
interface ShopRepository {
    val catalog: StateFlow<CatalogState>

    /** User-visible billing outcomes (granted, pending, failed, restored). No replay: missed events are not queued. */
    val events: Flow<PurchaseEvent>

    /** Idempotent. Start listening for purchase updates and run the first purchase sync. Call from Application. */
    fun start()

    /** Query localized prices. Safe to call repeatedly (Shop open, Retry). */
    suspend fun loadCatalog()

    /** Open the Play sheet for [productId]. Failures are also emitted on [events]. Success = sheet launched. */
    suspend fun buy(productId: String): StoreResult<Unit>

    /** Restore: re-query, re-apply entitlement, grant/consume leftovers, acknowledge. Emits Restored / NothingToRestore / Failed. */
    suspend fun restore()

    /** Quiet re-query (app resume, Shop open). Only purchase outcomes found are emitted. */
    suspend fun refresh()
}
