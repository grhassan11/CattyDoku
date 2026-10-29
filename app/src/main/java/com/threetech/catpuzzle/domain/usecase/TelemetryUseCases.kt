package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.analytics.AnalyticsLogger
import com.threetech.catpuzzle.domain.config.RemoteConfigRepository
import com.threetech.catpuzzle.domain.consent.ConsentState
import com.threetech.catpuzzle.domain.consent.TelemetryConsentPolicy
import com.threetech.catpuzzle.domain.crash.CrashReporter

/** Push the current consent into Analytics (collection + consent mode) and Crashlytics (collection). Idempotent. */
class ApplyTelemetryConsent(
    private val analytics: AnalyticsLogger,
    private val crash: CrashReporter,
) {
    operator fun invoke(state: ConsentState) {
        val consent = TelemetryConsentPolicy.from(state)
        analytics.applyConsent(consent.analytics, consent.ads)
        crash.setCollectionEnabled(consent.analytics)
    }
}

/** Kick a background Remote Config fetch + activate. Returns immediately; never blocks or extends the splash. */
class RefreshRemoteConfig(private val repository: RemoteConfigRepository) {
    operator fun invoke() = repository.refresh()
}
