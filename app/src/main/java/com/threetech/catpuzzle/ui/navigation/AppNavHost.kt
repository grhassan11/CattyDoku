package com.threetech.catpuzzle.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.domain.startup.StartFlow
import com.threetech.catpuzzle.domain.startup.StartStep
import com.threetech.catpuzzle.ui.screens.game.GameRoute
import com.threetech.catpuzzle.ui.screens.game.GameViewModel
import com.threetech.catpuzzle.ui.screens.home.HomeRoute
import com.threetech.catpuzzle.ui.screens.home.StreakRoute
import com.threetech.catpuzzle.ui.screens.nodegallery.NodeGalleryScreen // TEMP node gallery
import com.threetech.catpuzzle.ui.screens.notification.NotificationPromptRoute
import com.threetech.catpuzzle.ui.screens.settings.SettingsRoute
import com.threetech.catpuzzle.ui.screens.shop.ShopRoute
import com.threetech.catpuzzle.ui.screens.shop.ShopUiMapper
import com.threetech.catpuzzle.ui.screens.splash.SplashRoute
import com.threetech.catpuzzle.ui.screens.tutorial.TutorialRoute
import com.threetech.catpuzzle.ui.screens.welcome.WelcomeRoute
import com.threetech.catpuzzle.ui.theme.GameColors

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

    /** Graph pattern: optional `daily` arg, default false, so plain "game" still resolves. */
    internal const val GAME_PATTERN = "game?daily={daily}"
    const val TUTORIAL = "tutorial"

    /** Full-screen Daily Streak (opened from the Home streak chip). */
    const val STREAK = "streak"

    /** Shop. Optional `section` arg (cat / hint / mouse) lands on that pack section. Plain "shop" = top. */
    const val SHOP = "shop"
    internal const val SHOP_PATTERN = "shop?section={section}"
    internal const val ARG_SHOP_SECTION = "section"

    /** Full-screen Settings (from Home only; in a game, Settings is a dialog on the game screen). */
    const val SETTINGS = "settings"

    /** TEMP design preview (node styles). Delete with ui/screens/nodegallery/. */
    const val NODE_GALLERY = "node_gallery"

    /** TEMP 9x9 sample: game screen in in-memory sample mode (arg `sample`, read by GameViewModel). Gallery only. */
    const val SAMPLE_9X9 = "game_sample?sample=true"
    internal const val SAMPLE_9X9_PATTERN = "game_sample?sample={sample}"

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
                    navController.goTo(StartFlow.after(StartStep.TUTORIAL, inputs), from = Routes.TUTORIAL)
                },
            )
        }
        composable(Routes.HOME) {
            HomeRoute(
                onPlay = { navController.navigate(Routes.GAME) },
                onPlayDaily = { navController.navigate(Routes.DAILY) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                onOpenStreak = { navController.navigate(Routes.STREAK) { launchSingleTop = true } },
                onOpenShop = { navController.navigate(Routes.SHOP) { launchSingleTop = true } },
                onOpenAvatar = { navController.navigate(Routes.NODE_GALLERY) { launchSingleTop = true } }, // TEMP node gallery
            )
        }
        composable(Routes.NODE_GALLERY) { // TEMP node gallery
            NodeGalleryScreen(
                onBack = { navController.popBackStack() },
                onPlay9x9Sample = { navController.navigate(Routes.SAMPLE_9X9) { launchSingleTop = true } }, // TEMP 9x9 sample
            )
        }
        composable( // TEMP 9x9 sample
            route = Routes.SAMPLE_9X9_PATTERN,
            arguments = listOf(
                navArgument(GameViewModel.ARG_SAMPLE) {
                    type = NavType.BoolType
                    defaultValue = true
                },
            ),
        ) {
            GameRoute(
                onBack = { navController.popBackStack() },
                onOpenShop = {},
                sample = true,
            )
        }
        composable(Routes.STREAK) {
            StreakRoute(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.GAME_PATTERN,
            arguments = listOf(
                navArgument(GameViewModel.ARG_DAILY) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { entry ->
            // Back (system or top bar) pops to Home for both a level and the daily.
            GameRoute(
                onBack = { navController.popBackStack() },
                // Booster dialog "Get X in Store". The game entry stays on the back stack (state kept); its lifecycle
                // drops to CREATED while Shop is on top, so the game clock pauses (ON_PAUSE) and resumes on return.
                onOpenShop = { booster -> navController.navigate(Routes.shopFor(booster)) { launchSingleTop = true } },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsRoute(onBack = { navController.popBackStack() })
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
                onBack = { navController.popBackStack() },
            )
        }
    }
}
