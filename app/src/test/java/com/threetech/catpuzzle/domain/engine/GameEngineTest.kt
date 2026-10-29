package com.threetech.catpuzzle.domain.engine

import com.threetech.catpuzzle.domain.model.BoardState
import com.threetech.catpuzzle.domain.model.CellMark
import com.threetech.catpuzzle.domain.model.DragMode
import com.threetech.catpuzzle.domain.model.GameEvent
import com.threetech.catpuzzle.domain.model.GameState
import com.threetech.catpuzzle.domain.model.GameStatus
import com.threetech.catpuzzle.domain.model.Pos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** UNIQUE_4 solution cells: (0,1) (1,3) (2,0) (3,2). Any other cat is wrong. */
class GameEngineTest {
    private val engine = GameEngine()
    private val p = GameFixtures.unique4()
    private fun fresh() = GameState.initial(4)

    @Test
    fun tapTogglesX() {
        val a = engine.applyTap(p, fresh(), Pos(0, 0))
        assertEquals(CellMark.X, a.state.board[0, 0])
        val b = engine.applyTap(p, a.state, Pos(0, 0))
        assertEquals(CellMark.EMPTY, b.state.board[0, 0])
        assertTrue(b.events.isEmpty())
    }

    @Test
    fun xNeverCostsHeartEvenOnSolutionCell() {
        val r = engine.applyTap(p, fresh(), Pos(0, 1))
        assertEquals(3, r.state.hearts)
        assertTrue(r.events.isEmpty())
    }

    @Test
    fun tapOnCatIsNoOp() {
        val s = engine.applyDoubleTap(p, fresh(), Pos(0, 1)).state
        val r = engine.applyTap(p, s, Pos(0, 1))
        assertSame(s, r.state)
    }

    @Test
    fun correctCatNeverLosesHeartAndScoresOnce() {
        val r = engine.applyDoubleTap(p, fresh(), Pos(0, 1))
        assertEquals(CellMark.CAT, r.state.board[0, 1])
        assertEquals(3, r.state.hearts)
        assertEquals(1, r.state.correctPlacements)
        assertTrue(r.events.isEmpty())
    }

    @Test
    fun wrongCatLosesOneHeart() {
        val r = engine.applyDoubleTap(p, fresh(), Pos(0, 0))
        assertEquals(2, r.state.hearts)
        assertEquals(CellMark.CAT, r.state.board[0, 0])
        assertEquals(0, r.state.correctPlacements)
        assertEquals(listOf<GameEvent>(GameEvent.WrongPlacement(Pos(0, 0), 2)), r.events)
    }

    @Test
    fun catOverXReplacesX() {
        var s = engine.applyTap(p, fresh(), Pos(0, 1)).state
        s = engine.applyDoubleTap(p, s, Pos(0, 1)).state
        assertEquals(CellMark.CAT, s.board[0, 1])
        assertEquals(3, s.hearts)
    }

    @Test
    fun threeWrongCatsLose() {
        var s = fresh()
        val wrong = listOf(Pos(0, 0), Pos(0, 2), Pos(0, 3))
        var last = engine.applyDoubleTap(p, s, wrong[0])
        s = last.state
        assertEquals(GameStatus.PLAYING, s.status)
        last = engine.applyDoubleTap(p, s, wrong[1])
        s = last.state
        assertEquals(1, s.hearts)
        assertEquals(GameStatus.PLAYING, s.status)
        last = engine.applyDoubleTap(p, s, wrong[2])
        s = last.state
        assertEquals(0, s.hearts)
        assertEquals(GameStatus.LOST, s.status)
        assertTrue(GameEvent.WrongPlacement(wrong[2], 0) in last.events)
        assertEquals(GameEvent.Lost, last.events.last())
    }

    @Test
    fun opsAfterLostAreNoOps() {
        var s = fresh().copy(hearts = 1)
        s = engine.applyDoubleTap(p, s, Pos(0, 0)).state
        assertEquals(GameStatus.LOST, s.status)
        assertSame(s, engine.applyTap(p, s, Pos(1, 0)).state)
        assertSame(s, engine.applyDoubleTap(p, s, Pos(1, 3)).state)
        assertSame(s, engine.undo(s).state)
    }

