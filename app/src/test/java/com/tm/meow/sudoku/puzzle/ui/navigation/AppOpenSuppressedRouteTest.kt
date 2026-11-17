package com.tm.meow.sudoku.puzzle.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOpenSuppressedRouteTest {
    private fun s(route: String?) = StartupRecoveryViewModel.isAppOpenSuppressedRoute(route)

    @Test
    fun settingsAboutShopSuppressed() {
        assertTrue(s(Routes.SETTINGS))
        assertTrue(s(Routes.ABOUT))
        assertTrue(s(Routes.SHOP))
        assertTrue(s(Routes.SHOP_PATTERN))
    }

    @Test
    fun otherRoutesAllowed() {
        assertFalse(s(null))
        assertFalse(s(Routes.SPLASH))
        assertFalse(s(Routes.GAME_PATTERN))
        assertFalse(s(Routes.STREAK))
    }
}
