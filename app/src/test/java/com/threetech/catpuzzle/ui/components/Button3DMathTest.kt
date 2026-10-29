package com.threetech.catpuzzle.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Button3DMathTest {
    @Test
    fun offsetRestIsZeroAndPressedLeavesRestLip() {
        assertEquals(0f, Button3DMath.faceOffset(0f, 18f), 0.001f)
        assertEquals(16.5f, Button3DMath.faceOffset(1f, 18f), 0.001f)
    }

    @Test
    fun offsetClampsProgress() {
        assertEquals(16.5f, Button3DMath.faceOffset(1.4f, 18f), 0.001f)
        assertEquals(0f, Button3DMath.faceOffset(-0.2f, 18f), 0.001f)
    }

    @Test
    fun shadowShrinksWhenPressed() {
        assertTrue(Button3DMath.shadowAlpha(1f) < Button3DMath.shadowAlpha(0f))
        assertEquals(0.22f, Button3DMath.shadowAlpha(0f), 0.001f)
    }
}
