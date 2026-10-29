package com.threetech.catpuzzle.ui.screens.tutorial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialHelpersTest {

    @Test
    fun accentSplitMarksOddPartsAsAccent() {
        assertEquals(
            listOf(
                AccentText.Segment("Place a ", false),
                AccentText.Segment("cat", true),
                AccentText.Segment(" now.", false),
            ),
            AccentText.split("Place a **cat** now."),
        )
    }

    @Test
    fun accentSplitHandlesEdgesAndUnbalancedMarkers() {
        assertEquals(listOf(AccentText.Segment("plain", false)), AccentText.split("plain"))
        assertEquals(listOf(AccentText.Segment("all", true)), AccentText.split("**all**"))
        assertEquals(
            listOf(AccentText.Segment("a ", false), AccentText.Segment("dangling", false)),
            AccentText.split("a **dangling"),
        )
        assertTrue(AccentText.split("").isEmpty())
    }

    @Test
    fun tapPressesOnceDoubleTapTwice() {
        fun presses(kind: PointerKind): Int {
            var n = 0
            var down = false
            for (i in 0..1000) {
                val pressed = PointerMotion.pressScale(kind, i / 1000f) < 0.9f
                if (pressed && !down) n++
                down = pressed
            }
            return n
        }
        assertEquals(1, presses(PointerKind.TAP))
        assertEquals(2, presses(PointerKind.DOUBLE_TAP))
    }

    @Test
    fun pressScaleStaysInRange() {
        for (kind in PointerKind.entries) for (i in 0..100) {
            val s = PointerMotion.pressScale(kind, i / 100f)
            assertTrue("$kind $s", s in 0.7f..1f)
        }
    }

    @Test
    fun swipeTravelHoldsAtEndsAndIsMonotonic() {
        assertEquals(0f, PointerMotion.travel(PointerKind.SWIPE, 0f), 0f)
        assertEquals(1f, PointerMotion.travel(PointerKind.SWIPE, 1f), 0f)
        var prev = -1f
        for (i in 0..100) {
            val v = PointerMotion.travel(PointerKind.SWIPE, i / 100f)
            assertTrue(v >= prev)
            prev = v
        }
        assertEquals(0f, PointerMotion.travel(PointerKind.TAP, 0.5f), 0f)
    }
}
