package com.threetech.catpuzzle.domain.engine

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakCalculatorTest {
    private val today = LocalDate.of(2026, 10, 28).toEpochDay() // a Wednesday

    @Test
    fun emptyHistoryIsZero() {
        assertEquals(0, StreakCalculator.currentStreak(emptySet(), today))
        assertEquals(0, StreakCalculator.bestStreak(emptyList()))
    }

    @Test
    fun consecutiveDaysEndingToday() {
        val days = setOf(today - 2, today - 1, today)
        assertEquals(3, StreakCalculator.currentStreak(days, today))
    }

    @Test
    fun streakStaysAliveWhenTodayNotPlayedYet() {
        val days = setOf(today - 3, today - 2, today - 1)
        assertEquals(3, StreakCalculator.currentStreak(days, today))
    }

    @Test
    fun gapResetsCurrentStreak() {
        val days = setOf(today - 5, today - 4, today - 3, today - 2) // yesterday missed
        assertEquals(0, StreakCalculator.currentStreak(days, today))
        assertEquals(4, StreakCalculator.bestStreak(days))
    }

    @Test
    fun gapInsideHistorySplitsRunsAndBestKeepsLongest() {
        val days = setOf(today - 10, today - 9, today - 8, today - 7, today - 1, today)
        assertEquals(2, StreakCalculator.currentStreak(days, today))
        assertEquals(4, StreakCalculator.bestStreak(days))
    }

    @Test
    fun sameDayIsIdempotentInASet() {
        val once = setOf(today)
        val twice = setOf(today, today)
        assertEquals(StreakCalculator.compute(once, today), StreakCalculator.compute(twice, today))
        assertEquals(1, StreakCalculator.currentStreak(twice, today))
    }

    @Test
    fun bestStreakIgnoresDuplicatesAndOrder() {
        assertEquals(3, StreakCalculator.bestStreak(listOf(5L, 3L, 4L, 4L, 20L)))
    }

    @Test
    fun streakCrossesDstTransitionsAsConsecutiveDays() {
        // US spring-forward 2026-03-08, EU 2026-03-29, US fall-back 2026-11-01: calendar days stay +1.
        for (d in listOf(LocalDate.of(2026, 3, 8), LocalDate.of(2026, 3, 29), LocalDate.of(2026, 11, 1))) {
            val e = d.toEpochDay()
            val days = setOf(e - 1, e, e + 1)
            assertEquals(3, StreakCalculator.currentStreak(days, e + 1))
            assertEquals(3, StreakCalculator.bestStreak(days))
        }
    }

    @Test
    fun streakCrossesMonthAndYearBoundaries() {
        val newYear = LocalDate.of(2027, 1, 1).toEpochDay()
        val days = setOf(newYear - 2, newYear - 1, newYear)
        assertEquals(3, StreakCalculator.currentStreak(days, newYear))
    }

    @Test
    fun weekViewIsMondayToSunday() {
        val monday = LocalDate.ofEpochDay(today).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
        val week = StreakCalculator.weekView(setOf(monday, today), today)
        assertEquals(7, week.size)
        assertEquals(DayOfWeek.MONDAY, week.first().dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, week.last().dayOfWeek)
        assertEquals(monday, week.first().epochDay)
        assertEquals((0..6).map { monday + it }, week.map { it.epochDay })
        assertTrue(week[0].played)
        assertFalse(week[1].played)
        assertTrue(week[2].isToday)
        assertTrue(week[2].played)
        assertFalse(week[2].isFuture)
        assertTrue(week[3].isFuture)
        assertFalse(week[1].isFuture)
    }

    @Test
    fun weekViewOnSundayStillStartsOnMonday() {
        val sunday = LocalDate.of(2026, 11, 1).toEpochDay()
        val week = StreakCalculator.weekView(emptySet(), sunday)
        assertEquals(DayOfWeek.SUNDAY, week.last().dayOfWeek)
        assertEquals(sunday, week.last().epochDay)
        assertTrue(week.last().isToday)
        assertEquals(DayOfWeek.MONDAY, week.first().dayOfWeek)
    }

    @Test
    fun computeCombinesEverything() {
        val info = StreakCalculator.compute(setOf(today - 1, today), today)
        assertEquals(2, info.current)
        assertEquals(2, info.best)
        assertTrue(info.playedToday)
        assertEquals(7, info.week.size)
    }
}
