package com.tm.meow.sudoku.puzzle.domain.ads

/** Moments the app could consider an ad. Only [LEVEL_WON_CONTINUE] (and optionally [LOSE_RETRY]) can ever show. */
enum class AdTrigger {
    /** User tapped "Level N+1" on the win dialog. */
    LEVEL_WON_CONTINUE,

    /**
     * User tapped Next puzzle / Back on the Sudoku win dialog. Tier rule lives in [SudokuAdRules]; the policy only checks
     * per-surface switch, Remove Ads, tutorial and consent (no Queens ad-free level, frequency, session age, interval or cap).
     */
    SUDOKU_WON_CONTINUE,

    /** User confirmed a Sudoku restart (or game-over Retry). Same gating as [SUDOKU_WON_CONTINUE]; tier rule in [SudokuAdRules]. */
    SUDOKU_RESTART,

    /** User tapped Retry on the lose dialog (remote `levels_game_restarted_interstitial_ad_show`, default on). */
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
    /** Levels win: completed level number is not a multiple of the remote frequency. */
    LEVEL_NOT_ON_FREQUENCY,
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
    /** Level just completed (win) / failed (retry), 1-based. Ignored for daily. */
    val level: Int,
    val isDaily: Boolean = false,
    val inTutorial: Boolean = false,
    /** Diagnostic only (debug log). Not part of any decision: Levels cadence is level-number based. */
    val winsSinceLastAd: Int = 0,
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

        if (!switchOn(ctx.trigger, config)) return skip(AdSkipReason.DISABLED)
        if (AdFreePolicy.isSuppressedByPurchase(AdFormat.INTERSTITIAL, ctx.adsRemoved)) return skip(AdSkipReason.ADS_REMOVED)
        val sudoku = ctx.trigger == AdTrigger.SUDOKU_WON_CONTINUE || ctx.trigger == AdTrigger.SUDOKU_RESTART
        if (ctx.trigger != AdTrigger.LEVEL_WON_CONTINUE && ctx.trigger != AdTrigger.LOSE_RETRY && !sudoku) {
            return if (ctx.trigger == AdTrigger.TUTORIAL) skip(AdSkipReason.TUTORIAL) else skip(AdSkipReason.NOT_A_BOUNDARY)
        }
        if (ctx.inTutorial) return skip(AdSkipReason.TUTORIAL)
        if (ctx.isDaily) return skip(AdSkipReason.DAILY)
        if (!ctx.canRequestAds) return skip(AdSkipReason.NO_CONSENT)
        // Sudoku: product rule (tier + win count, see SudokuAdRules) decides upstream; no Queens pacing applies.
        if (sudoku) return AdDecision.Show
        if (AdFreePolicy.isAdFree(ctx.level, config.adFreeThroughLevel)) return skip(AdSkipReason.AD_FREE_LEVEL)

        if (ctx.trigger == AdTrigger.LEVEL_WON_CONTINUE) {
            if (ctx.level <= ctx.lastInterstitialLevel) return skip(AdSkipReason.LEVEL_ALREADY_HAD_AD)
            // Level-number cadence: ctx.level is the JUST-COMPLETED level (the win dialog "Level N+1" button passes the
            // level just won). Past the ad-free levels, show only when it is a multiple of the frequency. No exemptions.
            if (ctx.level % config.levelsInterstitialFrequency.coerceAtLeast(1) != 0) return skip(AdSkipReason.LEVEL_NOT_ON_FREQUENCY)
        }
        if (ctx.nowMs - ctx.sessionStartMs < config.minSessionAgeMs) return skip(AdSkipReason.SESSION_TOO_YOUNG)
        val last = ctx.lastAdShownMs
        if (last != null && ctx.nowMs - last < config.minIntervalMs) return skip(AdSkipReason.TOO_SOON_AFTER_LAST_AD)
        if (ctx.adsShownThisSession >= config.maxPerSession) return skip(AdSkipReason.SESSION_CAP)
        return AdDecision.Show
    }

    /** Per-surface remote switch of [trigger]. Non-boundary triggers pass here and are rejected as NOT_A_BOUNDARY / TUTORIAL. */
    private fun switchOn(trigger: AdTrigger, config: AdPolicyConfig): Boolean = when (trigger) {
        AdTrigger.LEVEL_WON_CONTINUE -> config.levelsWinInterstitialShow
        AdTrigger.LOSE_RETRY -> config.levelsRestartInterstitialShow
        AdTrigger.SUDOKU_WON_CONTINUE -> config.sudokuWinInterstitialShow
        AdTrigger.SUDOKU_RESTART -> config.sudokuRestartInterstitialShow
        else -> true
    }
}
