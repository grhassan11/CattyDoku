package com.threetech.catpuzzle.domain.consent

import kotlinx.coroutines.flow.StateFlow

/** Consent as the rest of the app needs it. Defaults = fail closed (no ads, no privacy entry). */
data class ConsentState(
    val canRequestAds: Boolean = false,
    /** UMP says the user must be able to reopen the privacy options form (Settings entry). */
    val privacyOptionsRequired: Boolean = false,
    /** Analytics / Crashlytics may collect (see TelemetryConsentPolicy). Fail closed. */
    val analyticsAllowed: Boolean = false,
)

/** UMP boundary. Impl (data/consent) needs a foreground Activity; it never throws. */
interface ConsentRepository {
    val state: StateFlow<ConsentState>

    /**
     * Request consent info update (every launch) and show the form if required. The info-update step is
     * time-boxed inside the impl; the form itself is not. Errors fail open: returns normally and [state]
     * reflects whatever UMP knows.
     */
    suspend fun gatherConsent()

    /** Open the privacy options form (only meaningful when [ConsentState.privacyOptionsRequired]). */
    suspend fun showPrivacyOptions()
}
