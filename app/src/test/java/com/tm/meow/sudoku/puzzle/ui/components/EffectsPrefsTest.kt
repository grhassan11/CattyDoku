package com.tm.meow.sudoku.puzzle.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class EffectsPrefsTest {
    @Test
    fun normalDeviceKeepsCount() {
        assertEquals(36, scaledParticleCount(36, lowRam = false))
    }

    @Test
    fun lowRamHalves() {
        assertEquals(18, scaledParticleCount(36, lowRam = true))
        assertEquals(15, scaledParticleCount(30, lowRam = true))
    }

    @Test
    fun lowRamNeverBelowFloorOrAboveCount() {
        assertEquals(4, scaledParticleCount(6, lowRam = true))
        assertEquals(3, scaledParticleCount(3, lowRam = true))
    }
}
