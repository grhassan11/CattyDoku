package com.threetech.catpuzzle.domain.engine

import com.threetech.catpuzzle.domain.model.CalendarDay
import com.threetech.catpuzzle.domain.model.StreakCalendar

/** Pure builder for [StreakCalendar]. Epoch-day arithmetic only (DST / timezone safe). */
object StreakCalendarBuilder {
    fun build(playedDays: Set<Long>, completedDays: Set<Long>, today: Long): StreakCalendar {
        val start = today - (StreakCalendar.SPAN - 1)
        val recent = List(StreakCalendar.SPAN) { i ->
            val day = start + i
            val done = day in completedDays
            CalendarDay(
                epochDay = day,
                played = day in playedDays || done,
                dailyDone = done,
                isToday = day == today,
            )
        }
        return StreakCalendar(
            info = StreakCalculator.compute(playedDays, today),
            recent = recent,
            dailyCompletedTotal = completedDays.size,
        )
    }
}
