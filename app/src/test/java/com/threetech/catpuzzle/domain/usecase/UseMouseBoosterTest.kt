package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.engine.GameFixtures
import com.threetech.catpuzzle.domain.engine.MouseBoosterEngine
import com.threetech.catpuzzle.domain.model.BoardState
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.domain.model.CellMark
import com.threetech.catpuzzle.domain.model.GameState
import com.threetech.catpuzzle.domain.model.GameStatus
import com.threetech.catpuzzle.domain.model.Pos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UseMouseBoosterTest {
    private val puzzle = GameFixtures.unique4()
    private val use = UseMouseBooster(MouseBoosterEngine()) { 99L }

    private fun state(charges: Int, board: BoardState = BoardState.empty(4)) =
        GameState(board = board, charges = mapOf(Booster.MOUSE to charges))

    @Test
    fun marksThreeSafeCellsAndSpendsOneCharge() {
        val r = use(puzzle, state(2)) as MouseBoosterResult.Marked
        assertEquals(3, r.cells.size)
        assertEquals(1, r.state.chargesOf(Booster.MOUSE))
        r.cells.forEach {
            assertFalse(GameFixtures.isSolution(puzzle, it))
            assertEquals(CellMark.X, r.state.board[it.row, it.col])
        }
        assertEquals(3, r.state.board.marks.count { it == CellMark.X })
    }

    @Test
    fun scoreNeutralAndNotUndoable() {
        val before = state(1)
        val r = use(puzzle, before) as MouseBoosterResult.Marked
        assertEquals(before.hearts, r.state.hearts)
        assertEquals(before.hintsUsed, r.state.hintsUsed)
        assertEquals(before.correctPlacements, r.state.correctPlacements)
        assertEquals(before.undoStack, r.state.undoStack)
        assertEquals(before.rewardedCells, r.state.rewardedCells)
    }

    @Test
    fun noChargeChangesNothing() {
        assertEquals(MouseBoosterResult.NoCharge, use(puzzle, state(0)))
    }

    @Test
    fun finishedGameIsUnavailable() {
        assertEquals(MouseBoosterResult.Unavailable, use(puzzle, state(3).copy(status = GameStatus.LOST)))
    }

    @Test
    fun marksAsManyAsExistAndNoneLeftIsUnavailable() {
        var board = BoardState.empty(4)
        var keep = 2 // leave exactly 2 eligible cells
        for (r in 0 until 4) for (c in 0 until 4) {
            if (GameFixtures.isSolution(puzzle, Pos(r, c))) continue
            if (keep > 0) {
                keep--
                continue
            }
            board = board.withMark(r, c, CellMark.X)
        }
        val two = use(puzzle, state(1, board)) as MouseBoosterResult.Marked
        assertEquals(2, two.cells.size)

        // Nothing eligible any more: charge is not consumed.
        assertEquals(MouseBoosterResult.Unavailable, use(puzzle, two.state.copy(charges = mapOf(Booster.MOUSE to 4))))
    }

    @Test
    fun sameSeedSameCells() {
        val a = use(puzzle, state(1)) as MouseBoosterResult.Marked
        val b = UseMouseBooster(MouseBoosterEngine()) { 99L }(puzzle, state(1)) as MouseBoosterResult.Marked
        assertEquals(a.cells, b.cells)
        assertTrue(a.cells.isNotEmpty())
    }
}
