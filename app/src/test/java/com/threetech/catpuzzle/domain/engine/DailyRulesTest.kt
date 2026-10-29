package com.threetech.catpuzzle.domain.engine

import com.threetech.catpuzzle.domain.usecase.GetOrCreatePuzzle
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyRulesTest {
    private val base = GetOrCreatePuzzle.DEFAULT_BASE_SEED

    @Test
    fun keyIsNegativeAndRoundTrips() {
        for (day in longArrayOf(1L, 20_000L, 20_752L, 40_000L)) {
            val key = DailyRules.keyForDay(day)
            assertTrue(key < 0)
            assertTrue(DailyRules.isDailyKey(key))
            assertEquals(day, DailyRules.dayForKey(key))
        }
        assertFalse(DailyRules.isDailyKey(1))
        assertFalse(DailyRules.isDailyKey(0))
    }

    @Test
    fun seedIsDeterministicPerDate() {
        assertEquals(DailyRules.seedFor(base, 20_752L), DailyRules.seedFor(base, 20_752L))
        assertNotEquals(DailyRules.seedFor(base, 20_752L), DailyRules.seedFor(base, 20_753L))
        assertNotEquals(DailyRules.seedFor(base, 20_752L), DailyRules.seedFor(base + 1, 20_752L))
    }

    @Test
    fun dailySeedsNeverCollideWithLevelSeeds() {
        // level seed as PuzzleGenerator.generateForLevel derives it, incl. StartLevel retry bases
        val levelSeeds = HashSet<Long>()
        for (attempt in 0 until 5) {
            val b = if (attempt == 0) base else SeededRng.mix(base, attempt.toLong())
            for (level in 1..3_000) levelSeeds.add(SeededRng.mix(b, level.toLong()))
        }
        for (day in 19_000L..24_000L) {
            for (attempt in 0 until 5) {
                assertFalse("day $day attempt $attempt", DailyRules.retrySeed(base, day, attempt) in levelSeeds)
            }
        }
    }

    @Test
    fun retrySeedZeroIsThePlainDailySeedAndRetriesDiffer() {
        assertEquals(DailyRules.seedFor(base, 20_752L), DailyRules.retrySeed(base, 20_752L, 0))
        val seeds = (0 until 5).map { DailyRules.retrySeed(base, 20_752L, it) }.toSet()
        assertEquals(5, seeds.size)
    }

    @Test
    fun specIsSixBySixWithWeekdayDifficultyAndDailyKey() {
        val monday = LocalDate.of(2026, 10, 26)
        assertEquals(DayOfWeek.MONDAY, monday.dayOfWeek)
        val spec = DailyRules.specFor(monday.toEpochDay())
        assertEquals(6, spec.size)
        assertEquals(DailyRules.keyForDay(monday.toEpochDay()), spec.levelIndex)
        assertEquals(3, spec.targetTechnique)
        assertEquals(5, DailyRules.targetTechniqueFor(monday.plusDays(5).toEpochDay())) // Saturday
        assertEquals(5, DailyRules.targetTechniqueFor(monday.plusDays(6).toEpochDay())) // Sunday
        for (i in 0 until 7) {
            val t = DailyRules.targetTechniqueFor(monday.plusDays(i.toLong()).toEpochDay())
            assertTrue("target $t in 6x6 band", t in 3..5)
        }
    }

    @Test
    fun dateRolloverChangesKeySeedAndSpec() {
        val d = 20_752L
        assertNotEquals(DailyRules.keyForDay(d), DailyRules.keyForDay(d + 1))
        assertNotEquals(DailyRules.seedFor(base, d), DailyRules.seedFor(base, d + 1))
        assertEquals(DailyRules.keyForDay(d + 1), DailyRules.specFor(d + 1).levelIndex)
    }

    @Test
    fun unlockRuleIsLevelFiveWon() {
        assertEquals(5, DailyRules.UNLOCK_LEVEL)
        assertFalse(DailyRules.isUnlocked(0))
        assertFalse(DailyRules.isUnlocked(4))
        assertTrue(DailyRules.isUnlocked(5))
        assertTrue(DailyRules.isUnlocked(40))
    }

    @Test
    fun availabilityStates() {
        assertEquals(DailyAvailability.Locked(5), DailyRules.availability(0, doneToday = false))
        assertEquals(DailyAvailability.Locked(5), DailyRules.availability(4, doneToday = false))
        assertEquals(DailyAvailability.Locked(5), DailyRules.availability(4, doneToday = true)) // lock wins
        assertEquals(DailyAvailability.Available, DailyRules.availability(5, doneToday = false))
        assertEquals(DailyAvailability.Done, DailyRules.availability(9, doneToday = true))
    }
}
