package com.tm.meow.sudoku.puzzle.ui.screens.sudoku.game

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuTier
import com.tm.meow.sudoku.puzzle.ui.theme.GameColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuTierThemeTest {

    private val themes = SudokuTier.entries.associateWith { sudokuTierTheme(it) }

    private fun contrast(a: Color, b: Color): Float {
        val x = a.luminance()
        val y = b.luminance()
        return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f)
    }

    private fun onBoard(fill: Color) = fill.compositeOver(GameColors.Card)

    @Test
    fun allTiersMapped() = assertEquals(6, themes.size)

    @Test
    fun allTiersDistinct() {
        assertEquals(6, themes.values.map { it.backgroundTop }.toSet().size)
        assertEquals(6, themes.values.map { it.accent }.toSet().size)
        assertEquals(6, themes.values.map { it.selectedFill }.toSet().size)
    }

    @Test
    fun derivedColoursDefined() {
        themes.values.forEach {
            assertTrue(it.selectedFill.alpha > it.sameDigitFill.alpha)
            assertTrue(it.sameDigitFill.alpha > it.peerFill.alpha)
            assertTrue(it.peerFill.alpha > 0f)
            assertTrue(it.padBorderWidthDp > 0f)
            assertTrue(it.padFill != GameColors.Card)
        }
    }

    @Test
    fun backgroundStaysLightSoDarkStatusIconsRemainValid() {
        themes.values.forEach {
            assertTrue(it.backgroundTop.luminance() > 0.6f)
            assertTrue(it.backgroundBottom.luminance() > 0.6f)
        }
    }

    @Test
    fun headerTitleReadable() {
        themes.values.forEach { assertTrue(contrast(it.title, it.backgroundTop) >= 4.5f) }
    }

    @Test
    fun digitsAndNotesReadableOnEveryHighlight() {
        themes.values.forEach { t ->
            val given = t.ink
            val note = t.inkSoft.copy(alpha = 0.85f)
            listOf(t.selectedFill, t.sameDigitFill, t.peerFill).map(::onBoard).forEach { bg ->
                assertTrue(contrast(given, bg) >= 4.5f)
                assertTrue(contrast(note.compositeOver(bg), bg) >= 2.5f)
            }
        }
    }

    @Test
    fun errorRedStillStandsOutOnHighlights() {
        // Not worse than the pre-tier selected look (about 2.6) on any tint.
        val baseline = contrast(GameColors.Conflict, onBoard(SudokuTierTheme.Default.selectedFill))
        themes.values.forEach { t ->
            listOf(t.selectedFill, t.sameDigitFill, t.peerFill).map(::onBoard).forEach { bg ->
                assertTrue(contrast(GameColors.Conflict, bg) >= baseline)
            }
        }
    }

    @Test
    fun highlightTintsAreNotErrorRed() {
        themes.values.forEach { t ->
            assertTrue(t.selectedFill.copy(alpha = 1f) != GameColors.Conflict)
        }
    }

    @Test
    fun inkDistinctPerTier() = assertEquals(6, themes.values.map { it.ink }.toSet().size)

    @Test
    fun inkReadableOnWhiteFillsAndBackgrounds() {
        themes.values.forEach { t ->
            assertTrue(contrast(t.ink, GameColors.Card) >= 4.5f)
            assertTrue(contrast(t.ink, onBoard(t.selectedFill)) >= 4.5f)
            assertTrue(contrast(t.ink, t.backgroundTop) >= 4.5f)
            assertTrue(contrast(t.ink, t.backgroundBottom) >= 4.5f)
            assertTrue(contrast(t.inkSoft, GameColors.Card) >= 4.5f)
            assertTrue(contrast(t.inkSoft, t.backgroundTop) >= 4.5f)
        }
    }

    @Test
    fun givenInkDiffersFromPlayerDigit() {
        themes.values.forEach { assertTrue(it.ink != com.tm.meow.sudoku.puzzle.ui.screens.sudoku.game.SudokuColors.PlayerDigit) }
    }

    @Test
    fun defaultKeepsOldBrown() {
        val d = SudokuTierTheme.Default
        assertEquals(GameColors.Title, d.ink)
        assertEquals(GameColors.Body, d.inkSoft)
        assertEquals(GameColors.CtaCreamLip, d.line)
    }
}
