package com.tm.meow.sudoku.puzzle.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tm.meow.sudoku.puzzle.domain.model.Booster
import com.tm.meow.sudoku.puzzle.domain.startup.StartFlow
import com.tm.meow.sudoku.puzzle.domain.startup.StartStep
import com.tm.meow.sudoku.puzzle.ui.screens.about.AboutRoute
import com.tm.meow.sudoku.puzzle.ui.screens.game.GameRoute
import com.tm.meow.sudoku.puzzle.ui.screens.game.GameViewModel
import com.tm.meow.sudoku.puzzle.ui.screens.home.HomeRoute
import com.tm.meow.sudoku.puzzle.ui.screens.home.StreakRoute
import com.tm.meow.sudoku.puzzle.ui.screens.notification.NotificationPromptRoute
import com.tm.meow.sudoku.puzzle.ui.screens.settings.SettingsRoute
import com.tm.meow.sudoku.puzzle.ui.screens.shop.ShopRoute
import com.tm.meow.sudoku.puzzle.ui.screens.shop.ShopUiMapper
import com.tm.meow.sudoku.puzzle.ui.screens.splash.SplashRoute
import com.tm.meow.sudoku.puzzle.ui.screens.sudoku.entry.SudokuEntryRoute
import com.tm.meow.sudoku.puzzle.ui.screens.sudoku.game.SudokuGameRoute
import com.tm.meow.sudoku.puzzle.ui.screens.sudoku.tutorial.SudokuTutorialRoute
import com.tm.meow.sudoku.puzzle.ui.screens.tutorial.TutorialRoute
import com.tm.meow.sudoku.puzzle.ui.screens.welcome.WelcomeRoute
import com.tm.meow.sudoku.puzzle.ui.theme.GameColors

/**
 * Flow: system splash -> in-app splash (UMP consent + ads init gate it, every launch) -> welcome / terms
 * (first launch) -> notification prompt (API 33+, once) -> tutorial (until done) -> home -> game. The order and which
 * steps apply live in the pure StartFlow. Every start step pops itself off the stack, so back never
 * returns to splash / welcome / prompt / tutorial and system back on Home exits the app.
 * String routes on purpose: type-safe routes need the kotlinx.serialization plugin (not added).
 */
object Routes {
    const val SPLASH = "splash"
    const val WELCOME = "welcome"
    const val NOTIFICATION = "notification"
    const val HOME = "home"
    const val GAME = "game"

    /** Same destination as [GAME] with the Daily Challenge mode argument (read by GameViewModel via SavedStateHandle). */
    const val DAILY = "game?daily=true"

    /** Graph pattern: optional `daily` (default false) and `level` (default 0 = resolve), so plain "game" still resolves. */
    internal const val GAME_PATTERN = "game?daily={daily}&level={level}"

    /** Same destination as [GAME], opening exactly [level] (Home map: current node = play, finished node = replay). */
    fun gameLevel(level: Int): String = "$GAME?level=$level"
    const val TUTORIAL = "tutorial"

    /** Full-screen Daily Streak (opened from the Home streak chip). */
    const val STREAK = "streak"

    /** Shop. Optional `section` arg (cat / hint / mouse) lands on that pack section. Plain "shop" = top. */
    const val SHOP = "shop"
    internal const val SHOP_PATTERN = "shop?section={section}"
    internal const val ARG_SHOP_SECTION = "section"

    /** Full-screen Settings (from Home only; in a game, Settings is a dialog on the game screen). */
    const val SETTINGS = "settings"

    /** Full-screen About (from Settings). */
    const val ABOUT = "about"

    /** Sudoku entry screen (difficulty picker). Opened from the Home Sudoku tab. */
    const val SUDOKU = "sudoku"
    /**
     * Sudoku tutorial (2 lessons) of one board size. Args: `size` = SudokuSize.name (required path segment); `next` = SudokuTier.name
     * of the game to open when the tutorial ends (empty = just pop back, used by the game "?"); `resume` = that game's resume flag.
     * First play of a size: opened by the entry screen. Re-opened from the game "?". Mode-local, not in StartFlow.
     */
    const val SUDOKU_TUTORIAL_PATTERN = "sudoku_tutorial/{size}?next={next}&resume={resume}"
    const val ARG_SUDOKU_TUTORIAL_SIZE = "size"
    const val ARG_SUDOKU_TUTORIAL_NEXT = "next"
    const val ARG_SUDOKU_TUTORIAL_RESUME = "resume"

