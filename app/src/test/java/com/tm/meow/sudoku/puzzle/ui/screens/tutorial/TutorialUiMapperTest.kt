package com.tm.meow.sudoku.puzzle.ui.screens.tutorial

import com.tm.meow.sudoku.puzzle.domain.model.Pos
import com.tm.meow.sudoku.puzzle.domain.model.Settings
import com.tm.meow.sudoku.puzzle.domain.tutorial.TutorialAction
import com.tm.meow.sudoku.puzzle.domain.tutorial.TutorialBottomKey
import com.tm.meow.sudoku.puzzle.domain.tutorial.TutorialEngine
import com.tm.meow.sudoku.puzzle.domain.tutorial.TutorialPlayer
import com.tm.meow.sudoku.puzzle.domain.tutorial.TutorialScript
import com.tm.meow.sudoku.puzzle.domain.tutorial.TutorialState
import com.tm.meow.sudoku.puzzle.domain.tutorial.TutorialTextKey
import com.tm.meow.sudoku.puzzle.ui.screens.game.CellMarkUi
import com.tm.meow.sudoku.puzzle.ui.screens.game.HintRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialUiMapperTest {

    private val engine = TutorialEngine()
    private fun ui(s: TutorialState, settings: Settings = Settings()) = TutorialUiMapper.map(engine, s, settings)
    private fun at(step: Int) = ui(TutorialPlayer.playUntil(engine, step))
    private val n = TutorialScript.SIZE

    @Test
    fun firstStepShowsDoubleTapPointerDimAndSkip() {
        val u = ui(TutorialState())
        assertEquals(TutorialTextKey.PLACE_FIRST, u.text)
        assertEquals(TutorialPointerUi(PointerKind.DOUBLE_TAP, Pos(0, 2), Pos(0, 2)), u.pointer)
        assertEquals(setOf(2), u.spotlight)
        assertEquals(setOf(2), u.interactive)
        assertFalse(u.light)
        assertFalse(u.showContinue)
        assertTrue(u.showSkip)
        assertNull(u.bottom)
        assertEquals(4, u.board.size)
        assertTrue(u.board.cells.none { it.mark != CellMarkUi.EMPTY }) // no starter cat
    }

    @Test
    fun recapStepIsLightWithContinueAndNoPointer() {
        val u = at(1)
        assertTrue(u.light)
        assertTrue(u.showContinue)
        assertFalse(u.continueIsFinal)
        assertFalse(u.showSkip)
        assertNull(u.pointer)
        assertTrue(u.spotlight.isEmpty())
        assertTrue(u.interactive.isEmpty()) // board fully gated
    }

    @Test
    fun tapXStepRingsEveryRemainingCellAndShowsBottomCard() {
        val s = TutorialPlayer.playUntil(engine, 2)
        val u = ui(s)
        assertEquals(TutorialBottomKey.TAP_EMPTY, u.bottom)
        assertEquals(setOf(0, 1, 3, 6, 10, 14), u.spotlight)
        assertEquals(PointerKind.TAP, u.pointer?.kind)
        // a marked cell leaves the ring set and the interactive set
        val after = engine.apply(s, TutorialAction.Tap(Pos(0, 0))).state
        assertEquals(setOf(1, 3, 6, 10, 14), ui(after).spotlight)
        assertEquals(Pos(0, 1), ui(after).pointer?.from)
    }

    @Test
    fun tintedStepsCarryTheRegionOfTheirColourWord() {
        assertEquals(TutorialScript.REGION_ROSE, at(3).tintRegion)
        assertEquals(TutorialScript.REGION_BLUE, at(5).tintRegion)
        assertNull(at(2).tintRegion)
    }

    @Test
    fun swipeStepPointerTravelsOverRemainingCellsInOrder() {
        val s = TutorialPlayer.playUntil(engine, 4)
        val u = ui(s)
        assertEquals(TutorialBottomKey.SWIPE_ACROSS, u.bottom)
        assertEquals(PointerKind.SWIPE, u.pointer?.kind)
        assertEquals(Pos(2, 1), u.pointer?.from)
        assertEquals(Pos(3, 0), u.pointer?.to)
        assertEquals(listOf(Pos(2, 1), Pos(2, 0), Pos(3, 0)), u.pointer?.path)
        // after one cell is crossed the finger starts at the next one
        val after = engine.apply(s, TutorialAction.Drag(listOf(Pos(2, 1), Pos(2, 0)))).state
        assertEquals(Pos(3, 0), ui(after).pointer?.from)
    }

    @Test
    fun rowDoneStepsShareTheTextAndBottomCard() {
        val a = at(6)
        val b = at(7)
        assertEquals(TutorialTextKey.ROW_DONE, a.text)
        assertEquals(TutorialTextKey.ROW_DONE, b.text)
        assertEquals(TutorialBottomKey.TAP_EMPTY, a.bottom)
        assertEquals(setOf(5, 7), a.spotlight)
        assertEquals(setOf(15), b.spotlight)
    }

    @Test
    fun findLastCatStepIsLightWithPillAndNoRing() {
        val u = at(8)
        assertTrue(u.light)
        assertTrue(u.hintPill)
        assertTrue(u.spotlight.isEmpty())
        assertNull(u.pointer)
        assertEquals(setOf(11), u.interactive) // only the empty cell (2,3) reacts
        assertFalse(u.showContinue)
        assertTrue(u.board.cells.none { it.hintRole != HintRole.NONE })
    }

    @Test
    fun lastStepIsFinalLightWithAllCatsAndNoGate() {
        val u = at(9)
        assertTrue(u.light)
        assertTrue(u.showContinue)
        assertTrue(u.continueIsFinal)
        assertEquals(TutorialTextKey.EXCELLENT, u.text)
        assertEquals(4, u.board.cells.count { it.mark == CellMarkUi.CAT })
        assertEquals(12, u.board.cells.count { it.mark == CellMarkUi.X })
    }

    @Test
    fun onlyDimStepsHaveSpotlightCells() {
        for (i in 0 until engine.stepCount) {
            val u = at(i)
            if (u.light) assertTrue("step $i", u.spotlight.isEmpty()) else assertTrue("step $i", u.spotlight.isNotEmpty())
        }
    }

    @Test
    fun rejectCountAndNoticeComeThrough() {
        val s = engine.apply(TutorialState(), TutorialAction.Tap(Pos(3, 3))).state
        val u = ui(s)
        assertEquals(1, u.rejectCount)
        assertNotNull(u.notice)
    }

    @Test
    fun patternModeFollowsSettings() {
        assertTrue(ui(TutorialState(), Settings(patternMode = true)).patternMode)
        assertFalse(ui(TutorialState()).patternMode)
    }

    @Test
    fun boardSizeMatchesScript() {
        assertEquals(n, ui(TutorialState()).board.size)
    }
}
