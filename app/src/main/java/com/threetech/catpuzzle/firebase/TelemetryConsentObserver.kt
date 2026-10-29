package com.threetech.catpuzzle.firebase

import com.threetech.catpuzzle.domain.consent.ConsentRepository
import com.threetech.catpuzzle.domain.usecase.ApplyTelemetryConsent
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
) {
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            try {
                consent.state.collect { apply(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // telemetry stays off
            }
        }
    }
}
