package com.threetech.catpuzzle.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.threetech.catpuzzle.data.local.prefs.ioSafe
import com.threetech.catpuzzle.data.local.prefs.toEntitlement
import com.threetech.catpuzzle.data.local.prefs.write
import com.threetech.catpuzzle.domain.billing.AdsRemovedRepository
import com.threetech.catpuzzle.domain.billing.Entitlement
import com.threetech.catpuzzle.domain.billing.EntitlementPolicy
import com.threetech.catpuzzle.domain.time.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * DataStore-backed entitlement cache (file `meowdoku_prefs`, keys `ads_removed_*`). Eagerly collected in an app-lifetime
 * scope so [adsRemoved] is a plain StateFlow every ad use case can read synchronously.
 *
 * Cold start: both flows start at "not removed" and flip within a few ms when the DataStore read lands, long before any
 * ad is requested (ads only start after the splash consent + SDK init gate). Expiry is evaluated each time the cache
 * changes; every successful Play sync rewrites `verifiedAtMs`, which re-evaluates it. A long offline session therefore
 * keeps the value it had at its last sync until the next sync (app start / resume).
 */
@Singleton
class AdsRemovedRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val time: TimeProvider,
) : AdsRemovedRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val entitlement: StateFlow<Entitlement> = dataStore.data.ioSafe()
        .map { it.toEntitlement() }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, Entitlement.NONE)

    override val adsRemoved: StateFlow<Boolean> = entitlement
        .map { EntitlementPolicy.isAdsRemoved(it, time.nowMillis()) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, false)

    override suspend fun apply(entitlement: Entitlement) {
        dataStore.edit { prefs -> prefs.write(entitlement) }
    }
}
