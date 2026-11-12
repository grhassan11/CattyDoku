package com.tm.meow.sudoku.puzzle.ui.screens.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameFeelTest {

    // ---- streak ----

    @Test
    fun correctPlacementAddsOne() {
        assertEquals(1, StreakLogic.next(0, correct = true, wrong = false))
        assertEquals(5, StreakLogic.next(4, correct = true, wrong = false))
    }

    @Test
    fun wrongMoveResetsToZero() {
        assertEquals(0, StreakLogic.next(7, correct = false, wrong = true))
        assertEquals(0, StreakLogic.next(0, correct = false, wrong = true))
    }

    @Test
    fun neutralStepsKeepTheStreak() {
        // X marks, undo, hint, conflicting-but-not-wrong cat: neither correct nor wrong.
        assertEquals(4, StreakLogic.next(4, correct = false, wrong = false))
    }

    @Test
    fun praiseOnlyOnExactThresholds() {
        assertNull(StreakLogic.praiseAt(0))
        assertNull(StreakLogic.praiseAt(2))
        assertEquals(PraiseKind.NICE, StreakLogic.praiseAt(3))
        assertNull(StreakLogic.praiseAt(4))
        assertEquals(PraiseKind.PURRFECT, StreakLogic.praiseAt(5))
        assertEquals(PraiseKind.MEOWVELOUS, StreakLogic.praiseAt(8))
        assertEquals(PraiseKind.CLAWSOME, StreakLogic.praiseAt(12))
        assertNull(StreakLogic.praiseAt(13))
    }

    @Test
    fun runningAStreakFiresEachMilestoneOnce() {
        var streak = 0
        val fired = ArrayList<PraiseKind>()
        repeat(14) {
            streak = StreakLogic.next(streak, correct = true, wrong = false)
            StreakLogic.praiseAt(streak)?.let(fired::add)
        }
        assertEquals(
            listOf(PraiseKind.NICE, PraiseKind.PURRFECT, PraiseKind.MEOWVELOUS, PraiseKind.CLAWSOME),
            fired,
        )
    }

    @Test
    fun pitchStartsAtOneRisesAndClamps() {
        assertEquals(1f, StreakLogic.pitch(0), 0f)
        assertEquals(1f, StreakLogic.pitch(1), 0f)
        assertTrue(StreakLogic.pitch(2) > 1f)
        var prev = 0f
        for (s in 0..40) {
            val p = StreakLogic.pitch(s)
            assertTrue(p >= prev)
            assertTrue(p in StreakLogic.PITCH_MIN..StreakLogic.PITCH_MAX)
            prev = p
        }
        assertEquals(StreakLogic.PITCH_MAX, StreakLogic.pitch(1000), 0f)
    }

    @Test
    fun comboChipFromStreakTwo() {
        assertFalse(StreakLogic.comboVisible(0))
        assertFalse(StreakLogic.comboVisible(1))
        assertTrue(StreakLogic.comboVisible(2))
        assertTrue(StreakLogic.comboVisible(9))
    }

    // ---- region diff ----

    @Test
    fun newlyDoneIsTheDiff() {
        assertEquals(listOf(2), RegionClear.newlyDone(listOf(true, false, false, false), listOf(true, false, true, false)))
        assertEquals(listOf(1, 3), RegionClear.newlyDone(listOf(false, false, true, false), listOf(false, true, true, true)))
    }

    @Test
    fun nothingNewWhenUnchangedOrLost() {
        assertEquals(emptyList<Int>(), RegionClear.newlyDone(listOf(true, false), listOf(true, false)))
        // A region that stopped being done is not "newly done".
        assertEquals(emptyList<Int>(), RegionClear.newlyDone(listOf(true, true), listOf(true, false)))
        assertEquals(emptyList<Int>(), RegionClear.newlyDone(emptyList(), emptyList()))
    }

    @Test
    fun missingBeforeEntriesCountAsNotDone() {
        assertEquals(listOf(0, 1), RegionClear.newlyDone(emptyList(), listOf(true, true, false)))
        assertEquals(listOf(2), RegionClear.newlyDone(listOf(true), listOf(true, false, true)))
    }
}
