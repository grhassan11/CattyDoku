package com.tm.meow.sudoku.puzzle.ui.screens.tutorial

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialSpotlightLogicTest {

    @Test
    fun cardSitsAGapAboveTheBoard() {
        assertEquals(240f, SpotlightLogic.cardTop(100f, 400f, 120f, 40f), 0.001f)
    }

    @Test
    fun tallCardIsClampedToTheAreaTop() {
        assertEquals(100f, SpotlightLogic.cardTop(100f, 200f, 300f, 40f), 0.001f)
    }

    @Test
    fun unmeasuredBoardParksCardAtTop() {
        assertEquals(80f, SpotlightLogic.cardTop(80f, null, 150f, 10f), 0.001f)
    }

    @Test
    fun bottomBlockSitsAGapUnderTheBoard() {
        assertEquals(640f, SpotlightLogic.bottomTop(100f, 900f, 600f, 100f, 40f), 0.001f)
    }

    @Test
    fun bottomBlockIsPulledUpToStayInsideTheArea() {
        assertEquals(800f, SpotlightLogic.bottomTop(100f, 900f, 780f, 100f, 40f), 0.001f)
    }

    @Test
    fun unmeasuredBoardParksBottomBlockAtTheBottom() {
        assertEquals(800f, SpotlightLogic.bottomTop(100f, 900f, null, 100f, 40f), 0.001f)
    }

    @Test
    fun cellRectPlacesCellOnThePitchGrid() {
        // pad 10, cell 20, gap 4 -> pitch 24. Cell (1,2): left 100+10+48, top 200+10+24.
        assertEquals(Rect(158f, 234f, 178f, 254f), SpotlightLogic.cellRect(100f, 200f, 10f, 20f, 4f, 1, 2))
    }

    @Test
    fun gatedCellsAreEveryCellNotInteractive() {
        assertEquals((0 until 16).toList(), SpotlightLogic.gatedCells(4, emptySet()))
        assertEquals((0 until 16).filter { it != 2 }, SpotlightLogic.gatedCells(4, setOf(2)))
        assertTrue(SpotlightLogic.gatedCells(4, (0 until 16).toSet()).isEmpty())
    }

    @Test
    fun brightCellsAreSpotlightPlusMarkedOnDimStepsOnly() {
        assertEquals(setOf(1, 2, 5), SpotlightLogic.brightCells(false, setOf(1), setOf(2, 5)))
        assertTrue(SpotlightLogic.brightCells(true, setOf(1), setOf(2, 5)).isEmpty())
    }

    @Test
    fun alongWalksThePolylineAndHoldsAtTheEnds() {
        val pts = listOf(Offset(0f, 0f), Offset(10f, 0f), Offset(10f, 20f))
        assertEquals(Offset(0f, 0f), SpotlightLogic.along(pts, 0f))
        assertEquals(Offset(5f, 0f), SpotlightLogic.along(pts, 0.25f))
        assertEquals(Offset(10f, 0f), SpotlightLogic.along(pts, 0.5f))
        assertEquals(Offset(10f, 10f), SpotlightLogic.along(pts, 0.75f))
        assertEquals(Offset(10f, 20f), SpotlightLogic.along(pts, 1f))
        assertEquals(Offset(3f, 4f), SpotlightLogic.along(listOf(Offset(3f, 4f)), 0.5f))
    }
}
