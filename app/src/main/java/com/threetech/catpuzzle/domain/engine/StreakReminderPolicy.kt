package com.threetech.catpuzzle.domain.engine

import java.time.Duration
import java.time.LocalDateTime

/**
 * Pure policy for the "streak is about to break" evening reminder. No clock, no Android.
 * Streak counting is win-only (daily_streak table) via [StreakCalculator].
 *
 * Fires around [FIRE_MINUTE_OF_DAY] (19:00 local). Delivery may be late (Doze / battery), so a run is still accepted
 * until [WINDOW_END_MINUTE_OF_DAY] (21:00); later than that it is skipped. One schedule per day = one notification max.
 */
object StreakReminderPolicy {
    const val FIRE_MINUTE_OF_DAY = 19 * 60
    const val WINDOW_END_MINUTE_OF_DAY = 21 * 60

    /** Live streak that a missing win today would break. 0 = nothing at risk (already played today, or no streak). */
    fun atRiskStreak(playedDays: Set<Long>, today: Long): Int =
        if (today in playedDays) 0 else StreakCalculator.currentStreak(playedDays, today)

    /** Fire-time decision. All inputs are re-read when the worker runs. */
    fun shouldNotify(
        playedDays: Set<Long>,
        today: Long,
        nowMinuteOfDay: Int,
        settingEnabled: Boolean,
        remoteEnabled: Boolean,
    ): Boolean =
        settingEnabled && remoteEnabled &&
            nowMinuteOfDay in FIRE_MINUTE_OF_DAY until WINDOW_END_MINUTE_OF_DAY &&
            atRiskStreak(playedDays, today) >= 1

    /** Keep a reminder scheduled only while a streak is alive and the feature is on. */
    fun shouldSchedule(playedDays: Set<Long>, today: Long, settingEnabled: Boolean, remoteEnabled: Boolean): Boolean =
        settingEnabled && remoteEnabled && StreakCalculator.currentStreak(playedDays, today) >= 1

    /**
     * Delay to the next 19:00. Today's slot is used only if it is still ahead and the player has not played today
     * (nothing to remind about otherwise).
     */
    fun delayUntilNextFireMs(now: LocalDateTime, playedToday: Boolean): Long {
        val todayFire = now.toLocalDate().atTime(FIRE_MINUTE_OF_DAY / 60, FIRE_MINUTE_OF_DAY % 60)
        val next = if (!playedToday && now.isBefore(todayFire)) todayFire else todayFire.plusDays(1)
        return Duration.between(now, next).toMillis().coerceAtLeast(0L)
    }
}

/** Deterministic copy rotation: same day = same variant, so it is testable. */
object ReminderCopy {
    fun indexFor(epochDay: Long, variants: Int): Int {
        require(variants > 0)
        return Math.floorMod(epochDay, variants.toLong()).toInt()
    }
}
