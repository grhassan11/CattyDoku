package com.threetech.catpuzzle.domain.model

import com.threetech.catpuzzle.domain.engine.DailyAvailability

/**
 * Live daily-challenge status. [today] = local epoch day at emission time.
 * [completedDays] = every local date whose daily was completed.
 */
data class DailyStatus(
    val today: Long,
    val highestLevelWon: Int,
    val completedDays: Set<Long>,
    val availability: DailyAvailability,
) {
    val doneToday: Boolean get() = today in completedDays
}

/** One dot of the streak calendar. [dailyDone] = the daily of that date was completed. */
data class CalendarDay(
    val epochDay: Long,
    val played: Boolean,
    val dailyDone: Boolean,
    val isToday: Boolean,
)

/**
 * Streak dialog data: [info] (current, best, Mon-Sun week) + [recent] (the last
 * [StreakCalendar.SPAN] days, oldest first, ending today) + total daily completions.
 */
data class StreakCalendar(
    val info: StreakInfo,
    val recent: List<CalendarDay>,
    val dailyCompletedTotal: Int,
) {
    companion object {
        const val SPAN = 30
    }
}
