package com.tm.meow.sudoku.puzzle.domain.ads

/**
 * App-open config. Lifecycle path: cold start never shows, only a return from background. Splash path ([AppOpenContext.fromSplash]) is the single cold-start show.
 * No discretionary gates (level, interval, background time, session cap): only technical / legal necessities.
 */
data class AppOpenConfig(
    /** Loaded app-open ads expire after 4 hours (Google). Older = discard + reload, never show. */
    val adExpiryMs: Long = DEFAULT_AD_EXPIRY_MS,
) {
    companion object {
        const val DEFAULT_AD_EXPIRY_MS = 4 * 60 * 60 * 1000L
    }
}

enum class AppOpenSkipReason {
    NO_CONSENT,
    SDK_NOT_READY,

    COLD_START,

    /** An interstitial or another app-open is on screen. */
    AD_SHOWING,

    /** UMP form on screen. */
    CONSENT_FORM_SHOWING,

    NO_AD,
    AD_EXPIRED,

    /** Player bought Remove Ads (monthly active or forever). */
    ADS_REMOVED,

    /** No validated internet connection. */
    OFFLINE,

    /** Foreground is the return from an ad click-out (browser / store): skip this one app-open only. */
    AD_CLICK_RETURN,

    /** Foreground happened while a no-app-open screen (Settings) is current. Ad stays loaded for the next foreground. */
    SUPPRESSED_SCREEN,
}

sealed interface AppOpenDecision {
    data object Show : AppOpenDecision
    data class Skip(val reason: AppOpenSkipReason) : AppOpenDecision
}

/** Everything the policy needs; no Android, no clock reads. */
data class AppOpenContext(
    val coldStart: Boolean,
    /** Splash-driven show (the one cold-start app-open moment): lifts the [coldStart] skip only. All other gates apply. */
    val fromSplash: Boolean = false,
    val canRequestAds: Boolean,
    val sdkInitialized: Boolean,
    val fullScreenAdShowing: Boolean,
    val consentFormShowing: Boolean,
    /** Age of the held ad; null = none held. */
    val adAgeMs: Long?,
    /** Remove Ads owned (monthly active OR forever): no app-open ever. */
    val adsRemoved: Boolean = false,
    /** Validated internet connection present. Default true. */
    val online: Boolean = true,
    /** This foreground is the return from an ad click-out (one-shot, already consumed by the caller). */
    val returnFromAdClick: Boolean = false,
    /** Current screen is Settings (or a sub-screen): no app-open here. Evaluated at the foreground moment only. */
    val suppressedScreen: Boolean = false,
)

/** Pure show/skip decision. */
object AppOpenPolicy {

    fun decide(ctx: AppOpenContext, config: AppOpenConfig = AppOpenConfig()): AppOpenDecision {
        fun skip(r: AppOpenSkipReason) = AppOpenDecision.Skip(r)

        if (AdFreePolicy.isSuppressedByPurchase(AdFormat.APP_OPEN, ctx.adsRemoved)) return skip(AppOpenSkipReason.ADS_REMOVED)
        if (ctx.coldStart && !ctx.fromSplash) return skip(AppOpenSkipReason.COLD_START)
        if (ctx.returnFromAdClick) return skip(AppOpenSkipReason.AD_CLICK_RETURN)
        if (ctx.suppressedScreen) return skip(AppOpenSkipReason.SUPPRESSED_SCREEN)
        if (!ctx.online) return skip(AppOpenSkipReason.OFFLINE)
        if (!ctx.canRequestAds) return skip(AppOpenSkipReason.NO_CONSENT)
        if (!ctx.sdkInitialized) return skip(AppOpenSkipReason.SDK_NOT_READY)
        if (ctx.fullScreenAdShowing) return skip(AppOpenSkipReason.AD_SHOWING)
        if (ctx.consentFormShowing) return skip(AppOpenSkipReason.CONSENT_FORM_SHOWING)
        val age = ctx.adAgeMs ?: return skip(AppOpenSkipReason.NO_AD)
        if (age >= config.adExpiryMs) return skip(AppOpenSkipReason.AD_EXPIRED)
        return AppOpenDecision.Show
    }
}
