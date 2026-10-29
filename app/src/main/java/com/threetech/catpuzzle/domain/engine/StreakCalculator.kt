package com.threetech.catpuzzle.domain.engine

import com.threetech.catpuzzle.domain.model.StreakInfo
import com.threetech.catpuzzle.domain.model.WeekDay
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Pure streak math over a set of played LOCAL dates (epoch days). No clock, no Android.
 * Epoch-day arithmetic is calendar based, so DST shifts cannot split or merge days.
 */
object StreakCalculator {

    /**
     * Consecutive played days ending today. If today is not played yet but yesterday is, the streak is
     * still alive (counted back from yesterday); one fully missed day resets it to 0.
     */
    fun currentStreak(playedDays: Set<Long>, today: Long): Int {
        val start = when {
            today in playedDays -> today
            (today - 1) in playedDays -> today - 1
            else -> return 0
        }
        var count = 0
        var day = start
        while (day in playedDays) {
            count++
            day--
        }
        return count
    }

    /** Longest run of consecutive days anywhere in history. */
    fun bestStreak(playedDays: Collection<Long>): Int {
        if (playedDays.isEmpty()) return 0
        val sorted = playedDays.toSortedSet().toList()
        var best = 1
        var run = 1
        for (i in 1 until sorted.size) {
            run = if (sorted[i] == sorted[i - 1] + 1) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }

    /** Monday..Sunday of the week containing [today]. */
    fun weekView(playedDays: Set<Long>, today: Long): List<WeekDay> {
        val dow = LocalDate.ofEpochDay(today).dayOfWeek
        val monday = today - (dow.value - DayOfWeek.MONDAY.value)
        return List(7) { i ->
            val day = monday + i
            WeekDay(
                epochDay = day,
                dayOfWeek = LocalDate.ofEpochDay(day).dayOfWeek,
                played = day in playedDays,
                isToday = day == today,
                isFuture = day > today,
            )
        }
    }

    fun compute(playedDays: Set<Long>, today: Long): StreakInfo = StreakInfo(
        current = currentStreak(playedDays, today),
        best = bestStreak(playedDays),
        playedToday = today in playedDays,
        week = weekView(playedDays, today),
    )
}
