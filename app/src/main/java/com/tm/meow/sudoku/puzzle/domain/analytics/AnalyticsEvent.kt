package com.tm.meow.sudoku.puzzle.domain.analytics

/**
 * Typed analytics events. Names and param keys are STABLE (snake_case, <= 40 chars): never rename, only add.
 * Param values are Long / String / Double only (booleans are 0/1 Long). No PII, no user ids, no ad-unit ids.
 * Pure Kotlin: the Firebase mapping lives in `firebase/`.
 *
 * Level events are unified across modes (levels | daily | sudoku): the `mode` param tells them apart, see [LevelContext]
 * and [AttemptStats] for the shared params.
 */
sealed class AnalyticsEvent(val name: String, val params: Map<String, Any> = emptyMap()) {

    // ---- navigation ----

    /**
     * GA4 `screen_view` (the strings equal FirebaseAnalytics.Event.SCREEN_VIEW / Param.SCREEN_NAME / SCREEN_CLASS).
     * [screenName] = stable snake_case name per route (no args, no PII). Logged once per destination change by AppNavHost.
     */
    data class ScreenView(val screenName: String) :
        AnalyticsEvent(
            "screen_view",
            mapOf("screen_name" to screenName, "screen_class" to SCREEN_CLASS),
        ) {
        companion object {
            /** Single-activity app: the class is constant. */
            const val SCREEN_CLASS = "MainActivity"
        }
    }

    // ---- onboarding ----

    data object TutorialBegin : AnalyticsEvent("tutorial_begin")

    /** [step] = 0-based lesson index reached. */
    data class TutorialStep(val step: Int) :
        AnalyticsEvent("tutorial_step", mapOf("step" to step.toLong()))

    data class TutorialComplete(val skipped: Boolean) :
        AnalyticsEvent("tutorial_complete", mapOf("skipped" to flag(skipped)))

    data object TermsAccept : AnalyticsEvent("terms_accept")

    data class NotificationPermission(val granted: Boolean) :
        AnalyticsEvent("notification_permission", mapOf("granted" to flag(granted)))

    // ---- unified level lifecycle (all modes) ----

    /** A fresh attempt starts (new level, restart, new puzzle). Resuming a saved game logs [LevelResume] instead. */
    data class LevelStart(val ctx: LevelContext) : AnalyticsEvent("level_start", ctx.toParams())

    /** A saved game is picked up again. */
    data class LevelResume(val ctx: LevelContext, val stats: AttemptStats) :
        AnalyticsEvent("level_resume", ctx.toParams() + stats.toParams())

    /**
     * Attempt finished. `result` = win | lose. [newBestTime] / [newBestScore] only on a win.
     * [stats] must carry the final score.
     */
    data class LevelEnd(
        val ctx: LevelContext,
        val stats: AttemptStats,
        val won: Boolean,
        val newBestTime: Boolean = false,
        val newBestScore: Boolean = false,
    ) : AnalyticsEvent(
        "level_end",
        buildMap<String, Any> {
            putAll(ctx.toParams())
            putAll(stats.toParams())
            put("result", if (won) RESULT_WIN else RESULT_LOSE)
            if (won) {
                put("new_best_time", flag(newBestTime))
                put("new_best_score", flag(newBestScore))
            }
        },
    ) {
        companion object {
            const val RESULT_WIN = "win"
            const val RESULT_LOSE = "lose"
        }
    }

    /** Left an open attempt without finishing it. At most one per attempt. [reason] = [QuitReason]. */
    data class LevelQuit(val ctx: LevelContext, val stats: AttemptStats, val reason: String) :
        AnalyticsEvent("level_quit", ctx.toParams() + stats.toParams() + mapOf<String, Any>("quit_reason" to reason))

    /** Restart of the current board (NOT a quit). Params describe the attempt being abandoned; a [LevelStart] follows. */
    data class LevelRestart(val ctx: LevelContext, val stats: AttemptStats) :
        AnalyticsEvent("level_restart", ctx.toParams() + stats.toParams())

    /** Lose-screen rewarded revive earned. */
    data class LevelRevive(val ctx: LevelContext, val stats: AttemptStats) :
        AnalyticsEvent("level_revive", ctx.toParams() + stats.toParams())

    data class UndoUsed(val ctx: LevelContext) : AnalyticsEvent("undo_used", ctx.toParams())

    data class HintUsed(val ctx: LevelContext) : AnalyticsEvent("hint_used", ctx.toParams())

    /** [type]: "cat" or "mouse" (hint has its own [HintUsed]). */
    data class BoosterUsed(val type: String, val ctx: LevelContext) :
        AnalyticsEvent("booster_used", ctx.toParams() + mapOf<String, Any>("type" to type))

    /** Player-initiated pause (Sudoku pause button). */
    data class GamePause(val ctx: LevelContext, val stats: AttemptStats) :
        AnalyticsEvent("game_pause", ctx.toParams() + stats.toParams())

    // ---- sudoku entry. [tier] = lowercase SudokuTier name ----

    data class SudokuTierSelect(val tier: String, val locked: Boolean) :
        AnalyticsEvent("sudoku_tier_select", mapOf("tier" to tier, "locked" to flag(locked)))

    /** "NEW GAME" tapped. [hadSave] = it replaces a saved game (confirm dialog follows). */
    data class SudokuNewGame(val tier: String, val hadSave: Boolean) :
        AnalyticsEvent("sudoku_new_game", mapOf("tier" to tier, "had_save" to flag(hadSave)))

