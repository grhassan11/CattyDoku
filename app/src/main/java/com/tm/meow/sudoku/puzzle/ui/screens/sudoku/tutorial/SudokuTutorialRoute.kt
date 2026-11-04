package com.tm.meow.sudoku.puzzle.ui.screens.sudoku.tutorial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The only place that touches [SudokuTutorialViewModel]: collects state + events, then hands plain state and lambdas to
 * the stateless [SudokuTutorialScreen]. [onFinished] runs after the size's seen flag is saved (finish, skip or back).
 */
@Composable
fun SudokuTutorialRoute(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SudokuTutorialViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SudokuTutorialEvent.Finished -> currentOnFinished()
            }
        }
    }

    SudokuTutorialScreen(
        state = state,
        onExit = viewModel::onExit,
        onStartLesson = viewModel::onStartLesson,
        onCellTap = viewModel::onCellTap,
        onDigit = viewModel::onDigit,
        onNextLesson = viewModel::onNextLesson,
        modifier = modifier,
    )
}
