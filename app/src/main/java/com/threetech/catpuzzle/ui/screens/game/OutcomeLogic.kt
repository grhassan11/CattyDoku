package com.threetech.catpuzzle.ui.screens.game

import kotlin.math.roundToInt

/** Headline lettering variant of the win dialog. The composable maps each to a string resource. */
enum class WinHeadline { FIRST_WIN, MILESTONE_5, MILESTONE_10, MILESTONE_25, STANDARD, DAILY }

/** Encouraging line under the mascot. The composable maps each to a string resource. */
enum class WinMessage { FIRST_WIN, PERFECT, GOOD, TOUGH }

/** Pure presentation rules of the finished-level dialogs. No Android, unit tested. */
object OutcomeLogic {
    const val COUNT_UP_MIN_MS = 600
    const val COUNT_UP_MAX_MS = 1_600

    /** Headline for a won level; [daily] wins always read "Daily complete!". */
    fun headline(level: Int, daily: Boolean = false): WinHeadline = if (daily) WinHeadline.DAILY else when (level) {
        1 -> WinHeadline.FIRST_WIN
        5 -> WinHeadline.MILESTONE_5
        10 -> WinHeadline.MILESTONE_10
        25 -> WinHeadline.MILESTONE_25
        else -> WinHeadline.STANDARD
    }

    /** First win always gets its own line; otherwise by how clean the run was. */
    fun message(level: Int, heartsLost: Int, hintsUsed: Int, daily: Boolean = false): WinMessage = when {
        level == 1 && !daily -> WinMessage.FIRST_WIN
        heartsLost <= 0 && hintsUsed <= 0 -> WinMessage.PERFECT
        heartsLost >= 2 -> WinMessage.TOUGH
        else -> WinMessage.GOOD
    }

    /** Score shown at animation [progress] 0..1 (clamped); exactly [target] at 1. */
    fun countUpValue(target: Int, progress: Float): Int {
        if (target <= 0) return 0
        val p = progress.coerceIn(0f, 1f)
        return if (p >= 1f) target else (target * p).roundToInt().coerceIn(0, target)
    }

    /** Longer scores count longer, within [COUNT_UP_MIN_MS]..[COUNT_UP_MAX_MS]. */
    fun countUpDurationMs(target: Int): Int =
        (COUNT_UP_MIN_MS + target.coerceAtLeast(0) / 2).coerceIn(COUNT_UP_MIN_MS, COUNT_UP_MAX_MS)

    /** "m:ss" for the stats row. Negative input is treated as 0. */
    fun formatElapsed(totalSeconds: Int): String {
        val s = totalSeconds.coerceAtLeast(0)
        return String.format(java.util.Locale.ROOT, "%d:%02d", s / 60, s % 60)
    }
}
