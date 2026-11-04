package com.tm.meow.sudoku.puzzle.domain.consent

/** What analytics / crash reporting / GA4 consent-mode signals may do, derived from consent. */
data class TelemetryConsent(
    val analytics: Boolean,
    val adStorage: Boolean,
    val adUserData: Boolean,
    val adPersonalization: Boolean,
)

/**
 * Pure consent gating for Analytics / Crashlytics / GA4 consent mode. Fail closed.
 *
 * Every signal needs [ConsentState.canRequestAds] AND, when UMP wrote a TCF purpose string (EEA / UK), the matching
 * TCF purposes. A blank string means no TCF region (nothing to check beyond canRequestAds).
 *  - analytics: purposes 1 + 7
 *  - ad_storage: purpose 1
 *  - ad_user_data: purposes 1 + 7
 *  - ad_personalization: purposes 1 + 3 + 4
 *
 * US regulated states (A-004): UMP writes no TCF string there (GPP only, not parsed). When [usRegulatedNoTcf] is
 * true (privacy options REQUIRED and no TCF string) the user may have opted out of sharing / targeted ads and we
 * cannot tell, so the three ad_* signals are DENIED (conservative). Analytics stays on canRequestAds.
 */
object TelemetryConsentPolicy {
    private const val PURPOSE_STORAGE = 1
    private const val PURPOSE_PERSONALIZED_ADS_PROFILE = 3
    private const val PURPOSE_PERSONALIZED_ADS_SELECT = 4
    private const val PURPOSE_MEASURE_AD_PERFORMANCE = 7

    fun analyticsAllowed(canRequestAds: Boolean, tcfPurposeConsents: String?): Boolean =
        allowed(canRequestAds, tcfPurposeConsents, PURPOSE_STORAGE, PURPOSE_MEASURE_AD_PERFORMANCE)

    fun adStorageAllowed(
        canRequestAds: Boolean,
        tcfPurposeConsents: String?,
        usRegulatedNoTcf: Boolean = false,
    ): Boolean =
        !usRegulatedNoTcf && allowed(canRequestAds, tcfPurposeConsents, PURPOSE_STORAGE)

    fun adUserDataAllowed(
        canRequestAds: Boolean,
        tcfPurposeConsents: String?,
        usRegulatedNoTcf: Boolean = false,
    ): Boolean =
        !usRegulatedNoTcf && allowed(canRequestAds, tcfPurposeConsents, PURPOSE_STORAGE, PURPOSE_MEASURE_AD_PERFORMANCE)

    fun adPersonalizationAllowed(
        canRequestAds: Boolean,
        tcfPurposeConsents: String?,
        usRegulatedNoTcf: Boolean = false,
    ): Boolean =
        !usRegulatedNoTcf && allowed(
            canRequestAds,
            tcfPurposeConsents,
            PURPOSE_STORAGE,
            PURPOSE_PERSONALIZED_ADS_PROFILE,
            PURPOSE_PERSONALIZED_ADS_SELECT,
        )

    /** True for a regulated US state: UMP says privacy options are required but there is no TCF purpose string. */
    fun usRegulatedNoTcf(privacyOptionsRequired: Boolean, tcfPurposeConsents: String?): Boolean =
        privacyOptionsRequired && tcfPurposeConsents.isNullOrBlank()

    fun from(state: ConsentState): TelemetryConsent = TelemetryConsent(
        analytics = state.analyticsAllowed,
        adStorage = state.adStorage,
        adUserData = state.adUserData,
        adPersonalization = state.adPersonalization,
    )

    private fun allowed(canRequestAds: Boolean, purposes: String?, vararg required: Int): Boolean {
        if (!canRequestAds) return false
        if (purposes.isNullOrBlank()) return true
        return required.all { purposeGranted(purposes, it) }
    }

    /** Purpose n (1-based) is the char at index n-1 of the IABTCF_PurposeConsents string. */
    private fun purposeGranted(purposes: String, purpose: Int): Boolean = purposes.getOrNull(purpose - 1) == '1'
}
