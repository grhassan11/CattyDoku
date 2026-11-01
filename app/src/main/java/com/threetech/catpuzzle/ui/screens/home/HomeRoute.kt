package com.threetech.catpuzzle.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.domain.engine.DailyRules
import com.threetech.catpuzzle.ui.ads.HomeAdsViewModel
import com.threetech.catpuzzle.ui.screens.rate.RateUsHost
import com.threetech.catpuzzle.ui.screens.rate.RateUsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The only place that touches [HomeViewModel]: collects state, refreshes on start (level label and
 * streak change while the game is open), then hands plain state and lambdas to the stateless [HomeScreen].
 * No sfx here. Bottom native ad: [HomeAdsViewModel] (loads on start, destroyed when Home leaves composition).
 */
@Composable
fun HomeRoute(
    onPlayLevel: (Int) -> Unit,
    onPlayDaily: () -> Unit,
    onOpenStreak: () -> Unit,
    onOpenShop: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAvatar: () -> Unit, // TEMP node gallery
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    adsViewModel: HomeAdsViewModel = hiltViewModel(),
    rateUsViewModel: RateUsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nativeAdSlot by adsViewModel.slot.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val requiredLevel = (state.daily as? DailyUi.Locked)?.requiredLevel ?: DailyRules.UNLOCK_LEVEL
    val lockedText = stringResource(R.string.home_daily_locked_snackbar, requiredLevel)
    val doneText = stringResource(R.string.home_daily_done_snackbar)
    val sudokuSoonText = stringResource(R.string.home_sudoku_coming_soon)

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        viewModel.refresh()
        adsViewModel.onScreenStart()
        // Home is a safe boundary for the one-time Rate us prompt. Short delay: a resume App Open ad (if any) starts
        // first, and the policy then sees it (full-screen flag / ad gap) and holds the prompt back.
        scope.launch {
            delay(RATE_US_CHECK_DELAY_MS)
            rateUsViewModel.tryShow()
        }
    }
    // Home leaves composition (Game opened, activity recreated): destroy the ad; ON_START loads a fresh one on return.
    DisposableEffect(adsViewModel) { onDispose { adsViewModel.release() } }

    Box(modifier.fillMaxSize()) {
    HomeScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onNodeClick = viewModel::onNodeSelected,
        onSheetDismiss = viewModel::onSheetDismissed,
        onPlayLevel = { lvl ->
            viewModel.onSheetDismissed()
            onPlayLevel(lvl)
        },
        onDailyChallenge = {
            when (state.daily) {
                DailyUi.Available -> onPlayDaily()
                is DailyUi.Locked, DailyUi.Done -> scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(if (state.daily == DailyUi.Done) doneText else lockedText)
                }
            }
        },
        onOpenStreak = onOpenStreak,
        onOpenSettings = onOpenSettings,
        onOpenShop = onOpenShop,
        onOpenAvatar = onOpenAvatar, // TEMP node gallery
        onSudokuTab = {
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(sudokuSoonText)
            }
        },
        modifier = Modifier.fillMaxSize(),
        nativeAdSlot = nativeAdSlot,
    )
    // One-time Rate us dialog (last child = on top of Home).
    RateUsHost(rateUsViewModel, snackbarHostState)
    }
}

/** Wait before checking the Rate us prompt on Home start (lets a resume App Open ad begin first). */
private const val RATE_US_CHECK_DELAY_MS = 1_500L
