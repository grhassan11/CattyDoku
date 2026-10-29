package com.threetech.catpuzzle.domain.ads

/**
 * App-open (resume-only) policy constants in ONE place. Phase 10 (Remote Config) may provide a different instance.
 * Cold start NEVER shows one: only a return from background does.
 */
data class AppOpenConfig(
    val enabled: Boolean = DEFAULT_ENABLED,
    /** Ignore short app switches: must have been in the background at least this long. */
    val minBackgroundMs: Long = DEFAULT_MIN_BACKGROUND_MS,
    /** Minimum gap since ANY full-screen ad (interstitial or app-open) was last shown. */
    val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
    /** Loaded app-open ads expire after 4 hours (Google). Older = discard + reload, never show. */
    val adExpiryMs: Long = DEFAULT_AD_EXPIRY_MS,
    /** Hard cap per process. */
    val maxPerSession: Int = DEFAULT_MAX_PER_SESSION,
    /** Levels 1..this are ad-free for every format (see [AdFreePolicy]). */
    val adFreeThroughLevel: Int = AdFreePolicy.AD_FREE_THROUGH_LEVEL,
) {
    companion object {
        const val DEFAULT_ENABLED = true
        const val DEFAULT_MIN_BACKGROUND_MS = 30_000L
        const val DEFAULT_MIN_INTERVAL_MS = 180_000L
        const val DEFAULT_AD_EXPIRY_MS = 4 * 60 * 60 * 1000L
        const val DEFAULT_MAX_PER_SESSION = 8
    }
}

enum class AppOpenSkipReason {
    DISABLED,
    NO_CONSENT,
    SDK_NOT_READY,

    /** Player has not passed the ad-free early levels yet. */
    AD_FREE_LEVEL,
    COLD_START,

    /** Splash / Terms / consent / notification prompt / Tutorial: Home not reached yet this process. */
    START_FLOW_ACTIVE,

    /** An interstitial or another app-open is on screen. */
    AD_SHOWING,

    /** UMP form on screen. */
    CONSENT_FORM_SHOWING,

    /** Foreground follows an ad click-out (browser / store). */
    RETURNED_FROM_AD,
    NEVER_BACKGROUNDED,
    SHORT_BACKGROUND,
    TOO_SOON_AFTER_LAST_AD,
    SESSION_CAP,
    NO_AD,
    AD_EXPIRED,

    /** Player bought Remove Ads (monthly active or forever). */
    ADS_REMOVED,
}

sealed interface AppOpenDecision {
    data object Show : AppOpenDecision
    data class Skip(val reason: AppOpenSkipReason) : AppOpenDecision
}

/** Everything the policy needs; no Android, no clock reads. */
data class AppOpenContext(
    val coldStart: Boolean,
    val canRequestAds: Boolean,
    val sdkInitialized: Boolean,
    val startFlowComplete: Boolean,
    val fullScreenAdShowing: Boolean,
    val consentFormShowing: Boolean,
    val returnedFromAd: Boolean,
    val nowMs: Long,
    /** Monotonic ms of the matching process ON_STOP; null = never stopped. */
    val backgroundedAtMs: Long?,
    /** Monotonic ms of the last full-screen ad (any kind); null = none this process. */
    val lastAdShownMs: Long?,
    val appOpenShownThisSession: Int,
    /** Age of the held ad; null = none held. */
    val adAgeMs: Long?,
    /** Player progress for the ad-free rule (level being played / last played). Default = past the gate. */
    val playerLevel: Int = Int.MAX_VALUE,
    /** Remove Ads owned (monthly active OR forever): no app-open ever. */
    val adsRemoved: Boolean = false,
)

/** Pure show/skip decision. */
object AppOpenPolicy {

    fun decide(ctx: AppOpenContext, config: AppOpenConfig = AppOpenConfig()): AppOpenDecision {
        fun skip(r: AppOpenSkipReason) = AppOpenDecision.Skip(r)

        if (!config.enabled) return skip(AppOpenSkipReason.DISABLED)
        if (AdFreePolicy.isSuppressedByPurchase(AdFormat.APP_OPEN, ctx.adsRemoved)) return skip(AppOpenSkipReason.ADS_REMOVED)
        if (ctx.coldStart) return skip(AppOpenSkipReason.COLD_START)
        if (!ctx.canRequestAds) return skip(AppOpenSkipReason.NO_CONSENT)
        if (!ctx.sdkInitialized) return skip(AppOpenSkipReason.SDK_NOT_READY)
        if (AdFreePolicy.isAdFree(ctx.playerLevel, config.adFreeThroughLevel)) return skip(AppOpenSkipReason.AD_FREE_LEVEL)
        if (!ctx.startFlowComplete) return skip(AppOpenSkipReason.START_FLOW_ACTIVE)
        if (ctx.fullScreenAdShowing) return skip(AppOpenSkipReason.AD_SHOWING)
        if (ctx.consentFormShowing) return skip(AppOpenSkipReason.CONSENT_FORM_SHOWING)
        if (ctx.returnedFromAd) return skip(AppOpenSkipReason.RETURNED_FROM_AD)
        val bg = ctx.backgroundedAtMs ?: return skip(AppOpenSkipReason.NEVER_BACKGROUNDED)
        if (ctx.nowMs - bg < config.minBackgroundMs) return skip(AppOpenSkipReason.SHORT_BACKGROUND)
        val last = ctx.lastAdShownMs
        if (last != null && ctx.nowMs - last < config.minIntervalMs) return skip(AppOpenSkipReason.TOO_SOON_AFTER_LAST_AD)
        if (ctx.appOpenShownThisSession >= config.maxPerSession) return skip(AppOpenSkipReason.SESSION_CAP)
        val age = ctx.adAgeMs ?: return skip(AppOpenSkipReason.NO_AD)
        if (age >= config.adExpiryMs) return skip(AppOpenSkipReason.AD_EXPIRED)
        return AppOpenDecision.Show
    }
}
