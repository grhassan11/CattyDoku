package com.threetech.catpuzzle.ui.screens.tutorial

import com.threetech.catpuzzle.domain.model.Pos
import com.threetech.catpuzzle.domain.model.Settings
import com.threetech.catpuzzle.domain.tutorial.TutorialAction
import com.threetech.catpuzzle.domain.tutorial.TutorialEngine
import com.threetech.catpuzzle.domain.tutorial.TutorialFocus
import com.threetech.catpuzzle.domain.tutorial.TutorialPlayer
import com.threetech.catpuzzle.domain.tutorial.TutorialState
import com.threetech.catpuzzle.domain.tutorial.TutorialTextKey
import com.threetech.catpuzzle.ui.screens.game.CellMarkUi
import com.threetech.catpuzzle.ui.screens.game.HintRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialUiMapperTest {

    private val engine = TutorialEngine()
    private fun ui(s: TutorialState, settings: Settings = Settings()) = TutorialUiMapper.map(engine, s, settings)

    @Test
    fun firstStepShowsDoubleTapPointerAndSpotlightOnTarget() {
        val u = ui(TutorialState())
        assertEquals(TutorialTextKey.PLACE_FIRST, u.text)
        assertEquals(TutorialPointerUi(PointerKind.DOUBLE_TAP, Pos(0, 1), Pos(0, 1)), u.pointer)
        assertEquals(setOf(1), u.spotlight)
        assertFalse(u.showContinue)
        assertEquals(TutorialFocus.BOARD, u.focus)
        assertEquals(4, u.board.size)
        assertTrue(u.board.cells.none { it.mark != CellMarkUi.EMPTY }) // no starter cat
        assertEquals(3, u.hearts)
        assertEquals(1, u.hintCharges)
        assertEquals(1, u.catCharges)
    }

    @Test
    fun infoStepHasContinueAndNoPointer() {
        val u = ui(TutorialPlayer.playUntil(engine, 1))
        assertTrue(u.showContinue)
        assertFalse(u.continueIsFinal)
        assertNull(u.pointer)
        assertTrue(u.spotlight.isEmpty())
    }

    @Test
    fun swipeStepPointerTravelsOverRemainingCells() {
        val s = TutorialPlayer.playUntil(engine, 3)
        val u = ui(s)
        assertEquals(PointerKind.SWIPE, u.pointer?.kind)
        assertEquals(Pos(1, 0), u.pointer?.from)
        assertEquals(Pos(1, 2), u.pointer?.to)
        // after one cell is crossed the finger starts at the next one
        val after = engine.apply(s, TutorialAction.Drag(listOf(Pos(1, 0), Pos(1, 1)))).state
        assertEquals(Pos(1, 2), ui(after).pointer?.from)
    }

    @Test
    fun boosterStepsFocusTheButtonWithNullCellPointer() {
        val hintStep = ui(TutorialPlayer.playUntil(engine, 6))
        assertEquals(TutorialFocus.HINT_BUTTON, hintStep.focus)
        assertEquals(TutorialPointerUi(PointerKind.TAP, null, null), hintStep.pointer)
        val catStep = ui(TutorialPlayer.playUntil(engine, 8))
        assertEquals(TutorialFocus.CAT_BUTTON, catStep.focus)
        assertEquals(0, catStep.hintCharges)
    }

    @Test
    fun heartsStepFocusesHearts() {
        assertEquals(TutorialFocus.HEARTS, ui(TutorialPlayer.playUntil(engine, 5)).focus)
    }

    @Test
    fun hintStepDrawsTargetRingOnlyOnTheTargetCell() {
        val u = ui(TutorialPlayer.playUntil(engine, 7))
        val ringed = u.board.cells.filter { it.hintRole == HintRole.TARGET }
        assertEquals(listOf(Pos(2, 0)), ringed.map { Pos(it.row, it.col) })
        assertTrue(u.board.cells.none { it.hintRole == HintRole.REASON })
        // no ring on other steps
        assertTrue(ui(TutorialPlayer.playUntil(engine, 4)).board.cells.none { it.hintRole != HintRole.NONE })
    }

    @Test
    fun lastStepContinueIsFinal() {
        val u = ui(TutorialPlayer.playUntil(engine, 9))
        assertTrue(u.showContinue)
        assertTrue(u.continueIsFinal)
        assertEquals(4, u.board.cells.count { it.mark == CellMarkUi.CAT })
    }

    @Test
    fun rejectCountAndNoticeComeThrough() {
        val s = engine.apply(TutorialState(), TutorialAction.Tap(Pos(3, 3))).state
        val u = ui(s)
        assertEquals(1, u.rejectCount)
        assertNotNull(u.notice)
        assertEquals(3, u.hearts)
    }

    @Test
    fun patternModeFollowsSettings() {
        assertTrue(ui(TutorialState(), Settings(patternMode = true)).patternMode)
        assertFalse(ui(TutorialState()).patternMode)
    }
}
