package com.tm.meow.sudoku.puzzle.ui.screens.sudoku.entry

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tm.meow.sudoku.puzzle.R
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuSize
import com.tm.meow.sudoku.puzzle.ui.ads.SudokuHomeAdsViewModel
import com.tm.meow.sudoku.puzzle.ui.screens.sudoku.tutorial.SudokuTutorialGateViewModel

/**
 * The only place that touches [SudokuEntryViewModel]: collects state + events, then hands plain state and lambdas to the
 * stateless [SudokuEntryScreen]. [onBack] = top-left back button (system back pops by default); both return to Levels Home.
 * Bottom native ad: [SudokuHomeAdsViewModel] (own remote switch). [onOpenGame] gets (SudokuTier.name, resume). [onOpenTutorial] gets (SudokuSize.name, SudokuTier.name, resume): it runs instead of [onOpenGame] the first time a board size is played (that size's tutorial flag unset) and opens the game when the tutorial ends.
 */
@Composable
fun SudokuEntryRoute(
    onBack: () -> Unit,
    onOpenGame: (tierId: String, resume: Boolean) -> Unit,
    onOpenTutorial: (sizeId: String, tierId: String, resume: Boolean) -> Unit,
    onOpenShop: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SudokuEntryViewModel = hiltViewModel(),
    adsViewModel: SudokuHomeAdsViewModel = hiltViewModel(),
    gateViewModel: SudokuTutorialGateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nativeAdSlot by adsViewModel.slot.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val openTutorial by rememberUpdatedState(onOpenTutorial)
    val openGame by rememberUpdatedState(onOpenGame)

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        viewModel.refresh()
        adsViewModel.onScreenStart()
    }
    // Leaving composition (game opened, back to Home): destroy the ad; ON_START loads a fresh one on return.
    DisposableEffect(adsViewModel) { onDispose { adsViewModel.release() } }

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
                // First play of this board size: its tutorial first (skippable), then the game. Leaving the tutorial in any way
                // sets that size's flag, so this cannot loop.
                is SudokuEntryEvent.OpenGame -> {
                    val size: SudokuSize = event.tier.size
                    if (gateViewModel.shouldShow(size)) {
                        openTutorial(size.name, event.tier.name, event.resume)
                    } else {
                        openGame(event.tier.name, event.resume)
                    }
                }
            }
        }
    }

    SudokuEntryScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = {
            viewModel.onLevelsTab()
            onBack()
        },
        onOpenShop = onOpenShop,
        onOpenSettings = onOpenSettings,
        onSelectTier = viewModel::onSelect,
        onContinue = viewModel::onContinue,
        onNewGame = viewModel::onNewGame,
        onConfirmNewGame = viewModel::onConfirmNewGame,
        onDismissConfirm = viewModel::onDismissConfirm,
        modifier = modifier,
        nativeAdSlot = nativeAdSlot,
    )
}
