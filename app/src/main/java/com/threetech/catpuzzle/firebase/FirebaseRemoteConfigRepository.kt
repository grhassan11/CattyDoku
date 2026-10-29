package com.threetech.catpuzzle.firebase

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.threetech.catpuzzle.domain.config.AppRemoteConfig
import com.threetech.catpuzzle.domain.config.RemoteConfigParser
import com.threetech.catpuzzle.domain.config.RemoteConfigRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Remote Config adapter. In-app defaults come from [RemoteConfigParser.defaults] (no XML). [current] is frozen after the
 * first read (see [RemoteConfigRepository]). [refresh] runs on [scope] (app lifetime, NOT a ViewModel scope) under a hard
 * time budget; any failure leaves the previously activated values / defaults in place.
 */
class FirebaseRemoteConfigRepository(
    private val remoteConfig: FirebaseRemoteConfig,
    private val scope: CoroutineScope,
    debug: Boolean,
) : RemoteConfigRepository {

    @Volatile private var snapshot: AppRemoteConfig? = null

    init {
        try {
            remoteConfig.setConfigSettingsAsync(
                FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(if (debug) 0L else MIN_FETCH_INTERVAL_SEC)
                    .setFetchTimeoutInSeconds(FETCH_TIMEOUT_SEC)
                    .build(),
            )
            remoteConfig.setDefaultsAsync(RemoteConfigParser.defaults())
        } catch (t: Throwable) {
            // defaults still apply through the parser
        }
    }

    override fun current(): AppRemoteConfig {
        snapshot?.let { return it }
        return synchronized(this) {
            snapshot ?: readNow().also { snapshot = it }
        }
    }

    override fun refresh() {
        scope.launch {
            try {
                withTimeoutOrNull(FETCH_BUDGET_MS) { fetchAndActivate() }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // failure = defaults / last activated values
            }
        }
    }

    private fun readNow(): AppRemoteConfig = try {
        RemoteConfigParser.parse { key -> remoteConfig.getString(key) }
    } catch (t: Throwable) {
        AppRemoteConfig()
    }

    private suspend fun fetchAndActivate(): Boolean = suspendCancellableCoroutine { cont ->
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (cont.isActive) cont.resume(task.isSuccessful)
        }
    }

    private companion object {
        const val MIN_FETCH_INTERVAL_SEC = 3_600L
        const val FETCH_TIMEOUT_SEC = 3L

        /** Outer budget: a hung fetch is abandoned (the SDK keeps its own 3 s network timeout inside). */
        const val FETCH_BUDGET_MS = 5_000L
    }
}
