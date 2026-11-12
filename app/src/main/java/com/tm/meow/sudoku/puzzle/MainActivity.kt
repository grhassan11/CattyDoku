package com.tm.meow.sudoku.puzzle

import android.os.Bundle
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.tm.meow.sudoku.puzzle.audio.AudioController
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveSettings
import com.tm.meow.sudoku.puzzle.ui.components.LocalAudioController
import com.tm.meow.sudoku.puzzle.ui.components.LocalReduceEffects
import com.tm.meow.sudoku.puzzle.ui.navigation.AppNavHost
import com.tm.meow.sudoku.puzzle.ui.navigation.AppStartViewModel
import com.tm.meow.sudoku.puzzle.ui.theme.CatTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() { // AppCompat: per-app language recreation on API < 33 (Theme.CatPuzzle is AppCompat-derived)
    // Same instance AppNavHost gets via hiltViewModel() (both are activity-scoped, default key).
    private val startViewModel: AppStartViewModel by viewModels()

    /** App-wide singleton (started in CatPuzzleApp); exposed to composables through [LocalAudioController]. */
    @Inject lateinit var audioController: AudioController

    /** Live "Reduce effects" setting for [LocalReduceEffects] (cosmetic only). */
    @Inject lateinit var observeSettings: ObserveSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate: swaps Theme.CatPuzzle.Splash for Theme.CatPuzzle afterwards.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Hold the system splash (icon only) until the start inputs are read (a few ms), then the nav graph
        // opens straight on the in-app splash: no blank frame, no fake delay, no double splash.
        // Hard cap: a stuck DataStore read can never hold the system splash (ANR / vitals risk). AppNavHost shows a cream frame until inputs resolve.
        val splashStartMs = SystemClock.elapsedRealtime()
        splash.setKeepOnScreenCondition {
            startViewModel.startInputs.value == null && SystemClock.elapsedRealtime() - splashStartMs < SPLASH_HARD_CAP_MS
        }
        // Status bar icons always dark (light bar style), regardless of system dark mode. Nav bar stays default.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        setContent {
            val reduceEffectsFlow = remember { observeSettings().map { it.reduceEffects }.distinctUntilChanged() }
            val reduceEffects by reduceEffectsFlow.collectAsState(initial = false)
            CompositionLocalProvider(
                LocalAudioController provides audioController,
                LocalReduceEffects provides reduceEffects,
            ) {
                CatTheme {
                    AppNavHost()
                }
            }
        }
    }

    private companion object {
        const val SPLASH_HARD_CAP_MS = 1_500L
    }
}
