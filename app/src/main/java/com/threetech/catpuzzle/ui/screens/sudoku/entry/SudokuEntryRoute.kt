package com.threetech.catpuzzle.ui.screens.sudoku.entry

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.threetech.catpuzzle.R

/**
 * The only place that touches [SudokuEntryViewModel]: collects state + events, then hands plain state and lambdas to the
 * stateless [SudokuEntryScreen]. [onBack] = system back and the Levels tab (both return to Home).
 * [onOpenGame] gets (SudokuTier.name, resume).
 */
@Composable
fun SudokuEntryRoute(
    onBack: () -> Unit,
    onOpenGame: (tierId: String, resume: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SudokuEntryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.refresh() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SudokuEntryEvent.Locked -> {
                    val text = context.getString(
                        R.string.sudoku_locked_toast,
                        event.winsNeeded,
                        context.getString(event.requiredTier.labelRes()),
                        context.getString(event.tier.labelRes()),
                        event.winsHave,
                    )
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(text)
                }
                is SudokuEntryEvent.OpenGame -> onOpenGame(event.tier.name, event.resume)
            }
        }
    }

    SudokuEntryScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onLevelsTab = onBack,
        onSelectTier = viewModel::onSelect,
        onContinue = viewModel::onContinue,
        onNewGame = viewModel::onNewGame,
        onConfirmNewGame = viewModel::onConfirmNewGame,
        onDismissConfirm = viewModel::onDismissConfirm,
        modifier = modifier,
    )
}
