package com.threetech.catpuzzle.domain.engine

import com.threetech.catpuzzle.domain.model.CellMark
import com.threetech.catpuzzle.domain.model.BoardState
import com.threetech.catpuzzle.domain.model.Pos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardValidatorTest {
    private val n = 4
    private val regions = Fixtures.UNIQUE_4

    private fun analyse(vararg cats: Pos) =
        BoardValidator.analyse(n, regions, Fixtures.board(n, *cats).marks)

    @Test
    fun emptyBoardNoConflictsNotSolved() {
        val a = BoardValidator.analyse(n, regions, BoardState.empty(n).marks)
        assertTrue(a.conflictCells.isEmpty())
        assertFalse(a.solved)
        assertFalse(a.rowDone.any { it })
    }

    @Test
    fun solvedState() {
        val a = analyse(Pos(0, 1), Pos(1, 3), Pos(2, 0), Pos(3, 2))
        assertTrue(a.solved)
        assertTrue(a.rowDone.all { it })
        assertTrue(a.colDone.all { it })
        assertTrue(a.regionDone.all { it })
    }

    @Test
    fun diagonalTouchConflicts() {
        val a = analyse(Pos(0, 0), Pos(1, 1))
        assertEquals(setOf(0, 5), a.conflictCells)
    }

    @Test
    fun diagonalTouchAntiDiagonalConflicts() {
        val a = analyse(Pos(0, 1), Pos(1, 2))
        assertEquals(setOf(1, 6), a.conflictCells)
    }

    @Test
    fun rowDuplicate() {
        val a = analyse(Pos(0, 0), Pos(0, 3))
        assertEquals(setOf(0, 3), a.conflictCells)
        assertFalse(a.rowDone[0])
    }

    @Test
    fun columnDuplicate() {
        val a = analyse(Pos(0, 0), Pos(2, 0))
        assertEquals(setOf(0, 8), a.conflictCells)
    }

    @Test
    fun regionDuplicateOnly() {
        // (0,0) and (2,1) are both region 2, not touching, different row/col
        val a = analyse(Pos(0, 0), Pos(2, 1))
        assertEquals(setOf(0, 9), a.conflictCells)
        assertFalse(a.regionDone[2])
    }

    @Test
    fun partialCorrectNotSolvedButDone() {
        val a = analyse(Pos(0, 1), Pos(1, 3), Pos(2, 0))
        assertFalse(a.solved)
        assertTrue(a.conflictCells.isEmpty())
        assertEquals(listOf(true, true, true, false), a.rowDone)
        assertEquals(listOf(true, true, true, false), a.regionDone)
    }

    @Test
    fun fourCatsWithConflictNotSolved() {
        val a = analyse(Pos(0, 0), Pos(1, 3), Pos(2, 1), Pos(3, 2))
        assertFalse(a.solved)
        assertTrue(a.hasConflicts)
    }

    @Test
    fun xMarksIgnored() {
        var b = Fixtures.board(n, Pos(0, 1))
        b = b.withMark(0, 2, CellMark.X).withMark(1, 1, CellMark.X)
        val a = BoardValidator.analyse(n, regions, b.marks)
        assertTrue(a.conflictCells.isEmpty())
    }
}
