package com.tm.meow.sudoku.puzzle.domain.engine

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuReminderPolicyTest {
    private val day = LocalDate.of(2026, 11, 5)
    private fun at(h: Int, m: Int = 0): LocalDateTime = day.atTime(h, m)

    @Test
    fun twoHoursLaterInsideWindowIsKept() {
        assertEquals(at(12, 30), SudokuReminderPolicy.fireAt(at(10, 30)))
        assertEquals(2L * 3_600_000L, SudokuReminderPolicy.delayMs(at(10, 30)))
    }

    @Test
    fun windowEdgesAreInclusive() {
        assertEquals(at(9), SudokuReminderPolicy.fireAt(at(7)))
        assertEquals(at(21), SudokuReminderPolicy.fireAt(at(19)))
    }

    @Test
    fun beforeWindowMovesToSameDayNineAm() {
        assertEquals(at(9), SudokuReminderPolicy.fireAt(at(2, 15)))
        assertEquals(at(9), SudokuReminderPolicy.fireAt(at(6, 59)))
    }

    @Test
    fun afterWindowMovesToNextDayNineAm() {
        assertEquals(day.plusDays(1).atTime(9, 0), SudokuReminderPolicy.fireAt(at(19, 1)))
        assertEquals(day.plusDays(1).atTime(9, 0), SudokuReminderPolicy.fireAt(at(22)))
    }

    @Test
    fun crossingMidnightLandsInEarlyMorningThenNineAm() {
        // 23:00 + 2 h = 01:00 next day -> 09:00 next day
        assertEquals(day.plusDays(1).atTime(9, 0), SudokuReminderPolicy.fireAt(at(23)))
        assertEquals(10L * 3_600_000L, SudokuReminderPolicy.delayMs(at(23)))
    }

    @Test
    fun delayIsNeverNegative() {
        assertTrue(SudokuReminderPolicy.delayMs(at(0)) > 0L)
    }

    @Test
    fun schedulesOnlyWhenToggleOnAndGameExists() {
        assertTrue(SudokuReminderPolicy.shouldSchedule(true, true))
        assertFalse(SudokuReminderPolicy.shouldSchedule(false, true))
        assertFalse(SudokuReminderPolicy.shouldSchedule(true, false))
    }

    private fun notify(
        setting: Boolean = true,
        allowed: Boolean = true,
        game: Boolean = true,
        minute: Int = 14 * 60,
    ) = SudokuReminderPolicy.shouldNotify(setting, allowed, game, minute)

    @Test
    fun notifiesWhenEverythingHolds() {
        assertTrue(notify())
        assertTrue(notify(minute = 9 * 60))
        assertTrue(notify(minute = 22 * 60 - 1)) // late delivery still inside the grace
    }

    @Test
    fun silentWhenAnyConditionFails() {
        assertFalse(notify(setting = false))
        assertFalse(notify(allowed = false))
        assertFalse(notify(game = false))
    }

    @Test
    fun silentOutsideWindowAndGrace() {
        assertFalse(notify(minute = 9 * 60 - 1))
        assertFalse(notify(minute = 22 * 60))
    }
}
