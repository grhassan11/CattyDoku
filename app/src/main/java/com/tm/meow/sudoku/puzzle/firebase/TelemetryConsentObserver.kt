package com.tm.meow.sudoku.puzzle.firebase

import com.tm.meow.sudoku.puzzle.domain.consent.ConsentRepository
import com.tm.meow.sudoku.puzzle.domain.usecase.ApplyTelemetryConsent
import com.tm.meow.sudoku.puzzle.domain.usecase.UserPropsSync
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Keeps Analytics / Crashlytics collection in step with UMP consent for the whole process: applies the current state
 * and every later change (splash gather, privacy options form). Started once from the Application. Initial state is
 * fail-closed, so collection stays off until consent really allows it.
 */
@Singleton
class TelemetryConsentObserver @Inject constructor(
    private val consent: ConsentRepository,
    private val apply: ApplyTelemetryConsent,
    private val userProps: UserPropsSync,
) {
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            try {
                // drop(1): skip the fail-closed default emission (manifest defaults already deny).
                consent.state.drop(1).collect {
                    apply(it)
                    userProps.sync() // no-op until analytics consent allows it
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // telemetry stays off
            }
        }
    }
}
