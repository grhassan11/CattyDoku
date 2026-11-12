package com.tm.meow.sudoku.puzzle.ui.screens.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.tm.meow.sudoku.puzzle.domain.ads.NativeAdSlotState
import com.tm.meow.sudoku.puzzle.domain.usecase.AdPlacement
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tm.meow.sudoku.puzzle.R
import com.tm.meow.sudoku.puzzle.domain.engine.IdleNudgePolicy
import com.tm.meow.sudoku.puzzle.domain.model.Booster
import com.tm.meow.sudoku.puzzle.ui.ads.AdsUiEvent
import com.tm.meow.sudoku.puzzle.ui.ads.AdsViewModel
import com.tm.meow.sudoku.puzzle.domain.analytics.QuitReason
import com.tm.meow.sudoku.puzzle.ui.components.LocalAudioController
import com.tm.meow.sudoku.puzzle.ui.screens.rate.RateUsHost
import com.tm.meow.sudoku.puzzle.ui.screens.rate.RateUsViewModel
import kotlinx.coroutines.launch

/**
 * The only place that touches the ViewModel: collects state + events, wires lifecycle, then hands
 * plain state and lambdas to the stateless [GameScreen].
 */
@Composable
fun GameRoute(
    onBack: () -> Unit,
    onOpenShop: (Booster) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GameViewModel = hiltViewModel(),
    adsViewModel: AdsViewModel = hiltViewModel(),
    rateUsViewModel: RateUsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val surfaces by adsViewModel.surfaces.collectAsStateWithLifecycle()
    val rewardedBusy by adsViewModel.rewardedBusy.collectAsStateWithLifecycle()
    val gameNative by adsViewModel.gameNative.collectAsStateWithLifecycle()
    // Current level / mode for the ad boundary (daily never shows an interstitial).
    val content = (state as? GameUiState.Ready)?.content
    val adLevel = content?.levelNumber ?: 0
    val adIsDaily = content?.isDaily ?: false
    // First composition: re-try a failed ads SDK init (no-op when up / no consent).
    LaunchedEffect(Unit) { adsViewModel.retryAdsInitOnEntry() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val noCatText by rememberUpdatedState(stringResource(PieceWording.boosterNone(content?.pieceSkin.word())))
    val noHintText = stringResource(R.string.booster_no_hint)
    val noMouseText = stringResource(R.string.booster_no_mouse)
    val adUnavailableText = stringResource(R.string.reward_ad_unavailable)
    val rewardNotEarnedText = stringResource(R.string.reward_not_earned)
    val context = LocalContext.current
    // "Need one?" rewarded dialog for an EMPTY booster (null = closed).
    var boosterDialog by rememberSaveable { mutableStateOf<Booster?>(null) }
    // Set when the Shop was opened from here: on return the game re-reads inventory (packs may have been bought).
    var shopOpened by rememberSaveable { mutableStateOf(false) }
    // In-game Settings dialog (overlay on this screen, not a navigation). Open = clock paused.
    var settingsVisible by rememberSaveable { mutableStateOf(false) }
    // "Next level" action waiting for the Rate us dialog to close (not saved: lost on process death, then the player taps again).
    var pendingNextLevel by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Transient wrong-move cue (animation only; not saved, so rotation / process death never replays it).
    var wrongMove by remember { mutableStateOf<WrongMoveUi?>(null) }
    var wrongMoveSeq by remember { mutableIntStateOf(0) }
    val latestBoard = rememberUpdatedState(content?.board)

    // Idle nudge: screen resumed flag (paused for Settings / Shop / ads / app stop) + press/release clock.
    var resumed by remember { mutableStateOf(true) }
    val interactionClock = remember { InteractionClock() }
    val nudgeEligible = IdleNudgePolicy.screenEligible(
        playable = content != null && content.outcome == null,
        overlayOpen = content?.hintOverlay != null || boosterDialog != null || settingsVisible,
        resumed = resumed,
    )
    val nudging = rememberIdleNudge(nudgeEligible, interactionClock)

    // App-wide audio (music + SFX, silenced during full-screen ads): provided in MainActivity.
    val audio = LocalAudioController.current
    // Idle meow: same eligibility as the nudge, minus a running mouse flight. Sound toggle + ad silence live in the controller.
    RememberIdleMeow(nudgeEligible && content?.mouseRun == null, interactionClock) { audio?.playIdleMeow() }

    // ---- ads wiring: all policy lives in domain; `surfaces` already folds in ad-free levels, consent, kill switches ----
    LaunchedEffect(adLevel) { adsViewModel.setScreenLevel(adLevel) }
    // Keep an interstitial loaded for the win / retry boundary: on entry and whenever level / consent / SDK state changes.
    // Idempotent (no double-load); ad-free levels, consent, SDK, Remove Ads are gated in the use case.
    LaunchedEffect(adLevel, surfaces) { adsViewModel.preloadInterstitialAd(isSudoku = false) }

    // Keep a rewarded ad preloaded while any booster is empty, on the last heart, and when the lose dialog shows.
    val boosters = content?.boosters
    val anyBoosterEmpty = boosters != null && (boosters.catCharges == 0 || boosters.hintCharges == 0 || (!boosters.thirdLocked && boosters.mouseCharges == 0))
    val lastHeart = content != null && content.outcome == null && content.hearts <= 1
    val lostShown = content?.outcome is OutcomeUi.Lost
    LaunchedEffect(surfaces.levelsBoosters, surfaces.levelsRevive, anyBoosterEmpty, lastHeart, lostShown, rewardedBusy, boosterDialog) {
        val want = (surfaces.levelsBoosters && (anyBoosterEmpty || boosterDialog != null)) ||
            (surfaces.levelsRevive && (lastHeart || lostShown))
        if (want) adsViewModel.preloadRewardedAd()
    }
    // Boosters rewarded turned off underneath an open dialog (e.g. consent revoked): close it.
    LaunchedEffect(surfaces.levelsBoosters) { if (!surfaces.levelsBoosters) boosterDialog = null }

    // Compact native slot (driven by levels_game_native_ad_show): load when allowed, destroy when the surface turns off.
    LaunchedEffect(surfaces.levelsGameNative) {
        if (surfaces.levelsGameNative) adsViewModel.requestGameNative(AdPlacement.LEVELS_GAME) else adsViewModel.releaseGameNative()
    }
    // No native ad under dialogs (win / lose / booster): the slot stays reserved, the ad view detaches.
    val nativeVisible = content?.outcome == null && boosterDialog == null && !settingsVisible
    val adsUi = GameAdsUi(
        rewardedSurface = surfaces.levelsBoosters,
        reviveSurface = surfaces.levelsRevive,
        rewardedLoad = surfaces.rewardedLoad,
        rewardedBusy = rewardedBusy,
        nativeReserved = surfaces.levelsGameNative,
        nativeSlot = if (nativeVisible) gameNative else NativeAdSlotState.Hidden,
    )

    LaunchedEffect(adsViewModel) {
        adsViewModel.events.collect { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            launch {
                snackbarHostState.showSnackbar(
                    when (event) {
                        AdsUiEvent.RewardedUnavailable -> adUnavailableText
                        AdsUiEvent.RewardNotEarned -> rewardNotEarnedText
                    },
                )
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is GameUiEvent.Haptic -> haptics.performHapticFeedback(
                    when (event.kind) {
                        // Distinct feel per outcome. Confirm / Reject use the platform constants on API 30+; Compose falls
                        // back to a plain click on older devices. Whole thing is skipped when the haptics setting is off
                        // (the ViewModel never emits Haptic then).
                        HapticKind.TICK -> HapticFeedbackType.TextHandleMove
                        HapticKind.CORRECT -> HapticFeedbackType.Confirm
                        HapticKind.PLACE -> HapticFeedbackType.ContextClick
                        HapticKind.WRONG, HapticKind.LOSE -> HapticFeedbackType.Reject
                        HapticKind.WIN -> HapticFeedbackType.LongPress
                    },
                )
                is GameUiEvent.Sfx -> audio?.playSfx(event.kind, event.rate)
                is GameUiEvent.NoCharges -> scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(
                        when (event.booster) {
                            Booster.CAT -> noCatText
                            Booster.HINT -> noHintText
                            Booster.MOUSE -> noMouseText
                        },
                    )
                }
                // Win counts toward the every-Nth-win ad rule (daily ignored: its key is <= 0). The ad itself
                // only ever shows later, when the user taps "Level N+1".
                is GameUiEvent.LevelWon -> adsViewModel.onLevelWon(isDaily = event.level <= 0)
                is GameUiEvent.LevelLost -> Unit // dialogs render from state.outcome
                // Visual-only: hand the transient cue to the board (it plays once per token, skips if animations are off).
                // Touching violation: cats in the 8-neighbourhood of the wrong cell (read from the board the UI already shows) shake
                // and pulse red too. No partners = plain wrong move, unchanged.
                is GameUiEvent.WrongMove -> {
                    val board = latestBoard.value
                    val partners = if (board != null && event.cells.size == 1) {
                        WrongMoveLogic.touchingCats(board.size, event.cells.first()) { board.cells[it].mark == CellMarkUi.CAT }
                    } else {
                        emptySet()
                    }
                    wrongMove = WrongMoveUi(++wrongMoveSeq, event.cells, partners)
                }
            }
        }
    }

    // PAUSE / RESUME (not STOP / START): a full-screen ad (rewarded, interstitial, app-open) pauses the Activity without
    // stopping it, so the game clock now stops while any ad is on screen.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        resumed = false
        viewModel.onAppStopped()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        resumed = true
        // Settings dialog still open (e.g. back from an ad / app switch): clock stays paused until it closes.
        if (!settingsVisible) viewModel.onAppStarted()
        if (shopOpened) {
            shopOpened = false
            viewModel.onBoosterRewardGranted() // mirrors the persisted inventory into the running game
        }
    }

    Box(modifier.fillMaxSize().trackInteractions(interactionClock)) {
    GameScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        // Top-bar back / dialog home: log a quit if an attempt is still open, then leave (system back is caught by onCleared).
        onBack = {
            viewModel.onUserLeft(QuitReason.TOP_BAR_BACK)
            onBack()
        },
        onWinHome = {
            audio?.stopSfx() // silence the win / award cue before leaving
            onBack()
        },
        settingsVisible = settingsVisible,
        onOpenSettings = {
            // Toggle: pause + save while the dialog is open, resume on close.
            if (settingsVisible) {
                settingsVisible = false
                resumed = true
                viewModel.onAppStarted()
            } else {
                settingsVisible = true
                resumed = false
                viewModel.onAppStopped()
            }
        },
        onDismissSettings = {
            settingsVisible = false
            resumed = true
            viewModel.onAppStarted()
        },
        onSettingChanged = viewModel::onSettingChanged,
        onCellTap = viewModel::onCellTap,
        onCellDoubleTap = viewModel::onCellDoubleTap,
        onDrag = viewModel::onDrag,
        onDragCross = viewModel::onDragCross,
        onUndo = viewModel::onUndo,
        onHint = viewModel::onHint,
        onHintOverlayDismiss = viewModel::onHintOverlayDismiss,
        nudgeActive = { nudging.value },
        onCatBooster = viewModel::onCatBooster,
        onMouseBooster = viewModel::onMouseBooster,
        onMouseAnimDone = viewModel::onMouseAnimDone,
        wrongMove = wrongMove,
        onRetryLoad = viewModel::onRetryLoad,
        // User-initiated boundary: maybe an interstitial BEFORE navigating (ad-free through Lv 9), then ALWAYS
        // continue (fail open).
        // The one-time Rate us dialog (after Lv 5 is won) goes FIRST, then the normal ad boundary + next level.
        onNextLevel = {
            audio?.stopSfx() // silence the win / award cue now, before the rate dialog, ad or next level starts
            val proceed = { adsViewModel.afterWin(adLevel, adIsDaily, onDone = viewModel::onNextLevel) }
            if (adIsDaily) {
                proceed()
            } else {
                scope.launch {
                    if (rateUsViewModel.tryShow()) pendingNextLevel = proceed else proceed()
                }
            }
        },
        // Retry boundary: interstitial gated by levels_game_restarted_interstitial_ad_show (default on) + the pacing guards.
        // Revive is a separate rewarded CTA.
        onLoseRetry = { adsViewModel.beforeRetry(adLevel, adIsDaily, onDone = viewModel::onRestart) },
        modifier = Modifier.fillMaxSize(),
        ads = adsUi,
        boosterDialog = boosterDialog,
        onEmptyBoosterTap = { b -> boosterDialog = b },
        onDismissBoosterDialog = { boosterDialog = null },
        // Reward lands in inventory ONLY on the earned-reward result; then the game mirrors the new stock.
        onWatchBoosterAd = { booster ->
            adsViewModel.watchBoosterAd(booster) {
                boosterDialog = null
                viewModel.onBoosterRewardGranted()
            }
        },
        onRetryRewardedLoad = adsViewModel::preloadRewardedAd,
        // Shop opens on the matching pack section. The dialog closes so it is not waiting on return. The game timer
        // pauses with the screen (ON_PAUSE above, plus the explicit pause below) and the board state stays in the ViewModel.
        onOpenStore = { booster ->
            boosterDialog = null
            shopOpened = true
            // Pause + save explicitly: the game screen can leave composition before its ON_PAUSE reaches the effect above.
            resumed = false
            viewModel.onAppStopped()
            onOpenShop(booster)
        },
        // Revive: +1 life and continue the same level, only after the earned-reward result.
        onWatchReviveAd = { adsViewModel.watchReviveAd(onGranted = viewModel::onRevive) },
    )
    // Rate us dialog on top of the win dialog; closing it (submit / Not now / back) continues to the next level.
    RateUsHost(
        viewModel = rateUsViewModel,
        snackbarHostState = snackbarHostState,
        onClosed = {
            pendingNextLevel?.let { next ->
                pendingNextLevel = null
                next()
            }
        },
    )
    }
}
