package com.threetech.catpuzzle.domain.engine

import com.threetech.catpuzzle.domain.model.StreakCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakCalendarBuilderTest {
    private val today = 20_752L

    @Test
    fun spansThirtyDaysEndingToday() {
        val c = StreakCalendarBuilder.build(emptySet(), emptySet(), today)
        assertEquals(StreakCalendar.SPAN, c.recent.size)
        assertEquals(today, c.recent.last().epochDay)
        assertEquals(today - 29, c.recent.first().epochDay)
        assertTrue(c.recent.last().isToday)
        assertEquals(1, c.recent.count { it.isToday })
        assertTrue(c.recent.none { it.played || it.dailyDone })
        assertEquals(0, c.info.current)
    }

    @Test
    fun marksPlayedAndDailyDays() {
        val played = setOf(today - 2, today - 1, today)
        val done = setOf(today - 1)
        val c = StreakCalendarBuilder.build(played, done, today)
        val byDay = c.recent.associateBy { it.epochDay }
        assertTrue(byDay.getValue(today - 2).played)
        assertFalse(byDay.getValue(today - 2).dailyDone)
        assertTrue(byDay.getValue(today - 1).dailyDone)
        assertTrue(byDay.getValue(today - 1).played)
        assertEquals(1, c.dailyCompletedTotal)
        assertEquals(3, c.info.current)
    }

    @Test
    fun dailyCompletionCountsAsPlayedDayEvenIfStreakRowMissing() {
        val c = StreakCalendarBuilder.build(emptySet(), setOf(today - 3), today)
        assertTrue(c.recent.first { it.epochDay == today - 3 }.played)
    }

    @Test
    fun streakWithDailyDayIsContinuous() {
        // played yesterday via a level, today only via the daily: streak 2
        val played = setOf(today - 1, today)
        val c = StreakCalendarBuilder.build(played, setOf(today), today)
        assertEquals(2, c.info.current)
        assertEquals(2, c.info.best)
        assertTrue(c.info.playedToday)
    }

    @Test
    fun oldCompletionsOutsideWindowStillCounted() {
        val c = StreakCalendarBuilder.build(emptySet(), setOf(today - 100, today - 5), today)
        assertEquals(2, c.dailyCompletedTotal)
        assertEquals(1, c.recent.count { it.dailyDone })
    }
}
