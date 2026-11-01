package com.threetech.catpuzzle.ui.screens.sudoku.game

import android.widget.Toast
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.ui.ads.AdsUiEvent
import com.threetech.catpuzzle.ui.ads.AdsViewModel

/**
 * The only place that touches [SudokuGameViewModel] / [AdsViewModel]: collects state + events, wires lifecycle, then hands
 * plain state and lambdas to the stateless [SudokuGameScreen].
 *
 * The tier comes from the nav arg through SavedStateHandle (the VM reads it); [tierId] / [resume] stay in the signature for
 * the nav host but are not needed here (the VM resumes a save when there is one, generates otherwise).
 * [onBack] pops to the Sudoku entry. [onOpenShop] opens the Shop on the matching pack section.
 * Ads gating = the Queens one: AdsViewModel.surfaces uses the player's Queens level (this screen never sets a screen level).
 */
@Composable
fun SudokuGameRoute(
    @Suppress("UNUSED_PARAMETER") tierId: String,
    @Suppress("UNUSED_PARAMETER") resume: Boolean,
    onBack: () -> Unit,
    onOpenShop: (Booster) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SudokuGameViewModel = hiltViewModel(),
    adsViewModel: AdsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val timeText by viewModel.timeText.collectAsStateWithLifecycle()
    val surfaces by adsViewModel.surfaces.collectAsStateWithLifecycle()
    val rewardedBusy by adsViewModel.rewardedBusy.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val noHintText = stringResource(R.string.booster_no_hint)
    val adUnavailableText = stringResource(R.string.reward_ad_unavailable)
    val rewardNotEarnedText = stringResource(R.string.reward_not_earned)
    val lockedText = stringResource(R.string.sudoku_game_locked)
    val failedText = stringResource(R.string.sudoku_game_failed)
    val rewardedAllowed by rememberUpdatedState(surfaces.rewarded)
    val onBackLatest by rememberUpdatedState(onBack)

    // PAUSE / RESUME (not STOP / START): a full-screen ad pauses the Activity without stopping it, so the clock holds then too.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onAppStopped() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onAppStarted() }

    // Keep a rewarded ad ready while the hint stock is empty or the out-of-hints dialog is open.
    val hintDialogOpen = state.dialog == SudokuDialogUi.HintNeeded
    LaunchedEffect(surfaces.rewarded, state.hintCharges, hintDialogOpen, rewardedBusy) {
        if (surfaces.rewarded && !state.loading && (state.hintCharges == 0 || hintDialogOpen)) {
            adsViewModel.preloadRewardedAd()
        }
    }
    // Rewarded turned off underneath the open dialog (consent revoked etc): close it.
    LaunchedEffect(surfaces.rewarded, hintDialogOpen) {
        if (!surfaces.rewarded && hintDialogOpen) viewModel.onHintDialog(false)
    }

    LaunchedEffect(adsViewModel) {
        adsViewModel.events.collect { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                when (event) {
                    AdsUiEvent.RewardedUnavailable -> adUnavailableText
                    AdsUiEvent.RewardNotEarned -> rewardNotEarnedText
                },
            )
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SudokuGameEvent.NoHintCharge -> if (rewardedAllowed) {
                    viewModel.onHintDialog(true)
                } else {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(noHintText)
                }
                is SudokuGameEvent.Exit -> {
                    when (event.reason) {
                        SudokuExitReason.LOCKED -> Toast.makeText(context, lockedText, Toast.LENGTH_SHORT).show()
                        SudokuExitReason.FAILED -> Toast.makeText(context, failedText, Toast.LENGTH_SHORT).show()
                        SudokuExitReason.NONE -> Unit
                    }
                    onBackLatest()
                }
            }
        }
    }

    SudokuGameScreen(
        state = state,
        timeText = timeText,
        snackbarHostState = snackbarHostState,
        rewardedLoad = surfaces.rewardedLoad,
        rewardedBusy = rewardedBusy,
        onBack = viewModel::onBack,
        onHelp = viewModel::onHelpClick, // P3c: replace with the tutorial entry
        onRestartClick = viewModel::onRestartClick,
        onPauseToggle = viewModel::onPauseToggle,
        onCellTap = viewModel::onCellTap,
        onDigit = viewModel::onDigit,
        onUndo = viewModel::onUndo,
        onToggleNotes = viewModel::onToggleNotes,
        onErase = viewModel::onErase,
        onHint = viewModel::onHint,
        onConfirmRestart = viewModel::onRestartConfirmed,
        onDismissDialog = viewModel::onDismissDialog,
        onRetry = viewModel::onRetry,
        onQuit = viewModel::onQuit,
        onWinNext = viewModel::onWinNext,
        onWinDone = viewModel::onWinDone,
        // Reward lands in inventory ONLY on the earned-reward result; the hint badge follows the inventory flow.
        onWatchHintAd = {
            adsViewModel.watchBoosterAd(Booster.HINT) { viewModel.onHintDialog(false) }
        },
        onRetryRewardedLoad = adsViewModel::preloadRewardedAd,
        // Shop opens on the HINT section. Dialog closes, clock holds + saves; the badge refreshes from the inventory flow on return.
        onOpenStore = {
            viewModel.onHintDialog(false)
            viewModel.onAppStopped()
            onOpenShop(Booster.HINT)
        },
        modifier = modifier,
    )
}
