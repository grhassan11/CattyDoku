package com.tm.meow.sudoku.puzzle.domain.ads

/** Moments the app could consider an ad. Only [LEVEL_WON_CONTINUE] (and optionally [LOSE_RETRY]) can ever show. */
enum class AdTrigger {
    /** User tapped "Level N+1" on the win dialog. */
    LEVEL_WON_CONTINUE,

    /**
     * User tapped Next puzzle / Back on the Sudoku win dialog. Tier rule lives in [SudokuAdRules]; the policy only checks
     * per-surface switch, Remove Ads, tutorial and consent (no Queens ad-free level or frequency).
     */
    SUDOKU_WON_CONTINUE,

    /** User confirmed a Sudoku restart (or game-over Retry). Same gating as [SUDOKU_WON_CONTINUE]; tier rule in [SudokuAdRules]. */
    SUDOKU_RESTART,

    /** User tapped Retry on the lose dialog (remote `levels_game_restarted_interstitial_ad_show`, default on). Shows from Level 1 (no ad-free gate). */
    LOSE_RETRY,

    /** User tapped Home (or system back) on the Levels lose screen. Same gating as [LOSE_RETRY] (same remote switch); own name for analytics. */
    LOSE_HOME,

    /**
     * User tapped the top-bar BACK icon (or system back) mid-game on Levels / Daily. Remote `levels_gameplay_back_button_native_ad_show`.
     * No progress requirement (any tap on a live game). Cool-down since any full-screen ad; never stacks with a win ad.
     * Ad shows only if already loaded (no wait).
     */
    LEVELS_GAME_BACK,

    /** Same as [LEVELS_GAME_BACK] for Sudoku (remote `sudoku_game_back_interstitial_ad_show`). */
    SUDOKU_GAME_BACK,

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
    /** Levels win: level 1..10 not in the remote first-10 list, or level > 10 not a multiple of the remote frequency. */
    LEVEL_NOT_ON_FREQUENCY,

    /** Player bought Remove Ads (monthly active or forever). */
    ADS_REMOVED,

    /** No validated internet connection: no ad can load, never wait for one. */
    OFFLINE,

    /** Back ad: a full-screen ad was shown too recently. Win ad: a back ad was shown too recently (no stacking). */
    COOLDOWN,
}

sealed interface AdDecision {
    data object Show : AdDecision
    data class Skip(val reason: AdSkipReason) : AdDecision
}

/** Everything the policy needs; no Android, no clock reads. */
data class InterstitialContext(
    val trigger: AdTrigger,
    val canRequestAds: Boolean,
    /** Level just completed (win) / failed (retry), 1-based. Ignored for daily. */
    val level: Int,
    val isDaily: Boolean = false,
    val inTutorial: Boolean = false,
    /** Diagnostic only (debug log). Not part of any decision: Levels cadence is level-number based. */
    val winsSinceLastAd: Int = 0,
    /** Persisted flag: level of the last interstitial (0 = none). */
    val lastInterstitialLevel: Int,
    /** Remove Ads owned (monthly active OR forever): no interstitial ever. */
    val adsRemoved: Boolean = false,
    /** Validated internet connection present. Default true (callers that do not track connectivity). */
    val online: Boolean = true,
    /** Ms since ANY full-screen ad (interstitial / app-open / rewarded) was shown this process; null = none yet. */
    val msSinceLastAd: Long? = null,
    /** Ms since a GAME_BACK interstitial was shown this process; null = none yet. */
    val msSinceLastBackAd: Long? = null,
)

/** Pure show/skip decision. Ad readiness is NOT decided here (that is the AdsRepository's job). */
object InterstitialPolicy {
    /** Min spacing (monotonic ms) for a back ad after any full-screen ad, and for a win ad after a back ad. Hardcoded, conservative. */
    const val BACK_COOLDOWN_MS = 10_000L