    data class SudokuContinue(val tier: String) :
        AnalyticsEvent("sudoku_continue", mapOf("tier" to tier))

    /** A win just unlocked [tier]. */
    data class SudokuTierUnlock(val tier: String) :
        AnalyticsEvent("sudoku_tier_unlock", mapOf("tier" to tier))

    /** Sudoku tutorial events carry [size] = board size label ("4x4" | "6x6" | "8x8" | "9x9"); one tutorial per size. */
    data class SudokuTutorialBegin(val size: String) :
        AnalyticsEvent("sudoku_tutorial_begin", mapOf("size" to size))

    data class SudokuTutorialStep(val lesson: Int, val step: Int, val size: String) :
        AnalyticsEvent(
            "sudoku_tutorial_step",
            mapOf("lesson" to lesson.toLong(), "step" to step.toLong(), "size" to size),
        )

    /** [completed] 1 = reached the end of the last lesson, 0 = skipped / backed out. */
    data class SudokuTutorialEnd(val completed: Boolean, val size: String) :
        AnalyticsEvent("sudoku_tutorial_end", mapOf("completed" to flag(completed), "size" to size))

    // ---- home ----

    /** [mode]: "levels" | "sudoku" (the tab tapped). */
    data class HomeModeTab(val mode: String) : AnalyticsEvent("home_mode_tab", mapOf("mode" to mode))

    /** A map node was tapped (level sheet opens). */
    data class LevelSelect(val level: Int, val world: Int) :
        AnalyticsEvent("level_select", mapOf("level_number" to level.toLong(), "world" to world.toLong()))

    data object StreakDialogOpen : AnalyticsEvent("streak_dialog_open")

    /** Streak milestone reward claimed. [day] = cycle day (3/7/14/20/30), [cycle] = 1-based 30-day cycle. */
    data class StreakRewardClaim(val day: Int, val cycle: Int) :
        AnalyticsEvent("streak_reward_claim", mapOf("day" to day.toLong(), "cycle" to cycle.toLong()))

    // ---- shop / billing ----

    /** [entryPoint]: "booster_dialog" (Shop opened from a booster prompt) or "other". */
    data class ShopOpen(val entryPoint: String) : AnalyticsEvent("shop_open", mapOf("entry_point" to entryPoint))

    /**
     * [productId] = Play product id (e.g. "cat_pack_5"). GA4 recommended name; `item_id` is the GA4 key.
     * [value] / [currency] (GA4 `value`, `currency`) only when Play price details are loaded.
     */
    data class BeginCheckout(val productId: String, val value: Double? = null, val currency: String? = null) :
        AnalyticsEvent(
            "begin_checkout",
            buildMap<String, Any> {
                put("item_id", productId)
                if (value != null && !currency.isNullOrEmpty()) {
                    put("value", value)
                    put("currency", currency)
                }
            },
        )

    data class PurchaseSuccess(val productId: String) :
        AnalyticsEvent("purchase_success", mapOf("product_id" to productId))

    /**
     * GA4 reserved `purchase` (revenue). [value] = price in [currency] (ISO 4217), [transactionId] = Play order id.
     * `items` = list of item maps (the Firebase logger turns it into a Bundle array).
     */
    data class Purchase(
        val productId: String,
        val value: Double,
        val currency: String,
        val transactionId: String,
    ) : AnalyticsEvent(
        "purchase",
        mapOf(
            "value" to value,
            "currency" to currency,
            "transaction_id" to transactionId,
            "items" to listOf(mapOf<String, Any>("item_id" to productId)),
        ),
    )

    /** [reason]: short code ("cancelled", "error", "unavailable", ...). */
    data class PurchaseFail(val productId: String, val reason: String) :
        AnalyticsEvent("purchase_fail", mapOf("product_id" to productId, "reason" to reason))

    data class RestorePurchases(val restored: Int) :
        AnalyticsEvent("restore_purchases", mapOf("restored" to restored.toLong()))

    // ---- ads (our own funnel; revenue comes from the GA4-AdMob link) ----

    /** [action]: "shown" | "failed". [placement]: e.g. "win", "retry", "sudoku_win". */
    data class AdInterstitial(val action: String, val placement: String) :
        AnalyticsEvent("ad_interstitial", mapOf("action" to action, "placement" to placement))

    /** [placement]: "booster_cat", "booster_hint", "booster_mouse" or "revive". */
    data class RewardedEarned(val placement: String) :
        AnalyticsEvent("rewarded_earned", mapOf("placement" to placement))

    data class RewardedFailed(val placement: String) :
        AnalyticsEvent("rewarded_failed", mapOf("placement" to placement))

    // ---- settings / misc ----

    /** [setting]: lowercase toggle name (music, sfx, voice, haptics, pattern_mode). */
    data class SettingChange(val setting: String, val enabled: Boolean) :
        AnalyticsEvent("settings_change", mapOf("setting" to setting, "enabled" to flag(enabled)))

    data class SettingsOpen(val entryPoint: String) :
        AnalyticsEvent("settings_open", mapOf("entry_point" to entryPoint))

    /** [action]: "shown", "dismissed", "store_listing" (4-5 stars) or "submitted_low" (1-3 stars). */
    data class RatePrompt(val action: String) : AnalyticsEvent("rate_prompt", mapOf("action" to action))
}

private fun flag(value: Boolean): Long = if (value) 1L else 0L
