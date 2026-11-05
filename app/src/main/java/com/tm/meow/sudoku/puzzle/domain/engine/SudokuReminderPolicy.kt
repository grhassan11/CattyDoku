package com.tm.meow.sudoku.puzzle.domain.engine

import java.time.Duration
import java.time.LocalDateTime

/**
 * Pure policy for the "unfinished Sudoku game" one-shot reminder. No clock, no Android: `now` is injected.
 *
 * Target = leave time + [DELAY_HOURS] h, clamped into the [WINDOW_START_MINUTE_OF_DAY]..[WINDOW_END_MINUTE_OF_DAY]
 * local window: before the window = same day 09:00, after it = next day 09:00 (never wakes the player at night).
 * Delivery may be late (Doze), so the fire-time check accepts up to [FIRE_GRACE_END_MINUTE_OF_DAY]; later = skipped.
 */
object SudokuReminderPolicy {
    const val DELAY_HOURS = 2L
    const val WINDOW_START_MINUTE_OF_DAY = 9 * 60
    const val WINDOW_END_MINUTE_OF_DAY = 21 * 60
    const val FIRE_GRACE_END_MINUTE_OF_DAY = 22 * 60

    /** Book a reminder only while the toggle is on and an unfinished game exists. */
    fun shouldSchedule(settingEnabled: Boolean, hasUnfinishedGame: Boolean): Boolean =
        settingEnabled && hasUnfinishedGame

    /** Fire-time decision. All inputs are re-read when the worker runs. */
    fun shouldNotify(
        settingEnabled: Boolean,
        notificationsAllowed: Boolean,
        hasUnfinishedGame: Boolean,
        nowMinuteOfDay: Int,
    ): Boolean =
        settingEnabled && notificationsAllowed && hasUnfinishedGame &&
            nowMinuteOfDay in WINDOW_START_MINUTE_OF_DAY until FIRE_GRACE_END_MINUTE_OF_DAY

    /** Fire moment for a player who left the game at [now]. */
    fun fireAt(now: LocalDateTime): LocalDateTime {
        val target = now.plusHours(DELAY_HOURS)
        val date = target.toLocalDate()
        val minute = target.hour * 60 + target.minute
        return when {
            minute < WINDOW_START_MINUTE_OF_DAY -> date.atTime(WINDOW_START_MINUTE_OF_DAY / 60, 0)
            minute > WINDOW_END_MINUTE_OF_DAY -> date.plusDays(1).atTime(WINDOW_START_MINUTE_OF_DAY / 60, 0)
            else -> target
        }
    }

    fun delayMs(now: LocalDateTime): Long =
        Duration.between(now, fireAt(now)).toMillis().coerceAtLeast(0L)
}
