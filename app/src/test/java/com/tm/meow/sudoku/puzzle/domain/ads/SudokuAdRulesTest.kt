package com.tm.meow.sudoku.puzzle.domain.ads

import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuTier
import com.tm.meow.sudoku.puzzle.domain.sudoku.usecase.ShowSudokuInterstitialIfAllowed
import com.tm.meow.sudoku.puzzle.domain.usecase.ShowInterstitialIfAllowed
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuAdRulesTest {

    private val nonEasy = SudokuTier.entries.filter { it != SudokuTier.EASY }

    @Test
    fun easyWinsOneToFourNoAdFiveAndAfterYes() {
        for (w in 1..4) assertFalse("easy win $w", SudokuAdRules.showOnWin(SudokuTier.EASY, w))
        for (w in listOf(5, 6, 7, 20)) assertTrue("easy win $w", SudokuAdRules.showOnWin(SudokuTier.EASY, w))
        assertFalse(SudokuAdRules.showOnWin(SudokuTier.EASY, null))
    }

    @Test
    fun otherTiersShowOnEveryWin() {
        assertEquals(5, nonEasy.size)
        for (t in nonEasy) for (w in listOf(1, 2, 3, 10)) assertTrue("$t $w", SudokuAdRules.showOnWin(t, w))
        for (t in nonEasy) assertTrue("$t unknown", SudokuAdRules.showOnWin(t, null))
    }

    @Test
    fun restartShowsEverywhereExceptEasy() {
        assertFalse(SudokuAdRules.showOnRestart(SudokuTier.EASY))
        for (t in nonEasy) assertTrue("$t", SudokuAdRules.showOnRestart(t))
    }

    @Test
    fun useCaseOnlyAsksPolicyWhenRuleAllows() = runTest {
        val show = mockk<ShowInterstitialIfAllowed>()
        coEvery { show(any(), any(), any(), any()) } returns true
        val uc = ShowSudokuInterstitialIfAllowed(show)

        assertFalse(uc.onWin(SudokuTier.EASY, 4))
        assertFalse(uc.onRestart(SudokuTier.EASY))
        coVerify(exactly = 0) { show(any(), any(), any(), any()) }

        assertTrue(uc.onWin(SudokuTier.EASY, 5))
        coVerify(exactly = 1) { show(AdTrigger.SUDOKU_WON_CONTINUE, 0, false, false) }
        assertTrue(uc.onWin(SudokuTier.MEDIUM, 1))
        assertTrue(uc.onRestart(SudokuTier.HARD))
        coVerify(exactly = 1) { show(AdTrigger.SUDOKU_RESTART, 0, false, false) }
    }
}