    /** [sizeId] = SudokuSize.name. [nextTierId] = SudokuTier.name to open after the tutorial, or empty to return to the caller. */
    fun sudokuTutorial(sizeId: String, nextTierId: String = "", resume: Boolean = true): String =
        "sudoku_tutorial/$sizeId?next=$nextTierId&resume=$resume"

    /** Sudoku game route. Args: `tier` = SudokuTier.name (required path segment), `resume` (default true). */
    const val SUDOKU_GAME_PATTERN = "sudoku_game/{tier}?resume={resume}"
    const val ARG_SUDOKU_TIER = "tier"
    const val ARG_SUDOKU_RESUME = "resume"

    /** [tierId] = SudokuTier.name. [resume] false = the entry just cleared the save and wants a fresh puzzle. */
    fun sudokuGame(tierId: String, resume: Boolean = true): String = "sudoku_game/$tierId?resume=$resume"

    /** Shop opened from a booster dialog: scroll to the matching pack section. */
    fun shopFor(booster: Booster): String = "$SHOP?$ARG_SHOP_SECTION=${ShopUiMapper.argFor(booster)}"
}

/** Leave [from] (removed from the back stack, inclusive) for [step]. */
private fun NavHostController.goTo(step: StartStep, from: String) {
    navigate(routeFor(step)) {
        popUpTo(from) { inclusive = true }
        launchSingleTop = true
    }
}
/**
 * Pop only while the top entry is RESUMED. During a pop/push transition the leaving screen is still on screen and tappable;
 * a second Back tap there would pop the next entry too (Home), leaving an empty back stack = blank screen.
 */
private fun NavHostController.popIfResumed() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) popBackStack()
}


