package com.tm.meow.sudoku.puzzle.ui.screens.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameFeelExtrasTest {

    // ---- combo popup style ----

    @Test
    fun comboScaleIsOneUpToStreakTwoThenRisesToCap() {
        assertEquals(1.0f, ComboStyle.scale(0), 1e-6f)
        assertEquals(1.0f, ComboStyle.scale(2), 1e-6f)
        assertTrue(ComboStyle.scale(5) > ComboStyle.scale(3))
        assertEquals(1.4f, ComboStyle.scale(12), 1e-6f)
        assertEquals(1.4f, ComboStyle.scale(99), 1e-6f)
    }

    @Test
    fun comboTiersFollowThresholds() {
        assertEquals(0, ComboStyle.tier(0))
        assertEquals(0, ComboStyle.tier(2))
        assertEquals(1, ComboStyle.tier(3))
        assertEquals(1, ComboStyle.tier(4))
        assertEquals(2, ComboStyle.tier(5))
        assertEquals(2, ComboStyle.tier(7))
        assertEquals(3, ComboStyle.tier(8))
        assertEquals(3, ComboStyle.tier(40))
    }

    @Test
    fun comboColoursDifferPerTier() {
        val colours = listOf(0, 3, 5, 8).map { ComboStyle.colorArgb(it) }
        assertEquals(4, colours.toSet().size)
    }

    // ---- touching partners ----

    private fun cats(n: Int, vararg idx: Int): (Int) -> Boolean = { it in idx.toSet() && it in 0 until n * n }

    @Test
    fun noNeighbourCatsMeansNoPartners() {
        assertTrue(WrongMoveLogic.touchingCats(5, 12, cats(5)).isEmpty())
        // a cat two cells away does not touch
        assertTrue(WrongMoveLogic.touchingCats(5, 12, cats(5, 2, 14)).isEmpty())
    }

    @Test
    fun allEightNeighboursCount() {
        // centre of a 5x5 board = 12; neighbours 6,7,8,11,13,16,17,18
        val p = WrongMoveLogic.touchingCats(5, 12, cats(5, 6, 7, 8, 11, 13, 16, 17, 18, 0, 24))
        assertEquals(setOf(6, 7, 8, 11, 13, 16, 17, 18), p)
    }

    @Test
    fun ownCellIsNeverAPartner() {
        assertTrue(WrongMoveLogic.touchingCats(5, 12, cats(5, 12)).isEmpty())
    }

    @Test
    fun cornersDoNotWrapAround() {
        // idx 0 (top-left) neighbours: 1, 5, 6. Index 4 (top-right) and 20 must not count.
        assertEquals(setOf(1, 5, 6), WrongMoveLogic.touchingCats(5, 0, cats(5, 1, 5, 6, 4, 20)))
        // idx 4 (top-right): left neighbour is 3, NOT 5 (row wrap)
        assertEquals(setOf(3), WrongMoveLogic.touchingCats(5, 4, cats(5, 3, 5)))
    }

    @Test
    fun badIndexIsEmpty() {
        assertTrue(WrongMoveLogic.touchingCats(5, -1, cats(5)).isEmpty())
        assertTrue(WrongMoveLogic.touchingCats(5, 25, cats(5)).isEmpty())
        assertTrue(WrongMoveLogic.touchingCats(0, 0, cats(5)).isEmpty())
    }

    // ---- progress + star flight ----

    @Test
    fun starFlightHitsEndpointsAndEases() {
        val out = FloatArray(2)
        StarFlight.bezier(0f, 10f, 20f, 50f, -30f, 110f, 220f, out)
        assertEquals(10f, out[0], 1e-4f)
        assertEquals(20f, out[1], 1e-4f)
        StarFlight.bezier(1f, 10f, 20f, 50f, -30f, 110f, 220f, out)
        assertEquals(110f, out[0], 1e-4f)
        assertEquals(220f, out[1], 1e-4f)
        assertEquals(0f, StarFlight.ease(0f), 1e-6f)
        assertEquals(1f, StarFlight.ease(1f), 1e-6f)
        assertEquals(0.5f, StarFlight.ease(0.5f), 1e-6f)
        assertEquals(1f, StarFlight.ease(2f), 1e-6f)
    }
}
