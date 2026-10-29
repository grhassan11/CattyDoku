package com.threetech.catpuzzle.domain.tutorial

import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.domain.model.CellMark
import com.threetech.catpuzzle.domain.model.GameState
import com.threetech.catpuzzle.domain.model.GameStatus
import com.threetech.catpuzzle.domain.model.Pos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialEngineTest {

    private val engine = TutorialEngine()

    private fun idx(name: TutorialTextKey): Int = TutorialScript.steps.indexOfFirst { it.text == name }

    private fun TutorialState.mark(r: Int, c: Int): CellMark = game.board[r, c]

    // ---- step advancement ----

    @Test
    fun startsAtStepZeroWithOneChargeEach() {
        val s = TutorialState()
        assertEquals(0, s.stepIndex)
        assertEquals(1, s.game.chargesOf(Booster.HINT))
        assertEquals(1, s.game.chargesOf(Booster.CAT))
        assertEquals(10, engine.stepCount)
    }

    @Test
    fun doubleTapOnTargetPlacesCatAndAdvances() {
        val s0 = TutorialState()
        val tap = engine.apply(s0, TutorialAction.Tap(Pos(0, 1)))
        val dbl = engine.apply(tap.state, TutorialAction.DoubleTap(Pos(0, 1)))
        assertEquals(TutorialOutcome.ADVANCED, dbl.outcome)
        assertEquals(1, dbl.state.stepIndex)
        assertEquals(CellMark.CAT, dbl.state.mark(0, 1))
        assertEquals(GameState.MAX_HEARTS, dbl.state.game.hearts)
        assertNull(dbl.state.notice)
    }

    @Test
    fun firstTapOfDoubleTapAppliesNothing() {
        val r = engine.apply(TutorialState(), TutorialAction.Tap(Pos(0, 1)))
        assertEquals(TutorialOutcome.NEEDS_SECOND_TAP, r.outcome)
        assertEquals(TutorialNotice.TAP_TWICE, r.state.notice)
        assertEquals(CellMark.EMPTY, r.state.mark(0, 1))
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
        var s = TutorialPlayer.playUntil(engine, 2)
        val targets = engine.step(s).targets
        assertEquals(3, targets.size)
        val a = engine.apply(s, TutorialAction.Tap(targets[0]))
        assertEquals(TutorialOutcome.APPLIED, a.outcome)
        assertEquals(2, a.state.stepIndex)
        assertEquals(targets.drop(1), engine.remainingTargets(a.state))
        val b = engine.apply(a.state, TutorialAction.Tap(targets[2])) // any order
        assertEquals(TutorialOutcome.APPLIED, b.outcome)
        val c = engine.apply(b.state, TutorialAction.Tap(targets[1]))
        assertEquals(TutorialOutcome.ADVANCED, c.outcome)
        assertEquals(3, c.state.stepIndex)
        for (t in targets) assertEquals(CellMark.X, c.state.mark(t.row, t.col))
    }

    @Test
    fun swipeAcrossAllTargetsAdvances() {
        val s = TutorialPlayer.playUntil(engine, 3)
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
        val s = TutorialPlayer.playUntil(engine, 3)
        val targets = engine.step(s).targets
        val r = engine.apply(s, TutorialAction.Drag(targets.take(2)))
        assertEquals(TutorialOutcome.APPLIED, r.outcome)
        assertEquals(3, r.state.stepIndex)
        assertEquals(listOf(targets[2]), engine.remainingTargets(r.state))
    }

    @Test
    fun hintButtonSpendsOneChargeAndAdvances() {
        val s = TutorialPlayer.playUntil(engine, idx(TutorialTextKey.TRY_HINT))
        val r = engine.apply(s, TutorialAction.UseHint)
        assertEquals(TutorialOutcome.ADVANCED, r.outcome)
        assertEquals(0, r.state.game.chargesOf(Booster.HINT))
        assertEquals(1, r.state.game.chargesOf(Booster.CAT))
        assertTrue(engine.step(r.state).showHintRing)
        assertEquals(listOf(Pos(2, 0)), engine.remainingTargets(r.state))
    }

    @Test
    fun catBoosterPlacesLockedCatSolvesBoardAndSpendsCharge() {
        val s = TutorialPlayer.playUntil(engine, idx(TutorialTextKey.TRY_CAT_BOOSTER))
        val r = engine.apply(s, TutorialAction.UseCatBooster)
        assertEquals(TutorialOutcome.ADVANCED, r.outcome)
        assertEquals(0, r.state.game.chargesOf(Booster.CAT))
        assertEquals(CellMark.CAT, r.state.mark(3, 2))
        assertTrue(3 * 4 + 2 in r.state.game.lockedCells)
        assertEquals(GameStatus.SOLVED, r.state.game.status)
        assertEquals(GameState.MAX_HEARTS, r.state.game.hearts)
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
        val s = TutorialState() // step 0: double-tap (0,1)
        val wrong = listOf(
            TutorialAction.Continue,
            TutorialAction.UseHint,
            TutorialAction.UseCatBooster,
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
        s = engine.apply(s, TutorialAction.DoubleTap(Pos(0, 1))).state
        assertNull(s.notice)
    }

    @Test
    fun continueOnPlayStepAndBoosterOnInfoStepAreRejected() {
        val info = TutorialPlayer.playUntil(engine, 1)
        assertEquals(TutorialOutcome.REJECTED, engine.apply(info, TutorialAction.UseHint).outcome)
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
        val s = TutorialPlayer.playUntil(engine, 3)
        val t = engine.step(s).targets
        val off = engine.apply(s, TutorialAction.Drag(listOf(t[0], t[1], Pos(2, 1))))
        assertEquals(TutorialOutcome.REJECTED, off.outcome)
        assertEquals(CellMark.EMPTY, off.state.mark(t[0].row, t[0].col))
        assertEquals(TutorialOutcome.REJECTED, engine.apply(s, TutorialAction.Tap(t[0])).outcome)
        assertEquals(TutorialOutcome.REJECTED, engine.apply(s, TutorialAction.Drag(listOf(t[0]))).outcome)
    }

    @Test
    fun alreadyPlacedCatCannotBeDoubleTappedAgain() {
        val s = TutorialPlayer.playUntil(engine, 1)
        // step 1 is info, so the cat sits at (0,1); a stray double-tap there is just wrong
        assertEquals(TutorialOutcome.REJECTED, engine.apply(s, TutorialAction.DoubleTap(Pos(0, 1))).outcome)
        assertEquals(CellMark.CAT, s.mark(0, 1))
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
        val r = engine.apply(done, TutorialAction.DoubleTap(Pos(0, 1)))
        assertEquals(TutorialOutcome.IGNORED, r.outcome)
        assertEquals(done, r.state)
        assertFalse(r.state.game.board.catCount > 0)
    }

    @Test
    fun remainingTargetsEmptyWhenCompleted() {
        assertTrue(engine.remainingTargets(engine.skip(TutorialState()).state).isEmpty())
    }
}
