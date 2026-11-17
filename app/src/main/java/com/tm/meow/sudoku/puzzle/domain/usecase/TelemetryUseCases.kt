package com.tm.meow.sudoku.puzzle.domain.usecase

import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.config.RemoteConfigRepository
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentState
import com.tm.meow.sudoku.puzzle.domain.consent.TelemetryConsentPolicy
import com.tm.meow.sudoku.puzzle.domain.crash.CrashReporter

/** Push the current consent into Analytics (collection + consent mode) and Crashlytics (collection). Idempotent. */
class ApplyTelemetryConsent(
    private val analytics: AnalyticsLogger,
    private val crash: CrashReporter,
) {
    operator fun invoke(state: ConsentState) {
        val consent = TelemetryConsentPolicy.from(state)
        analytics.applyConsent(
            consent.analytics,
            consent.adStorage,
            consent.adUserData,
            consent.adPersonalization,
        )
        crash.setCollectionEnabled(consent.analytics)
    }
}

/**
 * Kick a background Remote Config fetch + activate. Returns immediately; never blocks or extends the splash.
 * [allowed] is the consent gate (A-003): the fetch creates a Firebase installation id and contacts Google, so it only
 * runs once consent allows it. When gated, cached / in-app default values keep working.
 */
class RefreshRemoteConfig(
    private val repository: RemoteConfigRepository,
    private val allowed: () -> Boolean = { true },
    private val online: () -> Boolean = { true },
) {
    operator fun invoke() {
        if (allowed()) repository.refresh()
    }

    /**
     * Splash variant (B): same consent gate, plus skipped while offline (no pointless wait). Bounded by [timeoutMs]; the
     * fetch result activates into [RemoteConfigRepository.current]. Returns true when fresh values are active. Never throws.
     */
    suspend fun awaitFirst(timeoutMs: Long): Boolean {
        if (!allowed()) return false
        if (!online()) {
            repository.refresh() // offline: still arm the single-flight attempt, but do not wait
            return false
        }
        return try {
            repository.refreshAndAwait(timeoutMs)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            false
        }
    }
}
