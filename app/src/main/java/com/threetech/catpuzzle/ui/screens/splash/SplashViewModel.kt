package com.threetech.catpuzzle.ui.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.threetech.catpuzzle.domain.config.NoopRemoteConfigRepository
import com.threetech.catpuzzle.domain.startup.SplashTiming
import com.threetech.catpuzzle.domain.usecase.RefreshRemoteConfig
import com.threetech.catpuzzle.domain.usecase.GatherConsentAndInitAds
import com.threetech.catpuzzle.domain.usecase.IsConsentFormShowing
import com.threetech.catpuzzle.domain.usecase.ObserveTermsAccepted
import com.threetech.catpuzzle.domain.usecase.PrefetchLevel
import com.threetech.catpuzzle.domain.usecase.ResolveStartLevel
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
 * cache for the level the game will open. Timing rule is the pure [SplashTiming]; elapsed time is counted in ticks
 * (virtual-time friendly) and does NOT advance while the UMP form is on screen. Hard cap [SplashTiming.MAX_MS]:
 * a hung consent / init never traps the user, the app continues with ads off.
 *
 * [start] is idempotent and called by the route once the screen is RESUMED (UMP needs a resumed Activity). The
 * ViewModel survives rotation, so consent never runs twice.
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val observeTermsAccepted: ObserveTermsAccepted,
    private val resolveStartLevel: ResolveStartLevel,
    private val prefetchLevel: PrefetchLevel,
    private val gather: GatherConsentAndInitAds,
    private val isConsentFormShowing: IsConsentFormShowing,
    private val refreshRemoteConfig: RefreshRemoteConfig = RefreshRemoteConfig(NoopRemoteConfigRepository),
) : ViewModel() {

    private val eventChannel = Channel<SplashEvent>(Channel.BUFFERED)
    val events: Flow<SplashEvent> = eventChannel.receiveAsFlow()

    private var started = false

    fun start() {
        if (started) return
        started = true
        // Fire-and-forget on the repository's own scope + time budget: never awaited, never extends the splash caps.
        // Fetched values apply from the next launch (config is frozen per process).
        try {
            refreshRemoteConfig()
        } catch (t: Throwable) {
            // defaults stay
        }
        viewModelScope.launch { run() }
    }

    private suspend fun run() {
        // Terms accepted = a returning launch (short minimum). A read failure counts as first launch.
        val returning = try {
            observeTermsAccepted().first()
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
        eventChannel.send(SplashEvent.Finished)
    }
}