    fun decide(ctx: InterstitialContext, config: AdPolicyConfig = AdPolicyConfig()): AdDecision {
        fun skip(r: AdSkipReason) = AdDecision.Skip(r)

        if (!switchOn(ctx.trigger, config)) return skip(AdSkipReason.DISABLED)
        if (AdFreePolicy.isSuppressedByPurchase(AdFormat.INTERSTITIAL, ctx.adsRemoved)) return skip(AdSkipReason.ADS_REMOVED)
        val sudoku = ctx.trigger == AdTrigger.SUDOKU_WON_CONTINUE || ctx.trigger == AdTrigger.SUDOKU_RESTART
        val back = ctx.trigger == AdTrigger.LEVELS_GAME_BACK || ctx.trigger == AdTrigger.SUDOKU_GAME_BACK
        if (ctx.trigger != AdTrigger.LEVEL_WON_CONTINUE && ctx.trigger != AdTrigger.LOSE_RETRY && ctx.trigger != AdTrigger.LOSE_HOME && !sudoku && !back) {
            return if (ctx.trigger == AdTrigger.TUTORIAL) skip(AdSkipReason.TUTORIAL) else skip(AdSkipReason.NOT_A_BOUNDARY)
        }
        if (ctx.inTutorial) return skip(AdSkipReason.TUTORIAL)
        if (!ctx.online) return skip(AdSkipReason.OFFLINE)
        if (!ctx.canRequestAds) return skip(AdSkipReason.NO_CONSENT)
        // Back ad: cool-down since ANY full-screen ad (win / app-open / rewarded / previous back). No level exemption.
        if (back) {
            val since = ctx.msSinceLastAd
            if (since != null && since < BACK_COOLDOWN_MS) return skip(AdSkipReason.COOLDOWN)
            return AdDecision.Show
        }
        // Win ad right after a back ad (leave + re-enter + fast win): no stacking. Win cadence is otherwise unchanged.
        if (ctx.trigger == AdTrigger.LEVEL_WON_CONTINUE || ctx.trigger == AdTrigger.SUDOKU_WON_CONTINUE) {
            val sinceBack = ctx.msSinceLastBackAd
            if (sinceBack != null && sinceBack < BACK_COOLDOWN_MS) return skip(AdSkipReason.COOLDOWN)
        }
        // Sudoku: product rule (tier + win count, see SudokuAdRules) decides upstream; no Queens pacing applies.
        if (sudoku) return AdDecision.Show
        // Ad-free levels no longer gate any Levels interstitial: the win ad starts at Level 1 (first-10 list / frequency rule below) and
        // the Out of Lives Restart (LOSE_RETRY) also shows from Level 1 (user decision). Only its switch applies.

        if (ctx.trigger == AdTrigger.LEVEL_WON_CONTINUE) {
            // Level-number cadence: ctx.level is the JUST-COMPLETED level (the win dialog "Level N+1" button passes the
            // level just won). Ad-free levels do NOT apply here. Levels 1..10: ONLY the first-10 list decides (frequency
            // ignored). Levels > 10: ONLY the frequency N (level % N == 0; N=1: 11,12,...; N=10: 20,30,...).
            // Daily puzzles carry no level number: not level-gated (unchanged).
            if (!ctx.isDaily) {
                val onCadence = if (ctx.level > AdPolicyConfig.FIRST_TEN_LAST_LEVEL) {
                    ctx.level % config.levelsInterstitialFrequency.coerceAtLeast(1) == 0
                } else {
                    ctx.level in config.levelsFirstTenInterstitialLevels
                }
                if (!onCadence) return skip(AdSkipReason.LEVEL_NOT_ON_FREQUENCY)
            }
        }
        return AdDecision.Show
    }

    /** Per-surface remote switch of [trigger]. Non-boundary triggers pass here and are rejected as NOT_A_BOUNDARY / TUTORIAL. */
    private fun switchOn(trigger: AdTrigger, config: AdPolicyConfig): Boolean = when (trigger) {
        AdTrigger.LEVEL_WON_CONTINUE -> config.levelsWinInterstitialShow
        AdTrigger.LOSE_RETRY, AdTrigger.LOSE_HOME -> config.levelsRestartInterstitialShow
        AdTrigger.SUDOKU_WON_CONTINUE -> config.sudokuWinInterstitialShow
        AdTrigger.SUDOKU_RESTART -> config.sudokuRestartInterstitialShow
        AdTrigger.LEVELS_GAME_BACK -> config.levelsBackInterstitialShow
        AdTrigger.SUDOKU_GAME_BACK -> config.sudokuBackInterstitialShow
        else -> true
    }
}
