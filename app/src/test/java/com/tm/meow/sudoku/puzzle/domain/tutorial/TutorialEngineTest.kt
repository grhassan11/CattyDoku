package com.tm.meow.sudoku.puzzle.domain.tutorial

import com.tm.meow.sudoku.puzzle.domain.model.CellMark
import com.tm.meow.sudoku.puzzle.domain.model.GameState
import com.tm.meow.sudoku.puzzle.domain.model.GameStatus
import com.tm.meow.sudoku.puzzle.domain.model.Pos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialEngineTest {

    private val engine = TutorialEngine()

    private fun TutorialState.mark(r: Int, c: Int): CellMark = game.board[r, c]

    // ---- step advancement ----

    @Test
    fun startsAtStepZeroOnAnEmptyBoard() {
        val s = TutorialState()
        assertEquals(0, s.stepIndex)
        assertEquals(10, engine.stepCount)
        assertTrue(s.game.board.marks.all { it == CellMark.EMPTY })
    }

    @Test
    fun doubleTapOnTargetPlacesCatAndAdvances() {
        val tap = engine.apply(TutorialState(), TutorialAction.Tap(Pos(0, 2)))
        val dbl = engine.apply(tap.state, TutorialAction.DoubleTap(Pos(0, 2)))
        assertEquals(TutorialOutcome.ADVANCED, dbl.outcome)
        assertEquals(1, dbl.state.stepIndex)
        assertEquals(CellMark.CAT, dbl.state.mark(0, 2))
        assertEquals(GameState.MAX_HEARTS, dbl.state.game.hearts)
        assertNull(dbl.state.notice)
    }

    @Test
    fun firstTapOfDoubleTapAppliesNothing() {
        val r = engine.apply(TutorialState(), TutorialAction.Tap(Pos(0, 2)))
        assertEquals(TutorialOutcome.NEEDS_SECOND_TAP, r.outcome)
        assertEquals(TutorialNotice.TAP_TWICE, r.state.notice)
        assertEquals(CellMark.EMPTY, r.state.mark(0, 2))
        assertEquals(0, r.state.rejections)
        assertEquals(0, r.state.stepIndex)
    }

    @Test
    fun infoStepAdvancesOnContinueOnly() {
        val s = TutorialPlayer.playUntil(engine, 1)
        assertEquals(1, s.stepIndex)
        val r = engine.apply(s, TutorialAction.Continue)
        assertEquals(TutorialOutcome.ADVANCED, r.outcome)
        assertEquals(2, r.state.stepIndex)
    }

    @Test
    fun tapXStepNeedsEveryTargetThenAdvances() {
        val s = TutorialPlayer.playUntil(engine, 2)
        val targets = engine.step(s).targets
        assertEquals(6, targets.size)
        var cur = s
        for ((i, t) in targets.reversed().withIndex()) { // any order
            val r = engine.apply(cur, TutorialAction.Tap(t))
            cur = r.state
            assertEquals(if (i == targets.lastIndex) TutorialOutcome.ADVANCED else TutorialOutcome.APPLIED, r.outcome)
        }
        assertEquals(3, cur.stepIndex)
        for (t in targets) assertEquals(CellMark.X, cur.mark(t.row, t.col))
    }

    @Test
    fun swipeAcrossAllTargetsAdvances() {
        val s = TutorialPlayer.playUntil(engine, 4)
        val targets = engine.step(s).targets
        val r = engine.apply(s, TutorialAction.Drag(targets))
        assertEquals(TutorialOutcome.ADVANCED, r.outcome)
        for (t in targets) assertEquals(CellMark.X, r.state.mark(t.row, t.col))
        // reversed stroke works too
        val rev = engine.apply(s, TutorialAction.Drag(targets.reversed()))
        assertEquals(TutorialOutcome.ADVANCED, rev.outcome)
    }

    @Test
    fun partialSwipeKeepsStepOpen() {
        val s = TutorialPlayer.playUntil(engine, 4)
        val targets = engine.step(s).targets
        val r = engine.apply(s, TutorialAction.Drag(targets.take(2)))
        assertEquals(TutorialOutcome.APPLIED, r.outcome)
        assertEquals(4, r.state.stepIndex)
        assertEquals(listOf(targets[2]), engine.remainingTargets(r.state))
    }

    @Test
    fun boardBeforeEachStepMatchesTheReferenceScreens() {
        fun board(step: Int): Pair<Set<Pos>, Set<Pos>> {
            val g = TutorialPlayer.playUntil(engine, step).game
            val cats = HashSet<Pos>()
            val xs = HashSet<Pos>()
            for (r in 0 until 4) for (c in 0 until 4) when (g.board[r, c]) {
                CellMark.CAT -> cats.add(Pos(r, c))
                CellMark.X -> xs.add(Pos(r, c))
                CellMark.EMPTY -> Unit
            }
            return cats to xs
        }
        val rowCol = setOf(Pos(0, 0), Pos(0, 1), Pos(0, 3), Pos(1, 2), Pos(2, 2), Pos(3, 2))
        val adj = setOf(Pos(2, 0), Pos(2, 1), Pos(3, 0))
        val rowDone = setOf(Pos(1, 1), Pos(1, 3))
        val none = emptySet<Pos>()
        assertEquals(none to none, board(0))
        assertEquals(setOf(Pos(0, 2)) to none, board(1))
        assertEquals(setOf(Pos(0, 2)) to none, board(2))
        assertEquals(setOf(Pos(0, 2)) to rowCol, board(3))
        assertEquals(setOf(Pos(0, 2), Pos(3, 1)) to rowCol, board(4))
        assertEquals(setOf(Pos(0, 2), Pos(3, 1)) to rowCol + adj, board(5))
        assertEquals(setOf(Pos(0, 2), Pos(3, 1), Pos(1, 0)) to rowCol + adj, board(6))
        assertEquals(setOf(Pos(0, 2), Pos(3, 1), Pos(1, 0)) to rowCol + adj + rowDone, board(7))
        assertEquals(setOf(Pos(0, 2), Pos(3, 1), Pos(1, 0)) to rowCol + adj + rowDone + Pos(3, 3), board(8))
        val (cats, xs) = board(9)
        assertEquals(setOf(Pos(0, 2), Pos(3, 1), Pos(1, 0), Pos(2, 3)), cats)
        assertEquals(12, xs.size)
    }

    @Test
    fun hintPillPlacesTheLastCatForFreeAndFillsCrosses() {
        val s = TutorialPlayer.playUntil(engine, 8)
        assertEquals(listOf(Pos(2, 3)), engine.remainingTargets(s))
        assertEquals(1, s.game.board.marks.count { it == CellMark.EMPTY })
        val r = engine.apply(s, TutorialAction.HintPill)
        assertEquals(TutorialOutcome.ADVANCED, r.outcome)
        assertEquals(9, r.state.stepIndex)
        assertEquals(CellMark.CAT, r.state.mark(2, 3))
        assertEquals(GameStatus.SOLVED, r.state.game.status)
        assertEquals(GameState.MAX_HEARTS, r.state.game.hearts)
        assertEquals(4, r.state.game.board.marks.count { it == CellMark.CAT })
        assertEquals(12, r.state.game.board.marks.count { it == CellMark.X })
        assertTrue(r.state.game.charges.isEmpty())
        assertEquals(r.state.game, engine.fillCrossesWhenSolved(r.state.game))
    }

    @Test
    fun doubleTapOnTheLastCellAlsoFinishesTheStep() {
        val s = TutorialPlayer.playUntil(engine, 8)
        val first = engine.apply(s, TutorialAction.Tap(Pos(2, 3)))
        assertEquals(TutorialOutcome.NEEDS_SECOND_TAP, first.outcome)
        val r = engine.apply(first.state, TutorialAction.DoubleTap(Pos(2, 3)))
        assertEquals(TutorialOutcome.ADVANCED, r.outcome)
        assertEquals(9, r.state.stepIndex)
        assertEquals(CellMark.CAT, r.state.mark(2, 3))
        assertTrue(r.state.game.board.marks.none { it == CellMark.EMPTY })
    }

    @Test
    fun noAutoFillBeforeLastCat() {
        val s = TutorialPlayer.playUntil(engine, 8)
        assertEquals(3, s.game.board.catCount)
        assertEquals(1, s.game.board.marks.count { it == CellMark.EMPTY })
        assertTrue(TutorialPlayer.playUntil(engine, 5).game.board.marks.any { it == CellMark.EMPTY })
    }

    @Test
    fun finalContinueCompletes() {
        val s = TutorialPlayer.playUntil(engine, engine.stepCount - 1)
        assertEquals(engine.stepCount - 1, s.stepIndex)
        val r = engine.apply(s, TutorialAction.Continue)
        assertEquals(TutorialOutcome.COMPLETED, r.outcome)
        assertTrue(r.state.completed)
    }

    // ---- wrong actions ----

    @Test
    fun wrongCellRejectedNoHeartLostNoAdvance() {
        val s0 = TutorialState()
        val r = engine.apply(s0, TutorialAction.DoubleTap(Pos(0, 0))) // a cat on a non-solution cell
        assertEquals(TutorialOutcome.REJECTED, r.outcome)
        assertEquals(GameState.MAX_HEARTS, r.state.game.hearts)
        assertEquals(s0.game.board, r.state.game.board)
        assertEquals(0, r.state.stepIndex)
        assertEquals(1, r.state.rejections)
        assertEquals(TutorialNotice.WRONG_ACTION, r.state.notice)
        assertTrue(r.state.game.isPlaying)
    }

    @Test
    fun tapOnOtherCellDuringDoubleTapStepRejected() {
        val r = engine.apply(TutorialState(), TutorialAction.Tap(Pos(3, 3)))
        assertEquals(TutorialOutcome.REJECTED, r.outcome)
        assertEquals(CellMark.EMPTY, r.state.mark(3, 3))
    }

    @Test
    fun everyWrongActionKindIsRejectedWithoutSideEffects() {
        val s = TutorialState() // step 0: double-tap (0,2)
        val wrong = listOf(
            TutorialAction.Continue,
            TutorialAction.HintPill,
            TutorialAction.Drag(listOf(Pos(0, 1), Pos(0, 2))),
            TutorialAction.DoubleTap(Pos(2, 2)),
        )
        for (a in wrong) {
            val r = engine.apply(s, a)
            assertEquals("$a", TutorialOutcome.REJECTED, r.outcome)
            assertEquals("$a", s.game, r.state.game)
            assertEquals("$a", 0, r.state.stepIndex)
        }
    }

    @Test
    fun rejectionsAccumulate() {
        var s = TutorialState()
        repeat(5) { s = engine.apply(s, TutorialAction.Tap(Pos(3, 3))).state }
        assertEquals(5, s.rejections)
        assertEquals(GameState.MAX_HEARTS, s.game.hearts)
    }

    @Test
    fun noticeClearsOnNextAcceptedAction() {
        var s = engine.apply(TutorialState(), TutorialAction.Tap(Pos(3, 3))).state
        assertEquals(TutorialNotice.WRONG_ACTION, s.notice)
        s = engine.apply(s, TutorialAction.DoubleTap(Pos(0, 2))).state
        assertNull(s.notice)
    }

    @Test
    fun continueOnPlayStepAndPillOnInfoStepAreRejected() {
        val info = TutorialPlayer.playUntil(engine, 1)
        assertEquals(TutorialOutcome.REJECTED, engine.apply(info, TutorialAction.HintPill).outcome)
        assertEquals(TutorialOutcome.REJECTED, engine.apply(info, TutorialAction.Tap(Pos(0, 0))).outcome)
        val play = TutorialPlayer.playUntil(engine, 2)
        assertEquals(TutorialOutcome.REJECTED, engine.apply(play, TutorialAction.Continue).outcome)
    }

    @Test
    fun tapXStepRejectsNonTargetsAndRepeatTaps() {
        val s = TutorialPlayer.playUntil(engine, 2)
        val target = engine.step(s).targets.first()
        assertEquals(TutorialOutcome.REJECTED, engine.apply(s, TutorialAction.Tap(Pos(3, 3))).outcome)
        // a quick double tap on a target would place a cat in the real game: not here
        assertEquals(TutorialOutcome.REJECTED, engine.apply(s, TutorialAction.DoubleTap(target)).outcome)
        val marked = engine.apply(s, TutorialAction.Tap(target)).state
        val again = engine.apply(marked, TutorialAction.Tap(target)) // would toggle the X off
        assertEquals(TutorialOutcome.REJECTED, again.outcome)
        assertEquals(CellMark.X, again.state.mark(target.row, target.col))
    }

    @Test
    fun swipeRejectsOffTargetCellsAndTapOnSwipeStep() {
        val s = TutorialPlayer.playUntil(engine, 4)
        val t = engine.step(s).targets
        val off = engine.apply(s, TutorialAction.Drag(listOf(t[0], t[1], Pos(1, 3))))
        assertEquals(TutorialOutcome.REJECTED, off.outcome)
        assertEquals(CellMark.EMPTY, off.state.mark(t[0].row, t[0].col))
        assertEquals(TutorialOutcome.REJECTED, engine.apply(s, TutorialAction.Tap(t[0])).outcome)
        assertEquals(TutorialOutcome.REJECTED, engine.apply(s, TutorialAction.Drag(listOf(t[0]))).outcome)
    }

    @Test
    fun alreadyPlacedCatCannotBeDoubleTappedAgain() {
        val s = TutorialPlayer.playUntil(engine, 1)
        // step 1 is info, so the cat sits at (0,2); a stray double-tap there is just wrong
        assertEquals(TutorialOutcome.REJECTED, engine.apply(s, TutorialAction.DoubleTap(Pos(0, 2))).outcome)
        assertEquals(CellMark.CAT, s.mark(0, 2))
    }

    // ---- skip / completion ----

    @Test
    fun skipCompletesFromAnyStep() {
        for (i in 0 until engine.stepCount) {
            val s = TutorialPlayer.playUntil(engine, i)
            val r = engine.skip(s)
            assertEquals(TutorialOutcome.COMPLETED, r.outcome)
            assertTrue(r.state.completed)
        }
    }

    @Test
    fun actionsAfterCompletionAreIgnored() {
        val done = engine.skip(TutorialState()).state
        val r = engine.apply(done, TutorialAction.DoubleTap(Pos(0, 2)))
        assertEquals(TutorialOutcome.IGNORED, r.outcome)
        assertEquals(done, r.state)
        assertFalse(r.state.game.board.catCount > 0)
    }

    @Test
    fun remainingTargetsEmptyWhenCompleted() {
        assertTrue(engine.remainingTargets(engine.skip(TutorialState()).state).isEmpty())
    }
}
