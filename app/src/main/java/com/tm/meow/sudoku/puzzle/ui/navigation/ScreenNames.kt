package com.tm.meow.sudoku.puzzle.ui.navigation

/**
 * Stable analytics screen name per nav route PATTERN (`NavDestination.route`, never the resolved URL), so no argument
 * value (level, tier, section, size) can reach `screen_view`. snake_case, <= 40 chars; never rename, only add.
 * Null = unknown route (not logged). Pure, unit-tested.
 */
internal fun screenNameFor(routePattern: String?): String? = when (routePattern) {
    Routes.SPLASH -> "splash"
    Routes.WELCOME -> "welcome"
    Routes.NOTIFICATION -> "notification_prompt"
    Routes.TUTORIAL -> "tutorial"
    Routes.HOME -> "home"
    Routes.SUDOKU -> "sudoku_entry"
    Routes.SUDOKU_TUTORIAL_PATTERN -> "sudoku_tutorial"
    Routes.SUDOKU_GAME_PATTERN -> "sudoku_game"
    Routes.STREAK -> "streak"
    Routes.GAME_PATTERN -> "game"
    Routes.SETTINGS -> "settings"
    Routes.SHOP_PATTERN -> "shop"
    else -> null
}
