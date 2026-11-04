package com.tm.meow.sudoku.puzzle.ui.screens.sudoku.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuBoardFxTest {

    // 4x4, 2x2 boxes. Solution rows: 1234 / 3412 / 2143 / 4321.
    private val sol = intArrayOf(1, 2, 3, 4, 3, 4, 1, 2, 2, 1, 4, 3, 4, 3, 2, 1)

    private fun cell(value: Int, given: Boolean = false, wrong: Boolean = false) =
        SudokuCellUi(value, 0, given, wrong, CellHighlight.NONE)

    /** Board where [empty] cells are blank and every other cell holds its solution digit (non-givens unless in [givens]). */
    private fun board(empty: Set<Int>, givens: Set<Int> = emptySet()): List<SudokuCellUi> =
        List(16) { i -> if (i in empty) cell(0) else cell(sol[i], given = i in givens) }

    @Test
    fun noChangeGivesNoEvent() {
        val b = board(setOf(0, 15))
        assertNull(SudokuBoardFx.detect(b, b, 4, 2, 2, 1))
    }

    @Test
    fun correctDigitPopsWithoutFlashWhenNoUnitCompletes() {
        // Cells 1 and 4 stay empty: row 0, column 0 and box 0 all remain incomplete.
        val prev = board(setOf(0, 1, 4, 15))
        val next = prev.toMutableList().also { it[0] = cell(1) }
        val e = SudokuBoardFx.detect(prev, next, 4, 2, 2, 7)!!
        assertEquals(7, e.id)
        assertEquals(0, e.popCell)
        assertEquals(-1, e.shakeCell)
        assertTrue(e.flashCells.isEmpty())
    }

    @Test
    fun wrongDigitShakes() {
        val prev = board(setOf(0, 15))
        val next = prev.toMutableList().also { it[0] = cell(3, wrong = true) }
        val e = SudokuBoardFx.detect(prev, next, 4, 2, 2, 2)!!
        assertEquals(0, e.shakeCell)
        assertEquals(-1, e.popCell)
        assertTrue(e.flashCells.isEmpty())
    }

    @Test
    fun lastCellOfRowColumnAndBoxFlashesTheWholeUnits() {
        // Cell 0 is the only empty cell: row 0, column 0 and box 0 all complete.
        val prev = board(setOf(0))
        val next = prev.toMutableList().also { it[0] = cell(1) }
        val e = SudokuBoardFx.detect(prev, next, 4, 2, 2, 1)!!
        val expected = (setOf(0, 1, 2, 3) + setOf(0, 4, 8, 12) + setOf(0, 1, 4, 5))
        assertEquals(expected, e.flashCells)
    }

    @Test
    fun alreadyCompleteUnitsDoNotFlashAgain() {
        // Cells 0 and 15 empty: row 0 + col 0 + box 0 incomplete before, but row 3 / col 3 / box 3 contain 15 only.
        val prev = board(setOf(0, 15))
        val next = prev.toMutableList().also { it[0] = cell(1) }
        val e = SudokuBoardFx.detect(prev, next, 4, 2, 2, 1)!!
        assertEquals(setOf(0, 1, 2, 3, 0, 4, 8, 12, 0, 1, 4, 5), e.flashCells)
        // Row 3 / col 3 / box 3 still hold the empty cell 15 and must not flash.
        assertTrue(15 !in e.flashCells)
    }

    @Test
    fun unitWithAWrongDigitIsNotComplete() {
        // Row 0 holds a wrong digit at cell 1, so placing cell 0 correctly completes neither row 0 nor box 0.
        val prev = board(setOf(0)).toMutableList().also { it[1] = cell(4, wrong = true) }
        val next = prev.toMutableList().also { it[0] = cell(1) }
        val e = SudokuBoardFx.detect(prev, next, 4, 2, 2, 1)!!
        assertTrue(0 in e.flashCells && 4 in e.flashCells) // column 0 completes
        assertTrue(3 !in e.flashCells && 5 !in e.flashCells) // row 0 and box 0 do not
    }

    @Test
    fun bulkChangeYieldsNoEvent() {
        val prev = board(setOf(0, 1, 15))
        val next = board(emptySet())
        assertNull(SudokuBoardFx.detect(prev, next, 4, 2, 2, 1))
    }

    @Test
    fun removalAndGivenChangesYieldNoEvent() {
        val prev = board(emptySet())
        val erased = prev.toMutableList().also { it[0] = cell(0) }
        assertNull(SudokuBoardFx.detect(prev, erased, 4, 2, 2, 1))
        val givenNow = prev.toMutableList().also { it[0] = cell(5, given = true) }
        assertNull(SudokuBoardFx.detect(prev, givenNow, 4, 2, 2, 1))
    }

    @Test
    fun sizeMismatchYieldsNoEvent() {
        assertNull(SudokuBoardFx.detect(board(emptySet()), board(emptySet()).take(15), 4, 2, 2, 1))
    }

    @Test
    fun sixBySixBoxIsTwoRowsByThreeCols() {
        // cell 10 = row 1, col 4 -> box rows 0..1, cols 3..5 => 3,4,5,9,10,11.
        val box = SudokuBoardFx.unitsOf(10, 6, 2, 3)[2].toSet()
        assertEquals(setOf(3, 4, 5, 9, 10, 11), box)
        val units = SudokuBoardFx.unitsOf(10, 6, 2, 3)
        assertEquals(setOf(6, 7, 8, 9, 10, 11), units[0].toSet())
        assertEquals(setOf(4, 10, 16, 22, 28, 34), units[1].toSet())
        assertNotNull(units)
    }

    @Test
    fun notesTextListsSetBitsAscending() {
        assertEquals("", SudokuNotesText.list(0))
        assertEquals("1, 4, 9", SudokuNotesText.list((1 shl 0) or (1 shl 3) or (1 shl 8)))
        assertEquals("2", SudokuNotesText.list(1 shl 1))
    }
}
