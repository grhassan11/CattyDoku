package com.threetech.catpuzzle.domain.ads

/** Moments the app could consider an ad. Only [LEVEL_WON_CONTINUE] (and optionally [LOSE_RETRY]) can ever show. */
enum class AdTrigger {
    /** User tapped "Level N+1" on the win dialog. */
    LEVEL_WON_CONTINUE,

    /** User tapped Retry on the lose dialog (policy OFF by default). */
    LOSE_RETRY,

    // Never boundaries: listed so callers state intent and tests pin the rule.
    DAILY_START,
    APP_LAUNCH,
    APP_RESUME,
    MID_GAMEPLAY,
    TUTORIAL,
}

enum class AdSkipReason {
    DISABLED,
    NOT_A_BOUNDARY,
    NO_CONSENT,
    TUTORIAL,
    DAILY,
    AD_FREE_LEVEL,
    LEVEL_ALREADY_HAD_AD,
    NOT_NTH_WIN,
    SESSION_TOO_YOUNG,
    TOO_SOON_AFTER_LAST_AD,
    SESSION_CAP,

    /** Player bought Remove Ads (monthly active or forever). */
    ADS_REMOVED,
}

sealed interface AdDecision {
    data object Show : AdDecision
    data class Skip(val reason: AdSkipReason) : AdDecision
}

/** Everything the policy needs; no Android, no clock reads (times are passed in). */
data class InterstitialContext(
    val trigger: AdTrigger,
    val canRequestAds: Boolean,
    /** Level just completed / failed (1-based). Ignored for daily. */
    val level: Int,
    val isDaily: Boolean = false,
    val inTutorial: Boolean = false,
    /** Wins since the last shown interstitial, INCLUDING the win being continued from. */
    val winsSinceLastAd: Int,
    val nowMs: Long,
    val sessionStartMs: Long,
    /** null = no ad shown yet this process. */
    val lastAdShownMs: Long?,
    /** Persisted flag: level of the last interstitial (0 = none). */
    val lastInterstitialLevel: Int,
    val adsShownThisSession: Int,
    /** Remove Ads owned (monthly active OR forever): no interstitial ever. */
    val adsRemoved: Boolean = false,
)

/** Pure show/skip decision. Ad readiness is NOT decided here (that is the AdsRepository's job). */
object InterstitialPolicy {

    fun decide(ctx: InterstitialContext, config: AdPolicyConfig = AdPolicyConfig()): AdDecision {
        fun skip(r: AdSkipReason) = AdDecision.Skip(r)

        if (!config.enabled) return skip(AdSkipReason.DISABLED)
        if (AdFreePolicy.isSuppressedByPurchase(AdFormat.INTERSTITIAL, ctx.adsRemoved)) return skip(AdSkipReason.ADS_REMOVED)
        if (ctx.trigger != AdTrigger.LEVEL_WON_CONTINUE && ctx.trigger != AdTrigger.LOSE_RETRY) {
            return if (ctx.trigger == AdTrigger.TUTORIAL) skip(AdSkipReason.TUTORIAL) else skip(AdSkipReason.NOT_A_BOUNDARY)
        }
        if (ctx.inTutorial) return skip(AdSkipReason.TUTORIAL)
        if (ctx.isDaily) return skip(AdSkipReason.DAILY)
        if (!ctx.canRequestAds) return skip(AdSkipReason.NO_CONSENT)
        if (ctx.trigger == AdTrigger.LOSE_RETRY && !config.showOnLoseRetry) return skip(AdSkipReason.NOT_A_BOUNDARY)
        if (AdFreePolicy.isAdFree(ctx.level, config.adFreeThroughLevel)) return skip(AdSkipReason.AD_FREE_LEVEL)

        if (ctx.trigger == AdTrigger.LEVEL_WON_CONTINUE) {
            if (ctx.level <= ctx.lastInterstitialLevel) return skip(AdSkipReason.LEVEL_ALREADY_HAD_AD)
            // First-ever interstitial (persisted lastInterstitialLevel == 0) is exempt from the Nth-win rule: level is already
            // past the ad-free check above, so the first non-ad-free win shows (in-memory win count resets per process).
            val firstEverAd = ctx.lastInterstitialLevel == 0
            if (!firstEverAd && ctx.winsSinceLastAd < config.everyNthWin.coerceAtLeast(1)) return skip(AdSkipReason.NOT_NTH_WIN)
        }
        if (ctx.nowMs - ctx.sessionStartMs < config.minSessionAgeMs) return skip(AdSkipReason.SESSION_TOO_YOUNG)
        val last = ctx.lastAdShownMs
        if (last != null && ctx.nowMs - last < config.minIntervalMs) return skip(AdSkipReason.TOO_SOON_AFTER_LAST_AD)
        if (ctx.adsShownThisSession >= config.maxPerSession) return skip(AdSkipReason.SESSION_CAP)
        return AdDecision.Show
    }
}