@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    startViewModel: AppStartViewModel = hiltViewModel(),
) {
    val startInputs by startViewModel.startInputs.collectAsStateWithLifecycle()
    val inputs = startInputs
    if (inputs == null) {
        // System splash is held until this resolves (MainActivity); cream frame is only a safety net.
        Box(modifier.fillMaxSize().background(GameColors.ScreenBackground))
        return
    }

    val navController = rememberNavController()
    // Restored back stack (process death) has no splash, so consent + ads init would never run: run them once when resumed.
    val startupRecovery: StartupRecoveryViewModel = hiltViewModel()
    LifecycleResumeEffect(navController, startupRecovery) {
        startupRecovery.onResumed(navController.currentBackStackEntry?.destination?.route)
        onPauseOrDispose { }
    }
    // screen_view per destination change. The VM dedupes, so rotation (listener re-attach) does not re-log.
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            startViewModel.onDestinationChanged(destination.route)
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
    NavHost(navController = navController, startDestination = Routes.SPLASH, modifier = modifier) {
        composable(Routes.SPLASH) {
            SplashRoute(
                onFinished = { navController.goTo(StartFlow.first(inputs), from = Routes.SPLASH) },
            )
        }
        composable(Routes.WELCOME) {
            WelcomeRoute(
                onAccepted = { navController.goTo(StartFlow.after(StartStep.WELCOME, inputs), from = Routes.WELCOME) },
            )
        }
        composable(Routes.NOTIFICATION) {
            NotificationPromptRoute(
                onFinished = {
                    navController.goTo(StartFlow.after(StartStep.NOTIFICATION, inputs), from = Routes.NOTIFICATION)
                },
            )
        }
        composable(Routes.TUTORIAL) {
            TutorialRoute(
                onFinished = {
                    // Replay from Settings (How to Play): just return there. First run: continue the start flow.
                    if (navController.previousBackStackEntry?.destination?.route == Routes.SETTINGS) {
                        navController.popIfResumed()
                    } else {
                        navController.goTo(StartFlow.after(StartStep.TUTORIAL, inputs), from = Routes.TUTORIAL)
                    }
                },
            )
        }
        composable(Routes.HOME) {
            HomeRoute(
                onPlayLevel = { level -> navController.navigate(Routes.gameLevel(level)) },
                onPlayDaily = { navController.navigate(Routes.DAILY) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                onOpenStreak = { navController.navigate(Routes.STREAK) { launchSingleTop = true } },
                onOpenShop = { navController.navigate(Routes.SHOP) { launchSingleTop = true } },
                onOpenSudoku = { navController.navigate(Routes.SUDOKU) { launchSingleTop = true } },
            )
        }
        composable(Routes.SUDOKU) {
            SudokuEntryRoute(
                onBack = { navController.popIfResumed() }, // back button: pop to Levels Home
                onOpenGame = { tierId, resume -> navController.navigate(Routes.sudokuGame(tierId, resume)) { launchSingleTop = true } },
                // First play of a size: its tutorial first, then the game replaces the tutorial entry (back from the game = entry).
                onOpenTutorial = { sizeId, tierId, resume ->
                    navController.navigate(Routes.sudokuTutorial(sizeId, tierId, resume)) { launchSingleTop = true }
                },
                onOpenShop = { navController.navigate(Routes.SHOP) { launchSingleTop = true } },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
            )
        }
        composable(
            route = Routes.SUDOKU_TUTORIAL_PATTERN,
            arguments = listOf(
                navArgument(Routes.ARG_SUDOKU_TUTORIAL_SIZE) { type = NavType.StringType },
                navArgument(Routes.ARG_SUDOKU_TUTORIAL_NEXT) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(Routes.ARG_SUDOKU_TUTORIAL_RESUME) {
                    type = NavType.BoolType
                    defaultValue = true
                },
            ),
        ) { entry ->
            val nextTier = entry.arguments?.getString(Routes.ARG_SUDOKU_TUTORIAL_NEXT).orEmpty()
            val resume = entry.arguments?.getBoolean(Routes.ARG_SUDOKU_TUTORIAL_RESUME) ?: true
            SudokuTutorialRoute(
                onFinished = {
                    if (nextTier.isEmpty()) {
                        navController.popIfResumed()
                    } else if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
                        navController.navigate(Routes.sudokuGame(nextTier, resume)) {
                            popUpTo(Routes.SUDOKU_TUTORIAL_PATTERN) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
            )
        }
        composable(
            route = Routes.SUDOKU_GAME_PATTERN,
            arguments = listOf(
                navArgument(Routes.ARG_SUDOKU_TIER) { type = NavType.StringType },
                navArgument(Routes.ARG_SUDOKU_RESUME) {
                    type = NavType.BoolType
                    defaultValue = true
                },
            ),
        ) { entry ->
            SudokuGameRoute(
                onBack = { navController.popIfResumed() },
                onOpenShop = { booster -> navController.navigate(Routes.shopFor(booster)) { launchSingleTop = true } },
                onOpenTutorial = { sizeId ->
                    navController.navigate(Routes.sudokuTutorial(sizeId)) { launchSingleTop = true }
                },
            )
        }
        composable(Routes.STREAK) {
            StreakRoute(onBack = { navController.popIfResumed() })
        }
        composable(
            route = Routes.GAME_PATTERN,
            arguments = listOf(
                navArgument(GameViewModel.ARG_DAILY) {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument(GameViewModel.ARG_LEVEL) {
                    type = NavType.IntType
                    defaultValue = 0
                },
            ),
        ) { entry ->
            // Back (system or top bar) pops to Home for both a level and the daily.
            GameRoute(
                onBack = { navController.popIfResumed() },
                // Booster dialog "Get X in Store". The game entry stays on the back stack (state kept); its lifecycle
                // drops to CREATED while Shop is on top, so the game clock pauses (ON_PAUSE) and resumes on return.
                onOpenShop = { booster -> navController.navigate(Routes.shopFor(booster)) { launchSingleTop = true } },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsRoute(
                onBack = { navController.popIfResumed() },
                onHowToPlay = { navController.navigate(Routes.TUTORIAL) { launchSingleTop = true } },
                onAbout = { navController.navigate(Routes.ABOUT) { launchSingleTop = true } },
            )
        }
        composable(Routes.ABOUT) {
            AboutRoute(onBack = { navController.popIfResumed() })
        }
        composable(
            route = Routes.SHOP_PATTERN,
            arguments = listOf(
                navArgument(Routes.ARG_SHOP_SECTION) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            ShopRoute(
                initialSection = ShopUiMapper.boosterFromArg(entry.arguments?.getString(Routes.ARG_SHOP_SECTION)),
                onBack = { navController.popIfResumed() },
            )
        }
    }
}
