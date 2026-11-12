package com.tm.meow.sudoku.puzzle.ui.screens.home.worlds

import org.junit.Assert.assertEquals
import org.junit.Test

class MapScaleTest {

    @Test fun normalPhoneIsUnscaled() = assertEquals(1f, mapScale(360f, 300f), 0f)

    @Test fun wideScreenNeverScalesUp() = assertEquals(1f, mapScale(480f, 400f), 0f)

    @Test fun narrowWidthShrinks() = assertEquals(0.8f, mapScale(288f, 300f), 0.001f)

    @Test fun shortBandShrinks() = assertEquals(0.75f, mapScale(360f, 136.5f), 0.001f)

    @Test fun clampedToMinimum() = assertEquals(0.7f, mapScale(200f, 50f), 0f)

    @Test fun smallerOfTheTwoWins() = assertEquals(0.8f, mapScale(288f, 182f), 0.001f)
}
