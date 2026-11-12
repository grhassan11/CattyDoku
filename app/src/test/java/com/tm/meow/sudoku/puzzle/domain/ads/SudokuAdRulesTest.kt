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

    private fun win(tier: SudokuTier, n: Int?, freq: Int = 1, free: Int = 9) =
        SudokuAdRules.showOnWin(tier, n, freq, free)

    @Test
    fun easyWinsOneToFourNoAdFiveAndAfterYes() {
        for (w in 1..4) assertFalse("easy win $w", win(SudokuTier.EASY, w))
        for (w in listOf(5, 6, 7, 20)) assertTrue("easy win $w", win(SudokuTier.EASY, w))
        assertFalse(win(SudokuTier.EASY, null))
        // easy ignores frequency + ad-free threshold
        assertTrue(win(SudokuTier.EASY, 5, freq = 10, free = 9))
    }

    @Test
    fun otherTiersFreq1ShowFromWinTenOnEveryWin() {
        assertEquals(5, nonEasy.size)
        for (t in nonEasy) {
            for (w in 1..9) assertFalse("$t $w", win(t, w))
            for (w in listOf(10, 11, 12, 13)) assertTrue("$t $w", win(t, w))
        }
    }

    @Test
    fun otherTiersFreq10ShowOnMultiplesPastAdFree() {
        for (t in nonEasy) {
            for (w in listOf(9, 11, 15, 19)) assertFalse("$t $w", win(t, w, freq = 10))
            for (w in listOf(10, 20, 30, 40)) assertTrue("$t $w", win(t, w, freq = 10))
        }
    }

    @Test
    fun otherTiersUnknownWinCountNeverShows() {
        for (t in nonEasy) assertFalse("$t unknown", win(t, null))
    }

    @Test
    fun frequencyBelowOneIsTreatedAsOne() {
        assertTrue(win(SudokuTier.MEDIUM, 11, freq = 0))
        assertFalse(win(SudokuTier.MEDIUM, 9, freq = 0))
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

        assertFalse(uc.onWin(SudokuTier.EASY, 4))
        assertFalse(uc.onRestart(SudokuTier.EASY))
        coVerify(exactly = 0) { show(any(), any(), any(), any()) }

        assertTrue(uc.onWin(SudokuTier.EASY, 5))
        coVerify(exactly = 1) { show(AdTrigger.SUDOKU_WON_CONTINUE, 0, false, false) }
        assertFalse(uc.onWin(SudokuTier.MEDIUM, 9))
        coVerify(exactly = 1) { show(AdTrigger.SUDOKU_WON_CONTINUE, 0, false, false) }
        assertTrue(uc.onWin(SudokuTier.MEDIUM, 10))
        assertTrue(uc.onRestart(SudokuTier.HARD))
        coVerify(exactly = 1) { show(AdTrigger.SUDOKU_RESTART, 0, false, false) }
    }
}