    @Test
    fun conflictEventWhenCatsClash() {
        var s = engine.applyDoubleTap(p, fresh(), Pos(0, 0)).state
        val r = engine.applyDoubleTap(p, s, Pos(0, 2))
        s = r.state
        assertTrue(r.events.any { it is GameEvent.Conflict })
    }

    @Test
    fun solvingEmitsSolved() {
        var s = fresh()
        var events: List<GameEvent> = emptyList()
        for (r in 0 until 4) {
            val step = engine.applyDoubleTap(p, s, Pos(r, p.solution[r]))
            s = step.state
            events = step.events
        }
        assertEquals(GameStatus.SOLVED, s.status)
        assertEquals(listOf<GameEvent>(GameEvent.Solved), events)
        assertEquals(3, s.hearts)
        assertEquals(4, s.correctPlacements)
    }

    @Test
    fun doubleTapOnCatRemovesIt() {
        var s = engine.applyDoubleTap(p, fresh(), Pos(0, 1)).state
        s = engine.applyDoubleTap(p, s, Pos(0, 1)).state
        assertEquals(CellMark.EMPTY, s.board[0, 1])
        assertEquals(3, s.hearts)
    }

    @Test
    fun removeCatOnlyOnCat() {
        val s = fresh()
        assertSame(s, engine.removeCat(p, s, Pos(0, 1)).state)
    }

    @Test
    fun outOfBoundsIsNoOp() {
        val s = fresh()
        assertSame(s, engine.applyTap(p, s, Pos(9, 9)).state)
        assertSame(s, engine.applyTap(p, s, Pos(-1, 0)).state)
    }

    // ---- undo ----

    @Test
    fun undoRestoresBoardButDoesNotRefundHeart() {
        var s = engine.applyDoubleTap(p, fresh(), Pos(0, 0)).state
        assertEquals(2, s.hearts)
        s = engine.undo(s).state
        assertEquals(CellMark.EMPTY, s.board[0, 0])
        assertEquals(2, s.hearts)
        assertTrue(s.undoStack.isEmpty())
    }

    @Test
    fun undoCatOverXRestoresX() {
        var s = engine.applyTap(p, fresh(), Pos(0, 0)).state
        s = engine.applyDoubleTap(p, s, Pos(0, 0)).state
        s = engine.undo(s).state
        assertEquals(CellMark.X, s.board[0, 0])
        s = engine.undo(s).state
        assertEquals(CellMark.EMPTY, s.board[0, 0])
    }

    @Test
    fun undoWithEmptyStackIsNoOp() {
        val s = fresh()
        assertSame(s, engine.undo(s).state)
    }

    @Test
    fun undoThenReplaceCorrectCatDoesNotDoubleScore() {
        var s = engine.applyDoubleTap(p, fresh(), Pos(0, 1)).state
        s = engine.undo(s).state
        s = engine.applyDoubleTap(p, s, Pos(0, 1)).state
        assertEquals(1, s.correctPlacements)
    }

    @Test
    fun undoRevertsWholeDragAsOneStep() {
        val s = engine.applyDrag(p, fresh(), listOf(Pos(0, 0), Pos(0, 2), Pos(0, 3))).state
        assertEquals(1, s.undoStack.size)
        val u = engine.undo(s).state
        assertEquals(BoardState.empty(4), u.board)
    }

    // ---- drag ----

    @Test
    fun dragMarkFromEmptyStartSkipsExistingXAndCats() {
        var s = engine.applyDoubleTap(p, fresh(), Pos(1, 3)).state // correct cat
        s = engine.applyTap(p, s, Pos(0, 2)).state // existing X
        val path = listOf(Pos(0, 0), Pos(0, 2), Pos(1, 3), Pos(0, 3))
        assertEquals(DragMode.MARK, engine.dragModeFor(p, s, path.first()))
        val r = engine.applyDrag(p, s, path).state
        assertEquals(CellMark.X, r.board[0, 0])
        assertEquals(CellMark.X, r.board[0, 2]) // stays X, not toggled off
        assertEquals(CellMark.CAT, r.board[1, 3])
        assertEquals(CellMark.X, r.board[0, 3])
        assertEquals(3, r.hearts)
    }

