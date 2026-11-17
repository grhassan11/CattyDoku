package com.tm.meow.sudoku.puzzle.data.network

import com.tm.meow.sudoku.puzzle.di.MainDispatcher
import com.tm.meow.sudoku.puzzle.domain.network.ConnectivityObserver
import com.tm.meow.sudoku.puzzle.domain.usecase.RecoverAfterReconnect
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * App-wide offline -> online edge detector (any screen). Started once from the Application. On every return to online after
 * having seen offline, waits [DEBOUNCE_MS] of stable connectivity (a flapping link restarts the wait; going offline cancels the
 * pass), then runs [RecoverAfterReconnect] (consent re-gather, SDK init, Remote Config, preloads) with bounded retries.
 * Online from the start never triggers anything: the normal splash startup owns that path.
 */
@Singleton
class NetworkRecoveryObserver @Inject constructor(
    private val connectivity: ConnectivityObserver,
    private val recover: RecoverAfterReconnect,
    @MainDispatcher main: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + main)
    private val started = AtomicBoolean(false)

    /** Idempotent. Main thread. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            var sawOffline = false
            connectivity.isOnline.collectLatest { online ->
                if (!online) {
                    sawOffline = true
                    return@collectLatest
                }
                if (!sawOffline) return@collectLatest
                delay(DEBOUNCE_MS)
                recover.runWithRetries()
                sawOffline = false
            }
        }
    }

    private companion object {
        /** Connectivity must hold this long before recovery starts (captive portals / flapping links). */
        const val DEBOUNCE_MS = 2_000L
    }
}
