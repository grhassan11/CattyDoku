package com.tm.meow.sudoku.puzzle

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.tm.meow.sudoku.puzzle.ui.theme.GameColors
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.tm.meow.sudoku.puzzle.audio.AudioController
import com.tm.meow.sudoku.puzzle.domain.analytics.NotificationType
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
        // Recreation (rotation, language change) keeps the original intent: only a fresh launch can be a tap.
        if (savedInstanceState == null) logNotificationOpen(intent)
        // Hold the system splash (icon only) until the start inputs are read (a few ms), then the nav graph
        // opens straight on the in-app splash: no blank frame, no fake delay, no double splash.
        // Hard cap: a stuck DataStore read can never hold the system splash (ANR / vitals risk). AppNavHost shows a cream frame until inputs resolve.
        val splashStartMs = SystemClock.elapsedRealtime()
        splash.setKeepOnScreenCondition {
            startViewModel.startInputs.value == null && SystemClock.elapsedRealtime() - splashStartMs < SPLASH_HARD_CAP_MS
        }
        // Status + nav bar icons always dark (light bar style), regardless of system dark mode; both bars transparent.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        // 3-button nav: system adds a gray contrast scrim unless enforcement is off (API 29+).
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            val reduceEffectsFlow = remember { observeSettings().map { it.reduceEffects }.distinctUntilChanged() }
            val reduceEffects by reduceEffectsFlow.collectAsStateWithLifecycle(initialValue = false)
            CompositionLocalProvider(
                LocalAudioController provides audioController,
                LocalReduceEffects provides reduceEffects,
            ) {
                CatTheme {
                    // Opaque root = window background colour: no black/empty frame while resources swap on a locale change.
                    Box(Modifier.fillMaxSize().background(GameColors.ScreenBackground)) {
                        AppNavHost()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        logNotificationOpen(intent)
    }

    /** One `notification_open` per tap: the extra is removed once read, so re-delivery of the same intent logs nothing. */
    private fun logNotificationOpen(intent: Intent?) {
        val type = intent?.getStringExtra(NotificationType.EXTRA_KEY) ?: return
        intent.removeExtra(NotificationType.EXTRA_KEY)
        startViewModel.onNotificationOpened(type)
    }

    private companion object {
        const val SPLASH_HARD_CAP_MS = 1_500L
    }
}
