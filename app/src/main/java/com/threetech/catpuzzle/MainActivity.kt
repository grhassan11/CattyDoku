package com.threetech.catpuzzle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.CompositionLocalProvider
import com.threetech.catpuzzle.audio.AudioController
import com.threetech.catpuzzle.ui.components.LocalAudioController
import com.threetech.catpuzzle.ui.navigation.AppNavHost
import com.threetech.catpuzzle.ui.navigation.AppStartViewModel
import com.threetech.catpuzzle.ui.theme.CatTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Same instance AppNavHost gets via hiltViewModel() (both are activity-scoped, default key).
    private val startViewModel: AppStartViewModel by viewModels()

    /** App-wide singleton (started in CatPuzzleApp); exposed to composables through [LocalAudioController]. */
    @Inject lateinit var audioController: AudioController

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate: swaps Theme.CatPuzzle.Splash for Theme.CatPuzzle afterwards.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Hold the system splash (icon only) until the start inputs are read (a few ms), then the nav graph
        // opens straight on the in-app splash: no blank frame, no fake delay, no double splash.
        splash.setKeepOnScreenCondition { startViewModel.startInputs.value == null }
        // Status bar icons always dark (light bar style), regardless of system dark mode. Nav bar stays default.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        setContent {
            CompositionLocalProvider(LocalAudioController provides audioController) {
                CatTheme {
                    AppNavHost()
                }
            }
        }
    }
}
