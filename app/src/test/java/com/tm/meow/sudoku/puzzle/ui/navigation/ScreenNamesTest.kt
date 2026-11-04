package com.tm.meow.sudoku.puzzle.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenNamesTest {
    private val patterns = listOf(
        Routes.SPLASH, Routes.WELCOME, Routes.NOTIFICATION, Routes.TUTORIAL, Routes.HOME, Routes.SUDOKU,
        Routes.SUDOKU_TUTORIAL_PATTERN, Routes.SUDOKU_GAME_PATTERN, Routes.STREAK, Routes.GAME_PATTERN,
        Routes.SETTINGS, Routes.SHOP_PATTERN,
    )

    @Test
    fun everyRoutePatternHasAUniqueSnakeCaseName() {
        val names = patterns.map { screenNameFor(it) }
        assertTrue(names.none { it == null })
        assertEquals(patterns.size, names.toSet().size)
        names.forEach { assertTrue(it, Regex("^[a-z][a-z0-9_]{0,39}$").matches(it!!)) }
    }

    @Test
    fun unknownOrNullRouteIsNotLogged() {
        assertNull(screenNameFor(null))
        assertNull(screenNameFor("game?level=7")) // resolved URL, not a pattern
    }
}
