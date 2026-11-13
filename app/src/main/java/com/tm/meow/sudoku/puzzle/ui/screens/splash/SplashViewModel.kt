package com.tm.meow.sudoku.puzzle.ui.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tm.meow.sudoku.puzzle.domain.config.NoopRemoteConfigRepository
import com.tm.meow.sudoku.puzzle.domain.startup.SplashTiming
import com.tm.meow.sudoku.puzzle.domain.usecase.RefreshRemoteConfig
import com.tm.meow.sudoku.puzzle.domain.usecase.GatherConsentAndInitAds
import com.tm.meow.sudoku.puzzle.domain.usecase.IsConsentFormShowing
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveReturningLaunch
import com.tm.meow.sudoku.puzzle.domain.usecase.PrefetchLevel
import com.tm.meow.sudoku.puzzle.domain.usecase.AdPlacement
import com.tm.meow.sudoku.puzzle.domain.usecase.PreloadAppOpenAd
import com.tm.meow.sudoku.puzzle.domain.usecase.PreloadGameNativeAd
import com.tm.meow.sudoku.puzzle.domain.usecase.ResolveStartLevel
import com.tm.meow.sudoku.puzzle.domain.usecase.ShowAppOpenOnSplash
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface SplashEvent {
    /** Consent + ads init done (or timed out / failed open): move on to the next start step. */
    data object Finished : SplashEvent
}

/**
 * In-app splash and startup gate. Stays up until BOTH are done: UMP consent gather and the ads SDK init that
 * follows it ([GatherConsentAndInitAds], fail-open, never throws). Alongside, best-effort warm-up of the puzzle
 * cache for the level the game will open. After that, the app-open ad is loaded (bounded wait) and shown; Finished follows its dismissal. Timing rule is the pure [SplashTiming]; elapsed time is counted in ticks
 * (virtual-time friendly) and does NOT advance while the UMP form is on screen. Hard cap [SplashTiming.MAX_MS]:
 * a hung consent / init never traps the user, the app continues with ads off.
 *
 * [start] is idempotent and called by the route once the screen is RESUMED (UMP needs a resumed Activity). The
 * ViewModel survives rotation, so consent never runs twice.
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val observeReturningLaunch: ObserveReturningLaunch,
    private val resolveStartLevel: ResolveStartLevel,
    private val prefetchLevel: PrefetchLevel,
    private val gather: GatherConsentAndInitAds,
    private val isConsentFormShowing: IsConsentFormShowing,
    private val showAppOpenOnSplash: ShowAppOpenOnSplash,
    private val preloadAppOpen: PreloadAppOpenAd,
    private val preloadNative: PreloadGameNativeAd,
    private val refreshRemoteConfig: RefreshRemoteConfig = RefreshRemoteConfig(NoopRemoteConfigRepository),
) : ViewModel() {

    private val eventChannel = Channel<SplashEvent>(Channel.BUFFERED)
    val events: Flow<SplashEvent> = eventChannel.receiveAsFlow()

    private var started = false

    fun start() {
        if (started) return
        started = true
        viewModelScope.launch { run() }
    }

    private suspend fun run() {
        // Tutorial done = a returning launch (short minimum). A read failure counts as first launch.
        val returning = try {
            observeReturningLaunch().first()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            false
        }
        val consent: Job = viewModelScope.launch {
            try {
                gather()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // fail open: ads stay off
            }
            // Consent + SDK init done: start the app-open load now, in parallel with the min splash / warm-up. Not awaited here;
            // ShowAppOpenOnSplash joins the in-flight load later. Idempotent, gated (consent / SDK / Remove Ads / offline), never throws.
            // Skipped when the splash app-open kill switch (Remote Config, cached/default, sync) is off.
            if (showAppOpenOnSplash.isEnabled()) preloadAppOpen()
            // Consent is resolved: the Remote Config fetch is consent-gated (A-003), so it can only start now. Fire-and-forget
            // on the repository's own scope + time budget: never awaited, never extends the splash caps. Fetched values apply
            // from the next launch (config is frozen per process); until then cached / default values (kill switches default true).
            try {
                refreshRemoteConfig()
            } catch (t: Throwable) {
                // defaults stay
            }
        }
        val warm: Job = viewModelScope.launch {
            try {
                prefetchLevel(resolveStartLevel())
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // best effort: the game screen loads (and reports failure) by itself
            }
        }
        var elapsed = 0L
        while (!SplashTiming.shouldProceed(elapsed, consent.isCompleted, warm.isCompleted, returning)) {
            delay(SplashTiming.TICK_MS)
            // The user reading the consent form must not eat the cap.
            if (!isConsentFormShowing()) elapsed += SplashTiming.TICK_MS
        }
        // Splash work done: load-wait + show the app-open ad (cold-start skip lifted). Returns when dismissed / failed / skipped /
        // timed out; Finished is only sent after, so Home / Tutorial never appears under the ad. Never throws.
        try {
            // First launch: 15 s budget counted from splash start (elapsed loop ticks), returning: 5 s. Exits early on ready / failed load.
            showAppOpenOnSplash(SplashTiming.appOpenWaitMs(returning, elapsed))
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // continue without ad
        }
        // Home native ads (Levels + Sudoku) into the app-wide cache, started only AFTER the app-open step so the two requests never
        // compete for network / SDK time (the app-open load must win). Each is gated by its own remote switch / consent /
        // Remove Ads / offline inside the use case. Not awaited; loads run one at a time; Home joins an in-flight one.
        try {
            preloadNative(AdPlacement.LEVELS_HOME)
            preloadNative(AdPlacement.SUDOKU_HOME)
        } catch (t: Throwable) {
            // best effort: Home loads by itself
        }
        eventChannel.send(SplashEvent.Finished)
    }
}
