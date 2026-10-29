package com.threetech.catpuzzle.data.ads

/**
 * TODO(META-CONSENT, release blocker for EEA/UK + US states): Meta Audience Network is NOT on the IAB GVL and
 * consent is NOT auto-forwarded by the Meta adapter. Google's Next-Gen mediation docs expose NO API for it
 * (no Additional Consent / data-processing-options call found, 2026-10-26). Options when decided:
 *  - AdMob console: leave Meta in the EEA/UK + US-state ad partner lists so Google can exclude it where consent is missing;
 *  - or wire Meta's own data-processing options once a Next-Gen/adapter hook exists.
 * Called right before MobileAds.initialize with the current canRequestAds. Intentionally a no-op today.
 * NEVER call AudienceNetworkAds.initialize() here: adapters auto-initialise inside MobileAds.initialize().
 */
internal object MetaConsentSeam {
    @Suppress("UNUSED_PARAMETER")
    fun apply(canRequestAds: Boolean) = Unit
}
