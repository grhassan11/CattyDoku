package com.threetech.catpuzzle.domain.engine

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakReminderPolicyTest {
    private val today = 20_000L
    private val evening = 19 * 60 + 5

    private fun notify(
        played: Set<Long>,
        minute: Int = evening,
        setting: Boolean = true,
        remote: Boolean = true,
    ) = StreakReminderPolicy.shouldNotify(played, today, minute, setting, remote)

    @Test
    fun notifiesWhenStreakAliveAndNotPlayedToday() {
        assertTrue(notify(setOf(today - 1, today - 2)))
        assertEquals(2, StreakReminderPolicy.atRiskStreak(setOf(today - 1, today - 2), today))
    }

    @Test
    fun silentWhenPlayedToday() {
        assertFalse(notify(setOf(today, today - 1)))
        assertEquals(0, StreakReminderPolicy.atRiskStreak(setOf(today, today - 1), today))
    }

    @Test
    fun silentWhenStreakDead() {
        assertFalse(notify(setOf(today - 2, today - 3)))
        assertFalse(notify(emptySet()))
    }

    @Test
    fun silentOutsideEveningWindow() {
        val played = setOf(today - 1)
        assertFalse(notify(played, minute = 18 * 60 + 59))
        assertTrue(notify(played, minute = 19 * 60))
        assertTrue(notify(played, minute = 20 * 60 + 59))
        assertFalse(notify(played, minute = 21 * 60))
    }

    @Test
    fun silentWhenDisabledLocallyOrRemotely() {
        val played = setOf(today - 1)
        assertFalse(notify(played, setting = false))
        assertFalse(notify(played, remote = false))
    }

    @Test
    fun schedulesOnlyWhileStreakAlive() {
        assertTrue(StreakReminderPolicy.shouldSchedule(setOf(today), today, true, true))
        assertTrue(StreakReminderPolicy.shouldSchedule(setOf(today - 1), today, true, true))
        assertFalse(StreakReminderPolicy.shouldSchedule(setOf(today - 2), today, true, true))
        assertFalse(StreakReminderPolicy.shouldSchedule(setOf(today), today, false, true))
        assertFalse(StreakReminderPolicy.shouldSchedule(setOf(today), today, true, false))
    }

    @Test
    fun delayTargetsNextSevenPm() {
        val morning = LocalDateTime.of(2026, 3, 10, 8, 0)
        assertEquals(11 * 3_600_000L, StreakReminderPolicy.delayUntilNextFireMs(morning, playedToday = false))
        // already played today: skip to tomorrow 19:00
        assertEquals(35 * 3_600_000L, StreakReminderPolicy.delayUntilNextFireMs(morning, playedToday = true))
        // past 19:00: tomorrow
        val late = LocalDateTime.of(2026, 3, 10, 20, 0)
        assertEquals(23 * 3_600_000L, StreakReminderPolicy.delayUntilNextFireMs(late, playedToday = false))
    }

    @Test
    fun copyIndexIsDeterministicAndCoversAllVariants() {
        assertEquals(ReminderCopy.indexFor(20_000L, 3), ReminderCopy.indexFor(20_000L, 3))
        assertEquals(setOf(0, 1, 2), (20_000L..20_005L).map { ReminderCopy.indexFor(it, 3) }.toSet())
        assertEquals(0, ReminderCopy.indexFor(-3L, 3))
        assertTrue((-10L..10L).all { ReminderCopy.indexFor(it, 7) in 0..6 })
    }
}
