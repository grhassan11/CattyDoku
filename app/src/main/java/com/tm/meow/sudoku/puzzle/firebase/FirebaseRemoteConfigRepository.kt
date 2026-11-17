package com.tm.meow.sudoku.puzzle.firebase

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.tm.meow.sudoku.puzzle.domain.config.AppRemoteConfig
import com.tm.meow.sudoku.puzzle.domain.config.RemoteConfigParser
import com.tm.meow.sudoku.puzzle.domain.config.RemoteConfigRepository
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Remote Config adapter. In-app defaults come from [RemoteConfigParser.defaults] (no XML). [current] is cached and the cache
 * is replaced on every successful activate (B+C: late fetch applies in the same session; see [RemoteConfigRepository]). [refresh] runs on [scope] (app lifetime, NOT a ViewModel scope) under a hard
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

    /** A fetch + activate completed successfully this process: later [refresh] calls (reconnect recovery) are no-ops. */
    @Volatile private var fetched = false
    @Volatile private var job: Job? = null
    private val inFlight = AtomicBoolean(false)

    /**
     * Single-flight and idempotent: no-op while a fetch runs or after one succeeded this process. A failed / timed-out fetch
     * (offline launch) leaves it re-armed, so the late-online recovery can call it again.
     */
    override fun refresh() {
        if (fetched || !inFlight.compareAndSet(false, true)) return
        job = scope.launch {
            try {
                if (withTimeoutOrNull(FETCH_BUDGET_MS) { fetchAndActivate() } == true) {
                    fetched = true
                    // Activated: replace the cache so the next current() read sees the new values.
                    synchronized(this@FirebaseRemoteConfigRepository) { snapshot = readNow() }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // failure = defaults / last activated values
            } finally {
                inFlight.set(false)
            }
        }
    }

    override suspend fun refreshAndAwait(timeoutMs: Long): Boolean {
        refresh()
        if (fetched) return true
        withTimeoutOrNull(timeoutMs) { job?.join() }
        return fetched
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
