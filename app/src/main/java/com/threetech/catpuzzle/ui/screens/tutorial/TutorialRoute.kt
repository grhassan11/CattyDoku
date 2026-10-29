package com.threetech.catpuzzle.ui.screens.tutorial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.threetech.catpuzzle.ui.screens.game.HapticKind
import com.threetech.catpuzzle.ui.components.LocalAudioController

/**
 * The only place that touches [TutorialViewModel]: collects state + events, then hands plain state
 * and lambdas to the stateless [TutorialScreen]. [onFinished] runs after `tutorial_done` is saved
 * (finish or skip).
 */
@Composable
fun TutorialRoute(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TutorialViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val audio = LocalAudioController.current
    val currentOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TutorialUiEvent.Haptic -> haptics.performHapticFeedback(
                    when (event.kind) {
                        HapticKind.TICK -> HapticFeedbackType.TextHandleMove
                        HapticKind.PLACE, HapticKind.WRONG, HapticKind.WIN, HapticKind.LOSE ->
                            HapticFeedbackType.LongPress
                    },
                )
                is TutorialUiEvent.Sfx -> audio?.playSfx(event.kind)
                TutorialUiEvent.Finished -> currentOnFinished()
            }
        }
    }

    TutorialScreen(
        state = state,
        onCellTap = viewModel::onCellTap,
        onCellDoubleTap = viewModel::onCellDoubleTap,
        onDrag = viewModel::onDrag,
        onHint = viewModel::onHint,
        onCatBooster = viewModel::onCatBooster,
        onContinue = viewModel::onContinue,
        onSkip = viewModel::onSkip,
        modifier = modifier,
    )
}