    @Test
    fun dragEraseFromXStartClearsOnlyXs() {
        var s = engine.applyTap(p, fresh(), Pos(0, 0)).state
        s = engine.applyTap(p, s, Pos(0, 2)).state
        val path = listOf(Pos(0, 0), Pos(0, 1), Pos(0, 2))
        assertEquals(DragMode.ERASE, engine.dragModeFor(p, s, path.first()))
        val r = engine.applyDrag(p, s, path).state
        assertEquals(CellMark.EMPTY, r.board[0, 0])
        assertEquals(CellMark.EMPTY, r.board[0, 1]) // empty stays empty (not marked)
        assertEquals(CellMark.EMPTY, r.board[0, 2])
    }

    @Test
    fun dragStartingOnCatDoesNothing() {
        val s = engine.applyDoubleTap(p, fresh(), Pos(0, 1)).state
        assertNull(engine.dragModeFor(p, s, Pos(0, 1)))
        assertSame(s, engine.applyDrag(p, s, listOf(Pos(0, 1), Pos(0, 0))).state)
    }

    @Test
    fun dragNeverCostsHearts() {
        val path = (0 until 4).map { Pos(it, 0) }
        val s = engine.applyDrag(p, fresh(), path).state
        assertEquals(3, s.hearts)
    }

    // ---- boosted placement ----

    // ---- starter cat ----

    private fun withStarter(): GameState = engine.placeStarter(p, fresh(), Pos(0, 1)).state

    @Test
    fun starterCatIsLockedFreeUnscoredAndNotUndoable() {
        val s = withStarter()
        assertEquals(CellMark.CAT, s.board[0, 1])
        assertEquals(3, s.hearts)
        assertEquals(0, s.correctPlacements)
        assertTrue(1 in s.lockedCells)
        assertTrue(1 in s.rewardedCells)
        assertTrue(s.undoStack.isEmpty())
        assertSame(s, engine.undo(s).state)
        assertSame(s, engine.applyTap(p, s, Pos(0, 1)).state)
        assertSame(s, engine.applyDoubleTap(p, s, Pos(0, 1)).state)
        assertSame(s, engine.removeCat(p, s, Pos(0, 1)).state)
        assertNull(engine.dragModeFor(p, s, Pos(0, 1)))
    }

    @Test
    fun undoAfterOtherMovesLeavesStarterCat() {
        var s = withStarter()
        s = engine.applyTap(p, s, Pos(3, 3)).state
        s = engine.undo(s).state
        assertEquals(CellMark.CAT, s.board[0, 1])
        assertEquals(CellMark.EMPTY, s.board[3, 3])
    }

    @Test
    fun winWithStarterNeedsAllNCatsAndScoresOnlyThePlacedOnes() {
        var s = withStarter()
        var events: List<GameEvent> = emptyList()
        for (r in 1 until 4) {
            val step = engine.applyDoubleTap(p, s, Pos(r, p.solution[r]))
            s = step.state
            events = step.events
        }
        assertEquals(GameStatus.SOLVED, s.status)
        assertEquals(listOf<GameEvent>(GameEvent.Solved), events)
        assertEquals(3, s.correctPlacements) // starter excluded from placements * 100
        assertEquals(3, s.hearts)
    }

    @Test
    fun notSolvedWithoutAllCats() {
        var s = withStarter()
        s = engine.applyDoubleTap(p, s, Pos(1, 3)).state
        assertEquals(GameStatus.PLAYING, s.status)
    }

    @Test
    fun boostedCatIsLockedFreeAndNotUndoable() {
        var s = engine.applyTap(p, fresh(), Pos(0, 1)).state // X on solution cell
        val r = engine.placeBoosted(p, s, Pos(0, 1))
        s = r.state
        assertEquals(CellMark.CAT, s.board[0, 1])
        assertEquals(3, s.hearts)
        assertEquals(0, s.correctPlacements)
        assertTrue(0 * 4 + 1 in s.lockedCells)
        // locked: tap / double tap / undo leave it
        assertSame(s, engine.applyTap(p, s, Pos(0, 1)).state)
        assertSame(s, engine.applyDoubleTap(p, s, Pos(0, 1)).state)
        val u = engine.undo(s).state // pops the earlier X entry, but must not touch the locked cell
        assertEquals(CellMark.CAT, u.board[0, 1])
    }
}
