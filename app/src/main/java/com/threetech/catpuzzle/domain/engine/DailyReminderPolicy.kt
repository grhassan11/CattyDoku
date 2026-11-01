package com.threetech.catpuzzle.domain.engine

import java.time.Duration
import java.time.LocalDateTime

/**
 * Pure policy for the daily 20:00 reminder. No clock, no Android.
 * Fires EVERY day at [FIRE_MINUTE_OF_DAY] (20:00 local), whatever the player did that day. The only gates are the
 * Settings toggle and the Remote Config kill switch (OS permission is checked where it is posted). A late run
 * (Doze / battery) is still accepted until [WINDOW_END_MINUTE_OF_DAY] (22:00), then skipped.
 */
object DailyReminderPolicy {
    const val FIRE_MINUTE_OF_DAY = 20 * 60
    const val WINDOW_END_MINUTE_OF_DAY = 22 * 60

    /** Number of rotating texts (strings daily_reminder_title_1..7), see [ReminderCopy.indexFor]. */
    const val COPY_VARIANTS = 7

    /** Rotating "come play" texts for players below the Daily unlock level (strings play_reminder_title_1..10). */
    const val PLAY_COPY_VARIANTS = 10

    /** Daily-Challenge copy only once Daily is unlocked (same rule as the Home lock); below it use the generic play copy. */
    fun mentionsDaily(highestLevelWon: Int): Boolean = DailyRules.isUnlocked(highestLevelWon)

    /** Fire-time decision. Inputs are re-read when the worker runs. */
    fun shouldNotify(nowMinuteOfDay: Int, settingEnabled: Boolean, remoteEnabled: Boolean): Boolean =
        settingEnabled && remoteEnabled && nowMinuteOfDay in FIRE_MINUTE_OF_DAY until WINDOW_END_MINUTE_OF_DAY

    /** The chain never ends while the feature is on. */
    fun shouldSchedule(settingEnabled: Boolean, remoteEnabled: Boolean): Boolean = settingEnabled && remoteEnabled

    /** Delay to the next 20:00: today's slot if still ahead, otherwise tomorrow's. */
    fun delayUntilNextFireMs(now: LocalDateTime): Long {
        val todayFire = now.toLocalDate().atTime(FIRE_MINUTE_OF_DAY / 60, FIRE_MINUTE_OF_DAY % 60)
        val next = if (now.isBefore(todayFire)) todayFire else todayFire.plusDays(1)
        return Duration.between(now, next).toMillis().coerceAtLeast(0L)
    }
}
