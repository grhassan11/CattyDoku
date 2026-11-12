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

    private fun win(tier: SudokuTier, n: Int?, freq: Int = 1) =
        SudokuAdRules.showOnWin(tier, n, freq)

    @Test
    fun allTiersFreq1ShowEveryWinFromOne() {
        assertEquals(5, nonEasy.size)
        for (t in SudokuTier.entries) {
            for (w in listOf(1, 2, 3, 4, 5, 9, 10, 20)) assertTrue("$t $w", win(t, w))
        }
    }

    @Test
    fun allTiersFreq10ShowOnMultiplesOfTenOnly() {
        for (t in SudokuTier.entries) {
            for (w in listOf(1, 5, 9, 11, 15, 19)) assertFalse("$t $w", win(t, w, freq = 10))
            for (w in listOf(10, 20, 30, 40)) assertTrue("$t $w", win(t, w, freq = 10))
        }
        assertTrue(win(SudokuTier.EASY, 3, freq = 3))
    }

    @Test
    fun unknownWinCountNeverShows() {
        for (t in SudokuTier.entries) assertFalse("$t unknown", win(t, null))
    }

    @Test
    fun frequencyBelowOneIsTreatedAsOne() {
        assertTrue(win(SudokuTier.MEDIUM, 1, freq = 0))
        assertTrue(win(SudokuTier.EASY, 7, freq = -3))
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
        val uc = ShowSudokuInterstitialIfAllowed(show, AdPolicyConfig())

        assertFalse(uc.onWin(SudokuTier.EASY, null))
        assertFalse(uc.onRestart(SudokuTier.EASY))
        coVerify(exactly = 0) { show(any(), any(), any(), any()) }

        assertTrue(uc.onWin(SudokuTier.EASY, 1))
        coVerify(exactly = 1) { show(AdTrigger.SUDOKU_WON_CONTINUE, 0, false, false) }
        coVerify(exactly = 1) { show(AdTrigger.SUDOKU_WON_CONTINUE, 0, false, false) }
        assertTrue(uc.onWin(SudokuTier.MEDIUM, 1))
        assertTrue(uc.onRestart(SudokuTier.HARD))
        coVerify(exactly = 1) { show(AdTrigger.SUDOKU_RESTART, 0, false, false) }
    }
}
