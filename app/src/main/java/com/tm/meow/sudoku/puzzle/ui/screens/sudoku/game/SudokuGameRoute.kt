package com.tm.meow.sudoku.puzzle.ui.screens.sudoku.game

import android.widget.Toast
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tm.meow.sudoku.puzzle.R
import com.tm.meow.sudoku.puzzle.domain.ads.BannerPlacement
import com.tm.meow.sudoku.puzzle.domain.model.Booster
import com.tm.meow.sudoku.puzzle.ui.ads.AdsUiEvent
import com.tm.meow.sudoku.puzzle.ui.ads.AdsViewModel
import com.tm.meow.sudoku.puzzle.ui.components.LocalAudioController
import com.tm.meow.sudoku.puzzle.ui.screens.game.HapticKind

/**
 * The only place that touches [SudokuGameViewModel] / [AdsViewModel]: collects state + events, wires lifecycle, then hands
 * plain state and lambdas to the stateless [SudokuGameScreen].
 *
 * The tier comes from the nav arg through SavedStateHandle (the VM reads it); the VM resumes a save when there is one,
 * generates otherwise.
 * [onBack] pops to the Sudoku entry. [onOpenShop] opens the Shop on the matching pack section. [onOpenTutorial] opens the tutorial of the current board size ("?"; arg = SudokuSize.name).
 * Ads gating = the Queens one: AdsViewModel.surfaces uses the player's Queens level (this screen never sets a screen level).
 * Sound + haptic cues arrive as events already filtered by the settings; this route only performs them.
 */
@Composable
fun SudokuGameRoute(
    onBack: () -> Unit,
    onOpenShop: (Booster) -> Unit,
    onOpenTutorial: (sizeId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SudokuGameViewModel = hiltViewModel(),
    adsViewModel: AdsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val timeText by viewModel.timeText.collectAsStateWithLifecycle()
    val surfaces by adsViewModel.surfaces.collectAsStateWithLifecycle()
    val rewardedBusy by adsViewModel.rewardedBusy.collectAsStateWithLifecycle()
    val bannerAd by adsViewModel.banner.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    // App-wide audio (silenced during full-screen ads): provided in MainActivity. Sound / haptics settings are applied in the VM.
    val audio = LocalAudioController.current

    val noHintText = stringResource(R.string.booster_no_hint)
    val adUnavailableText = stringResource(R.string.reward_ad_unavailable)
    val rewardNotEarnedText = stringResource(R.string.reward_not_earned)
    val lockedText = stringResource(R.string.sudoku_game_locked)
    val failedText = stringResource(R.string.sudoku_game_failed)
    val rewardedAllowed by rememberUpdatedState(surfaces.rewarded)
    val rewardedBusyLatest by rememberUpdatedState(rewardedBusy)
    val onBackLatest by rememberUpdatedState(onBack)

    // PAUSE / RESUME (not STOP / START): a full-screen ad pauses the Activity without stopping it, so the clock holds then too.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onAppStopped() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onAppStarted() }

    // Keep a rewarded ad ready while the hint stock is empty, the out-of-hints dialog is open or the game-over dialog is open.
    val hintDialogOpen = state.dialog == SudokuDialogUi.HintNeeded
    val gameOverOpen = state.dialog is SudokuDialogUi.GameOver
    LaunchedEffect(surfaces.rewarded, state.hintCharges, hintDialogOpen, gameOverOpen, rewardedBusy) {
        if (surfaces.rewarded && !state.loading && (state.hintCharges == 0 || hintDialogOpen || gameOverOpen)) {
            adsViewModel.preloadRewardedAd()
        }
    }
    // Rewarded turned off underneath the open dialog (consent revoked etc): close it.
    LaunchedEffect(surfaces.rewarded, hintDialogOpen) {
        if (!surfaces.rewarded && hintDialogOpen) viewModel.onHintDialog(false)
    }

    // Sticky banner (Sudoku placement, no level gate): load when allowed, destroy when the surface turns off or on leave.
    val bannerWidthDp = LocalConfiguration.current.screenWidthDp
    LaunchedEffect(surfaces.sudokuBanner, bannerWidthDp) {
        if (surfaces.sudokuBanner) adsViewModel.requestBanner(bannerWidthDp, BannerPlacement.SUDOKU_GAME) else adsViewModel.releaseBanner()
    }
    DisposableEffect(adsViewModel) { onDispose { adsViewModel.releaseBanner() } }
    // No banner under dialogs (restart / win / game over / out of hints) or the pause panel: slot stays reserved, ad view detaches.
    val bannerVisible = state.dialog == SudokuDialogUi.None && !state.paused && !state.loading

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
                // Out of hints + rewarded allowed: ALWAYS show the out-of-hints dialog (same as Levels). The dialog's
                // Watch button runs the ad when READY (+1 hint ONLY on the earned-reward result); loading / failed / Shop live there too.
                SudokuGameEvent.NoHintCharge -> if (rewardedAllowed) {
                    if (!rewardedBusyLatest) viewModel.onHintDialog(true) // ad request already in flight: ignore the extra tap
                } else {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(noHintText)
                }
                is SudokuGameEvent.Sfx -> audio?.playSfx(event.kind)
                is SudokuGameEvent.Haptic -> haptics.performHapticFeedback(
                    when (event.kind) {
                        HapticKind.TICK -> HapticFeedbackType.TextHandleMove
                        HapticKind.CORRECT, HapticKind.PLACE, HapticKind.WRONG, HapticKind.WIN, HapticKind.LOSE -> HapticFeedbackType.LongPress
                    },
                )
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
        // "?" opens the tutorial: hold the clock + save first (same as the Store hop); ON_RESUME on return restarts it.
        onHelp = {
            viewModel.onAppStopped()
            onOpenTutorial(state.tier.size.name)
        },
        onRestartClick = viewModel::onRestartClick,
        onPauseToggle = viewModel::onPauseToggle,
        onCellTap = viewModel::onCellTap,
        onDigit = viewModel::onDigit,
        onUndo = viewModel::onUndo,
        onToggleNotes = viewModel::onToggleNotes,
        onErase = viewModel::onErase,
        onHint = viewModel::onHint,
        onConfirmRestart = viewModel::onRestart,
        onDismissDialog = viewModel::onDismissDialog,
        onRestart = viewModel::onRestart,
        onQuit = viewModel::onQuit,
        onWinNext = viewModel::onWinNext,
        onWinDone = viewModel::onWinDone,
        // Reward lands in inventory ONLY on the earned-reward result; the hint badge follows the inventory flow.
        onWatchHintAd = {
            adsViewModel.watchBoosterAd(Booster.HINT) { viewModel.onHintDialog(false) }
        },
        // Game-over continue: +1 heart and resume, ONLY on the earned-reward result. null hides the CTA (rewarded not allowed).
        onWatchRevive = if (surfaces.rewarded) {
            { adsViewModel.watchReviveAd(onGranted = viewModel::onRevive) }
        } else {
            null
        },
        onRetryRewardedLoad = adsViewModel::preloadRewardedAd,
        // Shop opens on the HINT section. Dialog closes, clock holds + saves; the badge refreshes from the inventory flow on return.
        onOpenStore = {
            viewModel.onHintDialog(false)
            viewModel.onAppStopped()
            onOpenShop(Booster.HINT)
        },
        modifier = modifier,
        bannerReserved = surfaces.sudokuBanner,
        bannerAd = if (bannerVisible) bannerAd else null,
    )
}
