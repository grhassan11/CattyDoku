package com.threetech.catpuzzle.ui.screens.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutcomeLogicTest {

    @Test
    fun headlineByLevel() {
        assertEquals(WinHeadline.FIRST_WIN, OutcomeLogic.headline(1))
        assertEquals(WinHeadline.MILESTONE_5, OutcomeLogic.headline(5))
        assertEquals(WinHeadline.MILESTONE_10, OutcomeLogic.headline(10))
        assertEquals(WinHeadline.MILESTONE_25, OutcomeLogic.headline(25))
        for (level in listOf(2, 3, 4, 6, 9, 11, 24, 26, 100)) {
            assertEquals(WinHeadline.STANDARD, OutcomeLogic.headline(level))
        }
    }

    @Test
    fun dailyHeadlineOverridesLevelMilestones() {
        for (level in listOf(0, 1, 5, 10, 25, 7)) {
            assertEquals(WinHeadline.DAILY, OutcomeLogic.headline(level, daily = true))
        }
        assertEquals(WinMessage.PERFECT, OutcomeLogic.message(1, 0, 0, daily = true))
    }

    @Test
    fun messageRules() {
        assertEquals(WinMessage.FIRST_WIN, OutcomeLogic.message(1, heartsLost = 2, hintsUsed = 3))
        assertEquals(WinMessage.PERFECT, OutcomeLogic.message(7, 0, 0))
        assertEquals(WinMessage.GOOD, OutcomeLogic.message(7, 1, 0))
        assertEquals(WinMessage.GOOD, OutcomeLogic.message(7, 0, 2))
        assertEquals(WinMessage.TOUGH, OutcomeLogic.message(7, 2, 0))
        assertEquals(WinMessage.TOUGH, OutcomeLogic.message(7, 3, 1))
    }

    @Test
    fun countUpEndpointsAndMonotonic() {
        assertEquals(0, OutcomeLogic.countUpValue(900, 0f))
        assertEquals(900, OutcomeLogic.countUpValue(900, 1f))
        assertEquals(450, OutcomeLogic.countUpValue(900, 0.5f))
        var prev = -1
        for (i in 0..100) {
            val v = OutcomeLogic.countUpValue(1234, i / 100f)
            assertTrue(v >= prev)
            prev = v
        }
        assertEquals(1234, prev)
    }

    @Test
    fun countUpClampsInputs() {
        assertEquals(0, OutcomeLogic.countUpValue(900, -1f))
        assertEquals(900, OutcomeLogic.countUpValue(900, 7f))
        assertEquals(0, OutcomeLogic.countUpValue(0, 0.5f))
        assertEquals(0, OutcomeLogic.countUpValue(-5, 1f))
    }

    @Test
    fun countUpDurationBounds() {
        assertEquals(OutcomeLogic.COUNT_UP_MIN_MS, OutcomeLogic.countUpDurationMs(0))
        assertEquals(OutcomeLogic.COUNT_UP_MIN_MS, OutcomeLogic.countUpDurationMs(-10))
        assertEquals(OutcomeLogic.COUNT_UP_MAX_MS, OutcomeLogic.countUpDurationMs(1_000_000))
        val mid = OutcomeLogic.countUpDurationMs(900)
        assertTrue(mid in OutcomeLogic.COUNT_UP_MIN_MS..OutcomeLogic.COUNT_UP_MAX_MS)
    }

    @Test
    fun formatElapsed() {
        assertEquals("0:00", OutcomeLogic.formatElapsed(0))
        assertEquals("0:09", OutcomeLogic.formatElapsed(9))
        assertEquals("1:05", OutcomeLogic.formatElapsed(65))
        assertEquals("12:00", OutcomeLogic.formatElapsed(720))
        assertEquals("0:00", OutcomeLogic.formatElapsed(-4))
    }
}
