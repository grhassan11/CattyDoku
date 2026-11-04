package com.tm.meow.sudoku.puzzle.data.local.prefs

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import com.tm.meow.sudoku.puzzle.domain.model.AppFlags
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuTutorialPrefsTest {

    @Test
    fun keysAreDistinctPerSizeAndTheNineByNineKeyIsTheLegacyOne() {
        val names = SudokuSize.entries.map { PrefKeys.sudokuTutorialKey(it).name }
        assertEquals(names.size, names.toSet().size)
        assertEquals("sudoku_tutorial_done", PrefKeys.sudokuTutorialKey(SudokuSize.S9).name)
        assertEquals("sudoku_tutorial_done_4x4", PrefKeys.sudokuTutorialKey(SudokuSize.S4).name)
        assertEquals("sudoku_tutorial_done_6x6", PrefKeys.sudokuTutorialKey(SudokuSize.S6).name)
        assertEquals("sudoku_tutorial_done_8x8", PrefKeys.sudokuTutorialKey(SudokuSize.S8).name)
    }

    @Test
    fun emptyStoreReadsAsNothingSeen() {
        assertTrue(emptyPreferences().toAppFlags().sudokuTutorialSeen.isEmpty())
    }

    @Test
    fun legacyFlagMigratesToNineByNineOnly() {
        // an install from before per-size tutorials: only the old key exists, set to true
        val old = preferencesOf(booleanPreferencesKey("sudoku_tutorial_done") to true)
        assertEquals(setOf(SudokuSize.S9), old.toAppFlags().sudokuTutorialSeen)
    }

    @Test
    fun legacyFlagFalseStaysNotSeen() {
        val old = preferencesOf(booleanPreferencesKey("sudoku_tutorial_done") to false)
        assertTrue(old.toAppFlags().sudokuTutorialSeen.isEmpty())
    }

    @Test
    fun everySizeRoundTripsThroughWriteAndRead() {
        for (size in SudokuSize.entries) {
            val prefs = mutablePreferencesOf()
            prefs.write(AppFlags(sudokuTutorialSeen = setOf(size)))
            assertEquals("$size", setOf(size), prefs.toAppFlags().sudokuTutorialSeen)
        }
        val prefs = mutablePreferencesOf()
        prefs.write(AppFlags(sudokuTutorialSeen = SudokuSize.entries.toSet()))
        assertEquals(SudokuSize.entries.toSet(), prefs.toAppFlags().sudokuTutorialSeen)
    }

    @Test
    fun writingSeenOverwritesAnEarlierSeen() {
        val prefs = mutablePreferencesOf()
        prefs.write(AppFlags(sudokuTutorialSeen = setOf(SudokuSize.S4, SudokuSize.S9)))
        prefs.write(AppFlags(sudokuTutorialSeen = setOf(SudokuSize.S4)))
        assertEquals(setOf(SudokuSize.S4), prefs.toAppFlags().sudokuTutorialSeen)
    }
}
