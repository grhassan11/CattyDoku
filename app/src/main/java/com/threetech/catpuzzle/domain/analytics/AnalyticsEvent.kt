package com.threetech.catpuzzle.domain.analytics

import com.threetech.catpuzzle.domain.model.GameResult
import com.threetech.catpuzzle.domain.model.GameState

/**
 * Typed analytics events. Names and param keys are STABLE (snake_case, <= 40 chars): never rename, only add.
 * Param values are Long / String only (booleans are 0/1 Long). No PII, no user ids, no ad-unit ids.
 * Pure Kotlin: the Firebase mapping lives in `firebase/`.
 */
sealed class AnalyticsEvent(val name: String, val params: Map<String, Any> = emptyMap()) {

    data object TutorialBegin : AnalyticsEvent("tutorial_begin")

    data class TutorialComplete(val skipped: Boolean) :
        AnalyticsEvent("tutorial_complete", mapOf("skipped" to flag(skipped)))

    /** A fresh attempt starts (new level, or restart). Resuming a saved game does not log this. */
    data class LevelStart(val level: Int, val boardSize: Int) :
        AnalyticsEvent("level_start", mapOf("level" to level.toLong(), "board_size" to boardSize.toLong()))

    data class LevelWin(
        val level: Int,
        val boardSize: Int,
        val timeSec: Int,
        val heartsLost: Int,
        val hintsUsed: Int,
        val score: Int,
    ) : AnalyticsEvent(
        "level_win",
        mapOf(
            "level" to level.toLong(),
            "board_size" to boardSize.toLong(),
            "time_sec" to timeSec.toLong(),
            "hearts_lost" to heartsLost.toLong(),
            "hints_used" to hintsUsed.toLong(),
            "score" to score.toLong(),
        ),
    )

    /** [level] is 0 for the daily challenge. */
    data class LevelLose(val level: Int, val boardSize: Int, val timeSec: Int, val hintsUsed: Int, val daily: Boolean) :
        AnalyticsEvent(
            "level_lose",
            mapOf(
                "level" to level.toLong(),
                "board_size" to boardSize.toLong(),
                "time_sec" to timeSec.toLong(),
                "hints_used" to hintsUsed.toLong(),
                "daily" to flag(daily),
            ),
        )

    data class HintUsed(val level: Int, val boardSize: Int) :
        AnalyticsEvent("hint_used", mapOf("level" to level.toLong(), "board_size" to boardSize.toLong()))

    /** [type]: "cat" or "mouse" (hint has its own [HintUsed]). */
    data class BoosterUsed(val type: String, val level: Int) :
        AnalyticsEvent("booster_used", mapOf("type" to type, "level" to level.toLong()))

    /** [action]: "shown", "dismissed", "store_listing" (4-5 stars) or "submitted_low" (1-3 stars). [stars] 0 when not applicable. */
    data class RatePrompt(val action: String, val stars: Int) :
        AnalyticsEvent("rate_prompt", mapOf("action" to action, "stars" to stars.toLong()))

    data class DailyStart(val boardSize: Int) :
        AnalyticsEvent("daily_start", mapOf("board_size" to boardSize.toLong()))

    data class DailyComplete(val boardSize: Int, val timeSec: Int, val heartsLost: Int, val hintsUsed: Int, val score: Int) :
        AnalyticsEvent(
            "daily_complete",
            mapOf(
                "board_size" to boardSize.toLong(),
                "time_sec" to timeSec.toLong(),
                "hearts_lost" to heartsLost.toLong(),
                "hints_used" to hintsUsed.toLong(),
                "score" to score.toLong(),
            ),
        )

    data object StreakDialogOpen : AnalyticsEvent("streak_dialog_open")

    /** Streak milestone reward claimed. [day] = cycle day (3/7/14/20/30), [cycle] = 1-based 30-day cycle. */
    data class StreakRewardClaim(val day: Int, val cycle: Int) :
        AnalyticsEvent("streak_reward_claim", mapOf("day" to day.toLong(), "cycle" to cycle.toLong()))

    /** [source]: "booster_dialog" (Shop opened from a booster prompt) or "other". */
    data class ShopOpen(val source: String) : AnalyticsEvent("shop_open", mapOf("source" to source))

    /** [productId] = Play product id (e.g. "cat_pack_5"). */
    data class PurchaseSuccess(val productId: String) :
        AnalyticsEvent("purchase_success", mapOf("product_id" to productId))

    /** [placement]: "booster_cat", "booster_hint", "booster_mouse" or "revive". */
    data class RewardedEarned(val placement: String) :
        AnalyticsEvent("rewarded_earned", mapOf("placement" to placement))

    /** [setting]: lowercase toggle name (music, sfx, voice, haptics, pattern_mode). */
    data class SettingChange(val setting: String, val enabled: Boolean) :
        AnalyticsEvent("settings_change", mapOf("setting" to setting, "enabled" to flag(enabled)))

    companion object {
        /**
         * Outcome of a finished level / daily. Win -> [LevelWin] or [DailyComplete]; loss -> [LevelLose].
         * Hearts lost derives from [GameResult.heartsLeft] (clamped 0..MAX_HEARTS).
         */
        fun levelFinished(result: GameResult, boardSize: Int, daily: Boolean): AnalyticsEvent {
            val heartsLost = (GameState.MAX_HEARTS - result.heartsLeft).coerceIn(0, GameState.MAX_HEARTS)
            return when {
                result.won && daily ->
                    DailyComplete(boardSize, result.elapsedSeconds, heartsLost, result.hintsUsed, result.score.total)
                result.won ->
                    LevelWin(result.levelIndex, boardSize, result.elapsedSeconds, heartsLost, result.hintsUsed, result.score.total)
                else ->
                    LevelLose(if (daily) 0 else result.levelIndex, boardSize, result.elapsedSeconds, result.hintsUsed, daily)
            }
        }
    }
}

private fun flag(value: Boolean): Long = if (value) 1L else 0L
