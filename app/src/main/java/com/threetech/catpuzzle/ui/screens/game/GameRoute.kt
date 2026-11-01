package com.threetech.catpuzzle.ui.screens.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
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
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.domain.engine.IdleNudgePolicy
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.ui.ads.AdsUiEvent
import com.threetech.catpuzzle.ui.ads.AdsViewModel
import com.threetech.catpuzzle.ui.components.LocalAudioController
import com.threetech.catpuzzle.ui.screens.rate.RateUsHost
import com.threetech.catpuzzle.ui.screens.rate.RateUsViewModel
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
    sample: Boolean = false, // TEMP 9x9 sample: no ads / rate-us / progress side effects
    viewModel: GameViewModel = hiltViewModel(),
    adsViewModel: AdsViewModel = hiltViewModel(),
    rateUsViewModel: RateUsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val surfaces by adsViewModel.surfaces.collectAsStateWithLifecycle()
    val rewardedBusy by adsViewModel.rewardedBusy.collectAsStateWithLifecycle()
    val bannerAd by adsViewModel.banner.collectAsStateWithLifecycle()
    // Current level / mode for the ad boundary (daily never shows an interstitial).
    val content = (state as? GameUiState.Ready)?.content
    val adLevel = if (sample) 0 else content?.levelNumber ?: 0 // TEMP 9x9 sample
    val adIsDaily = content?.isDaily ?: false
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val noCatText = stringResource(R.string.booster_no_cat)
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
    LaunchedEffect(adLevel) { if (!sample) adsViewModel.setScreenLevel(adLevel) } // TEMP 9x9 sample

    // Keep a rewarded ad preloaded while any booster is empty, on the last heart, and when the lose dialog shows.
    val boosters = content?.boosters
    val anyBoosterEmpty = boosters != null && (boosters.catCharges == 0 || boosters.hintCharges == 0 || (!boosters.thirdLocked && boosters.mouseCharges == 0))
    val lastHeart = content != null && content.outcome == null && content.hearts <= 1
    val lostShown = content?.outcome is OutcomeUi.Lost
    LaunchedEffect(surfaces.rewarded, anyBoosterEmpty, lastHeart, lostShown, rewardedBusy, boosterDialog) {
        if (!sample && surfaces.rewarded && (anyBoosterEmpty || lastHeart || lostShown || boosterDialog != null)) {
            adsViewModel.preloadRewardedAd()
        }
    }
    // Rewarded turned off underneath an open dialog (e.g. consent revoked): close it.
    LaunchedEffect(surfaces.rewarded) { if (!surfaces.rewarded) boosterDialog = null }

    // Sticky banner: load when allowed, destroy when the surface turns off. Slot width = screen width.
    val bannerWidthDp = LocalConfiguration.current.screenWidthDp
    LaunchedEffect(surfaces.banner, bannerWidthDp) {
        if (!sample && surfaces.banner) adsViewModel.requestBanner(bannerWidthDp) else adsViewModel.releaseBanner() // TEMP 9x9 sample
    }
    // No banner under dialogs (win / lose / booster): the slot stays reserved, the ad view detaches.
    val bannerVisible = content?.outcome == null && boosterDialog == null && !settingsVisible
    val adsUi = if (sample) GameAdsUi() else GameAdsUi( // TEMP 9x9 sample: all ad surfaces off
        rewardedSurface = surfaces.rewarded,
        rewardedLoad = surfaces.rewardedLoad,
        rewardedBusy = rewardedBusy,
        bannerReserved = surfaces.banner,
        bannerAd = if (bannerVisible) bannerAd else null,
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
                        HapticKind.TICK -> HapticFeedbackType.TextHandleMove
                        HapticKind.PLACE, HapticKind.WRONG, HapticKind.WIN, HapticKind.LOSE ->
                            HapticFeedbackType.LongPress
                    },
                )
                is GameUiEvent.Sfx -> audio?.playSfx(event.kind)
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
                is GameUiEvent.LevelWon -> if (!sample) adsViewModel.onLevelWon(isDaily = event.level <= 0) // TEMP 9x9 sample
                is GameUiEvent.LevelLost -> Unit // dialogs render from state.outcome
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
        onBack = onBack,
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
        onRestart = {
            // Close first (clock resumes), then restart the level; same call the lose dialog's Retry uses.
            settingsVisible = false
            resumed = true
            viewModel.onAppStarted()
            viewModel.onRestart()
        },
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
        onRetryLoad = viewModel::onRetryLoad,
        // User-initiated boundary: maybe an interstitial BEFORE navigating (ad-free through Lv 9), then ALWAYS
        // continue (fail open).
        // The one-time Rate us dialog (after Lv 5 is won) goes FIRST, then the normal ad boundary + next level.
        onNextLevel = {
            audio?.stopSfx() // silence the win / award cue now, before the rate dialog, ad or next level starts
            val proceed = { adsViewModel.afterWin(adLevel, adIsDaily, onDone = viewModel::onNextLevel) }
            if (sample) {
                onBack() // TEMP 9x9 sample: win dialog "next" returns to the gallery
            } else if (adIsDaily) {
                proceed()
            } else {
                scope.launch {
                    if (rateUsViewModel.tryShow()) pendingNextLevel = proceed else proceed()
                }
            }
        },
        // Retry seam (interstitial OFF by default via AdPolicyConfig.showOnLoseRetry). Revive is a separate rewarded CTA.
        onLoseRetry = {
            // TEMP 9x9 sample: retry the sample directly, no ad seam
            if (sample) viewModel.onRestart() else adsViewModel.beforeRetry(adLevel, adIsDaily, onDone = viewModel::onRestart)
        },
        modifier = Modifier.fillMaxSize(),
        ads = adsUi,
        boosterDialog = boosterDialog,
        onEmptyBoosterTap = { b -> // TEMP 9x9 sample: no rewarded dialog, just the "none left" snackbar
            if (!sample) {
                boosterDialog = b
            } else {
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(
                        when (b) {
                            Booster.CAT -> noCatText
                            Booster.HINT -> noHintText
                            Booster.MOUSE -> noMouseText
                        },
                    )
                }
            }
        },
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
