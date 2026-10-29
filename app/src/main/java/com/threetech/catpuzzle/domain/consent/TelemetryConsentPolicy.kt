package com.threetech.catpuzzle.domain.consent

/** What analytics / crash reporting may do, derived from consent. */
data class TelemetryConsent(val analytics: Boolean, val ads: Boolean)

/**
 * Pure consent gating for Analytics / Crashlytics. Fail closed.
 *
 * Analytics needs [ConsentState.canRequestAds] AND, when UMP wrote a TCF purpose string (EEA / UK), consent for
 * purpose 1 (store/access information) and purpose 7 (measure ad performance). A blank string means no TCF region
 * (nothing to check beyond canRequestAds).
 */
object TelemetryConsentPolicy {
    private const val PURPOSE_STORAGE = 1
    private const val PURPOSE_MEASURE_AD_PERFORMANCE = 7

    fun analyticsAllowed(canRequestAds: Boolean, tcfPurposeConsents: String?): Boolean {
        if (!canRequestAds) return false
        if (tcfPurposeConsents.isNullOrBlank()) return true
        return purposeGranted(tcfPurposeConsents, PURPOSE_STORAGE) &&
            purposeGranted(tcfPurposeConsents, PURPOSE_MEASURE_AD_PERFORMANCE)
    }

    fun from(state: ConsentState): TelemetryConsent =
        TelemetryConsent(analytics = state.analyticsAllowed, ads = state.canRequestAds)

    /** Purpose n (1-based) is the char at index n-1 of the IABTCF_PurposeConsents string. */
    private fun purposeGranted(purposes: String, purpose: Int): Boolean = purposes.getOrNull(purpose - 1) == '1'
}
