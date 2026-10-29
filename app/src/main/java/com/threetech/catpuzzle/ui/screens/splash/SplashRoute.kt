package com.threetech.catpuzzle.ui.screens.splash

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * The only place that touches [SplashViewModel]. [onFinished] = consent + ads init done, or the hard cap hit.
 * The gate starts once the screen is RESUMED (the UMP form needs a resumed Activity); the call is idempotent.
 */
@Composable
fun SplashRoute(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    val currentOnFinished by rememberUpdatedState(onFinished)

    LifecycleResumeEffect(viewModel) {
        viewModel.start()
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SplashEvent.Finished -> currentOnFinished()
            }
        }
    }
    // Back on the splash is swallowed: leaving mid-consent would strand the gate. Splash is short and capped.
    BackHandler { }

    SplashScreen(modifier = modifier)
}
