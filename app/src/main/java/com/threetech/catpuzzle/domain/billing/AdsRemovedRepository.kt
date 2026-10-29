package com.threetech.catpuzzle.domain.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * "Ads removed" = active monthly subscription OR forever owned. ONE flag that every ad format reads (through
 * [com.threetech.catpuzzle.domain.ads.AdFreePolicy]). Backed by a DataStore cache so it answers at cold start / offline
 * before billing connects; Play query results overwrite it ([apply]).
 */
interface AdsRemovedRepository {
    /** Effective flag (cache + expiry rules). Starts false for a few ms until the cache is read. */
    val adsRemoved: StateFlow<Boolean>

    /** Raw entitlement (Shop card state: Active / Owned). */
    val entitlement: StateFlow<Entitlement>

    /** Persist [entitlement] (from a successful Play query or a purchase event). */
    suspend fun apply(entitlement: Entitlement)
}

/** Default for callers that have no billing (tests / previews): ads never removed. */
object NoAdsRemoved : AdsRemovedRepository {
    override val adsRemoved: StateFlow<Boolean> = MutableStateFlow(false)
    override val entitlement: StateFlow<Entitlement> = MutableStateFlow(Entitlement.NONE)
    override suspend fun apply(entitlement: Entitlement) = Unit
}
