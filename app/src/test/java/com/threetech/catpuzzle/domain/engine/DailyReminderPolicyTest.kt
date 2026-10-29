package com.threetech.catpuzzle.domain.engine

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyReminderPolicyTest {

    @Test
    fun firesInsideWindowRegardlessOfGameState() {
        assertTrue(DailyReminderPolicy.shouldNotify(20 * 60, true, true))
        assertTrue(DailyReminderPolicy.shouldNotify(20 * 60 + 5, true, true))
    }

    @Test
    fun windowEdges() {
        assertFalse(DailyReminderPolicy.shouldNotify(19 * 60 + 59, true, true))
        assertTrue(DailyReminderPolicy.shouldNotify(21 * 60 + 59, true, true))
        assertFalse(DailyReminderPolicy.shouldNotify(22 * 60, true, true))
    }

    @Test
    fun silentWhenDisabledLocallyOrRemotely() {
        assertFalse(DailyReminderPolicy.shouldNotify(20 * 60, false, true))
        assertFalse(DailyReminderPolicy.shouldNotify(20 * 60, true, false))
    }

    @Test
    fun scheduleFollowsGatesOnly() {
        assertTrue(DailyReminderPolicy.shouldSchedule(true, true))
        assertFalse(DailyReminderPolicy.shouldSchedule(false, true))
        assertFalse(DailyReminderPolicy.shouldSchedule(true, false))
    }

    @Test
    fun delayTargetsNextEightPm() {
        val morning = LocalDateTime.of(2026, 3, 10, 8, 0)
        assertEquals(12 * 3_600_000L, DailyReminderPolicy.delayUntilNextFireMs(morning))
        val atFire = LocalDateTime.of(2026, 3, 10, 20, 0)
        assertEquals(24 * 3_600_000L, DailyReminderPolicy.delayUntilNextFireMs(atFire))
        val late = LocalDateTime.of(2026, 3, 10, 21, 0)
        assertEquals(23 * 3_600_000L, DailyReminderPolicy.delayUntilNextFireMs(late))
    }

    @Test
    fun firesAfterStreakReminder() {
        assertTrue(DailyReminderPolicy.FIRE_MINUTE_OF_DAY > StreakReminderPolicy.FIRE_MINUTE_OF_DAY)
    }

    @Test
    fun sevenVariantsCoveredOverAWeek() {
        val seen = (20_000L..20_006L).map { ReminderCopy.indexFor(it, DailyReminderPolicy.COPY_VARIANTS) }.toSet()
        assertEquals((0..6).toSet(), seen)
    }
}
