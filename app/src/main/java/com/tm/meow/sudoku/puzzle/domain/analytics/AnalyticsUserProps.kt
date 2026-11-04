package com.tm.meow.sudoku.puzzle.domain.analytics

/**
 * GA4 user property names. Max 25 custom dimensions per property: this set is 14. Names <= 24 chars, values <= 36.
 * Win counters are intentionally absent (derivable from level_end / level progress).
 */
object AnalyticsUserProps {
    const val MAX_LEVEL = "max_level"
    const val LEVELS_WON = "levels_won"
    const val SDK_EASY_PLAYS = "sdk_easy_plays"
    const val SDK_MEDIUM_PLAYS = "sdk_medium_plays"
    const val SDK_HARD_PLAYS = "sdk_hard_plays"
    const val SDK_EXPERT_PLAYS = "sdk_expert_plays"
    const val SDK_MASTER_PLAYS = "sdk_master_plays"
    const val SDK_EXTREME_PLAYS = "sdk_extreme_plays"
    const val SUDOKU_UNLOCKED = "sudoku_unlocked"
    const val ADS_REMOVED = "ads_removed"
    const val STREAK_DAYS = "streak_days"
    const val PAYER = "payer"
    const val TUTORIAL_DONE = "tutorial_done"
    const val NOTIF_ENABLED = "notif_enabled"

    val ALL = listOf(
        MAX_LEVEL, LEVELS_WON, SDK_EASY_PLAYS, SDK_MEDIUM_PLAYS, SDK_HARD_PLAYS, SDK_EXPERT_PLAYS, SDK_MASTER_PLAYS,
        SDK_EXTREME_PLAYS,
        SUDOKU_UNLOCKED, ADS_REMOVED, STREAK_DAYS, PAYER, TUTORIAL_DONE, NOTIF_ENABLED,
    )
}
