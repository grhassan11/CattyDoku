package com.tm.meow.sudoku.puzzle.ui.ads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsEvent
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.analytics.NoopAnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.ads.AdSurfaces
import com.tm.meow.sudoku.puzzle.domain.ads.RewardedPlacement
import com.tm.meow.sudoku.puzzle.domain.ads.AdTrigger
import com.tm.meow.sudoku.puzzle.domain.ads.NativeAdSlotState
import com.tm.meow.sudoku.puzzle.domain.ads.RewardedResult
import com.tm.meow.sudoku.puzzle.domain.model.Booster
import com.tm.meow.sudoku.puzzle.domain.network.AlwaysOnline
import com.tm.meow.sudoku.puzzle.domain.time.TimeProvider
import com.tm.meow.sudoku.puzzle.domain.usecase.GrantBoosterReward
import com.tm.meow.sudoku.puzzle.domain.usecase.AdPlacement
import com.tm.meow.sudoku.puzzle.domain.usecase.LoadNativeAd
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveAdSurfaces
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveOnline
import com.tm.meow.sudoku.puzzle.domain.usecase.PreloadGameNativeAd
import com.tm.meow.sudoku.puzzle.domain.usecase.PreloadInterstitialAd
import com.tm.meow.sudoku.puzzle.domain.usecase.TakeGameNativeAd
import com.tm.meow.sudoku.puzzle.domain.usecase.PreloadRewardedAd
import com.tm.meow.sudoku.puzzle.domain.usecase.RecordLevelWon
import com.tm.meow.sudoku.puzzle.domain.usecase.RetryAdsInit
import com.tm.meow.sudoku.puzzle.domain.usecase.ShowInterstitialIfAllowed
import com.tm.meow.sudoku.puzzle.domain.usecase.ShowRewardedAd
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One-shot messages for the game screen (snackbar text is resolved in the UI). */
sealed interface AdsUiEvent {
    /** Rewarded ad could not be shown (not ready, load failure, no consent, SDK off). Nothing was granted. */
    data object RewardedUnavailable : AdsUiEvent

    /** Ad was closed before the reward was earned. Nothing was granted. */
    data object RewardNotEarned : AdsUiEvent
}

/**
 * Ad boundary for the game screen. Use cases only; the Activity-bound show happens in the data layer.
 * [afterWin] / [beforeRetry] ALWAYS call [onDone] exactly once, ad or no ad (fail open). One full-screen ad action at a
 * time: [busy] ignores taps while an interstitial or rewarded request is in flight (no double navigation / double grant).
 * Rewarded: [watchBoosterAd] / [watchReviveAd] call their callback ONLY after the earned-reward result, never on dismiss.
 * Game native slot (banner-sized compact native, replaces the old banner): held here (survives recomposition / dialogs /
 * Activity recreation), destroyed when the surface turns off or the VM clears.
 */
@HiltViewModel
class AdsViewModel @Inject constructor(
    private val recordLevelWon: RecordLevelWon,
    private val showInterstitial: ShowInterstitialIfAllowed,
    private val preloadInterstitial: PreloadInterstitialAd,
    observeAdSurfaces: ObserveAdSurfaces,
    private val preloadRewarded: PreloadRewardedAd,
    private val showRewarded: ShowRewardedAd,
    private val grantBoosterReward: GrantBoosterReward,
    private val loadNative: LoadNativeAd,
    private val takeGameNative: TakeGameNativeAd,
    private val preloadGameNative: PreloadGameNativeAd,
    private val time: TimeProvider,
    private val retryAdsInit: RetryAdsInit,
    observeOnline: ObserveOnline = ObserveOnline(AlwaysOnline),
    private val analytics: AnalyticsLogger = NoopAnalyticsLogger,
) : ViewModel() {

    private val busy = AtomicBoolean(false)

    /** Live internet state (true until the first platform callback). Rewarded CTAs show a helper text and stay disabled while false. */
    val isOnline: StateFlow<Boolean> = observeOnline()

    private var lastPreloadWasSudoku: Boolean? = null
    private var rewardedPreloadRequested = false

    init {
        // Offline preloads are skipped by the use cases (and the interstitial retry is parked, not spent): when the network is
        // back, re-ask once so the next boundary finds an ad ready.
        viewModelScope.launch {
            isOnline.collect { online ->
                if (!online) return@collect
                lastPreloadWasSudoku?.let { preloadInterstitialAd(it) }
                if (rewardedPreloadRequested) preloadRewardedAd()
            }
        }
    }

    private val _rewardedBusy = MutableStateFlow(false)

    /** True while a rewarded request / ad is in flight (UI disables the CTAs). */
    val rewardedBusy: StateFlow<Boolean> = _rewardedBusy.asStateFlow()

    private val screenLevel = MutableStateFlow(0)

    /** Level on screen (0 = daily / loading). Combined with persisted progress for the ad-free rule. */
    fun setScreenLevel(level: Int) {
        screenLevel.value = level
    }

    /** Live ad surfaces (rewarded / game native slots) after kill switches, ad-free levels, consent and SDK state. */
    val surfaces: StateFlow<AdSurfaces> = observeAdSurfaces(screenLevel)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdSurfaces())

    private val _events = Channel<AdsUiEvent>(Channel.BUFFERED)
    val events: Flow<AdsUiEvent> = _events.receiveAsFlow()

    private var initRetryJob: Job? = null

    /** Game screen entry: re-try a failed SDK init. No-op if already up, consent not granted, or a retry is running. */
    fun retryAdsInitOnEntry() {
        if (initRetryJob?.isActive == true) return
        initRetryJob = viewModelScope.launch { retryAdsInit() }
    }

    // ------------------------------------------------------------ interstitial

    /**
     * Keep an interstitial loaded for the next boundary. [isSudoku] true = no level gate; Levels skips ad-free levels only when the win interstitial is off.
     * Call on game-screen entry (and again once the level number / ads state is known). Idempotent; policy in the use case.
     */
    fun preloadInterstitialAd(isSudoku: Boolean) {
        lastPreloadWasSudoku = isSudoku
        preloadInterstitial(if (isSudoku) null else surfaces.value.playerLevel.coerceAtLeast(screenLevel.value))
    }

    /** Call when a level is WON (diagnostic win counter). Daily wins are ignored. */
    fun onLevelWon(isDaily: Boolean) = recordLevelWon(isDaily)

    /** Win dialog "Level N+1": maybe show an interstitial BEFORE [onDone] (navigates). Not ad-free gated: frequency rule only. */
    fun afterWin(level: Int, isDaily: Boolean, onDone: () -> Unit): Boolean =
        run(AdTrigger.LEVEL_WON_CONTINUE, level, isDaily, onDone)

    /** Lose dialog Retry: maybe an interstitial (remote `levels_game_restarted_interstitial_ad_show`, default on) BEFORE [onDone]. */
    fun beforeRetry(level: Int, isDaily: Boolean, onDone: () -> Unit): Boolean =
        run(AdTrigger.LOSE_RETRY, level, isDaily, onDone)

    /** Lose screen Home / system back: same gating + RC switch as [beforeRetry], BEFORE [onDone] (navigates Home). */
    fun beforeLoseHome(level: Int, isDaily: Boolean, onDone: () -> Unit): Boolean =
        run(AdTrigger.LOSE_HOME, level, isDaily, onDone)

    /**
     * Levels / Daily gameplay BACK (top-bar icon or system back): maybe the preloaded interstitial (remote
     * `levels_gameplay_back_button_native_ad_show`) BEFORE [onDone] (navigates). Caller must check progress first. No load wait.
     */
    fun beforeGameBack(level: Int, isDaily: Boolean, onDone: () -> Unit): Boolean =
        run(AdTrigger.LEVELS_GAME_BACK, level, isDaily, onDone)

    /** Returns false (and never calls [onDone]) when another full-screen ad action is in flight. */
    private fun run(trigger: AdTrigger, level: Int, isDaily: Boolean, onDone: () -> Unit): Boolean {
        if (!busy.compareAndSet(false, true)) return false
        viewModelScope.launch {
            try {
                showInterstitial(trigger, level, isDaily)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // fail open
            } finally {
                busy.set(false)
            }
            onDone()
        }
        return true
    }

    // ------------------------------------------------------------ rewarded

    /**
     * Keep a rewarded ad loaded. Call when any booster is empty, on the last heart, and when the lose dialog shows.
     * Policy (ad-free levels, consent, SDK) is decided in the use case; the preloader refills itself after each show.
     */
    fun preloadRewardedAd() {
        rewardedPreloadRequested = true
        preloadRewarded(surfaces.value.playerLevel.coerceAtLeast(screenLevel.value))
    }

    /** Levels booster dialog "Watch Video": on EARNED grant +1 [booster] to inventory, then [onGranted] (game refreshes charges). */
    fun watchBoosterAd(booster: Booster, onGranted: () -> Unit) =
        watchBooster(booster, RewardedPlacement.LEVELS_BOOSTERS, onGranted)

    /** Sudoku out-of-hints dialog "Watch": on EARNED grant +1 hint, then [onGranted]. Gated by the Sudoku boosters switch. */
    fun watchSudokuHintAd(onGranted: () -> Unit) =
        watchBooster(Booster.HINT, RewardedPlacement.SUDOKU_BOOSTERS, onGranted)

    private fun watchBooster(booster: Booster, rewardedPlacement: RewardedPlacement, onGranted: () -> Unit) {
        watch(placement = "booster_${booster.name.lowercase()}", rewardedPlacement = rewardedPlacement, onEarned = {
            grantBoosterReward(booster)
            analytics.log(AnalyticsEvent.RewardedEarned("booster_${booster.name.lowercase()}"))
            onGranted()
        })
    }

    /** Levels lose dialog "Watch Ad": on EARNED call [onGranted] (game revives with +1 life and continues). */
    fun watchReviveAd(onGranted: () -> Unit) = watchRevive(RewardedPlacement.LEVELS_REVIVE, onGranted)

    /** Sudoku game-over "Watch Ad": on EARNED call [onGranted] (game revives). Gated by the Sudoku revive switch. */
    fun watchSudokuReviveAd(onGranted: () -> Unit) = watchRevive(RewardedPlacement.SUDOKU_REVIVE, onGranted)

    private fun watchRevive(rewardedPlacement: RewardedPlacement, onGranted: () -> Unit) {
        watch(placement = "revive", rewardedPlacement = rewardedPlacement, onEarned = {
            analytics.log(AnalyticsEvent.RewardedEarned("revive"))
            onGranted()
        })
    }

    private fun watch(placement: String, rewardedPlacement: RewardedPlacement, onEarned: suspend () -> Unit) {
        if (!isOnline.value) return // offline: the CTA is disabled and shows a helper text; never attempt a load
        if (!busy.compareAndSet(false, true)) return
        _rewardedBusy.value = true
        viewModelScope.launch {
            try {
                when (showRewarded(surfaces.value.playerLevel.coerceAtLeast(screenLevel.value), rewardedPlacement)) {
                    RewardedResult.EARNED -> onEarned()
                    RewardedResult.DISMISSED -> _events.trySend(AdsUiEvent.RewardNotEarned)
                    RewardedResult.NOT_READY, RewardedResult.FAILED -> {
                        analytics.log(AnalyticsEvent.RewardedFailed(placement))
                        _events.trySend(AdsUiEvent.RewardedUnavailable)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                analytics.log(AnalyticsEvent.RewardedFailed(placement))
                _events.trySend(AdsUiEvent.RewardedUnavailable)
            } finally {
                busy.set(false)
                _rewardedBusy.value = false
                preloadRewardedAd() // next one, if a booster is still empty the screen keeps asking too
            }
        }
    }

    // ------------------------------------------------------------ game native slot (was banner)

    private val _gameNative = MutableStateFlow<NativeAdSlotState>(NativeAdSlotState.Hidden)

    /**
     * Compact game-screen native slot. [NativeAdSlotState.Loading] while a request is in flight (UI shows the skeleton),
     * [NativeAdSlotState.Loaded] when ready, [NativeAdSlotState.Failed] on no fill / timeout (UI keeps the skeleton),
     * [NativeAdSlotState.Hidden] before the first request / after [releaseGameNative] (UI: blank, same height).
     */
    val gameNative: StateFlow<NativeAdSlotState> = _gameNative.asStateFlow()

    private var gameNativeJob: Job? = null
    private var gameNativeFailedAtMs: Long? = null

    /**
     * Load the native ad for [placement] ([AdPlacement.LEVELS_GAME] or [AdPlacement.SUDOKU_GAME]). No-op when one is held,
     * loading, or a load failed within the last minute (no refetch thrash on recomposition / re-entry).
     */
    fun requestGameNative(placement: AdPlacement) {
        if (_gameNative.value is NativeAdSlotState.Loaded || gameNativeJob?.isActive == true) return
        val failedAt = gameNativeFailedAtMs
        if (failedAt != null && time.monotonicMillis() - failedAt < NATIVE_RETRY_AFTER_FAIL_MS) {
            // Throttled: no request, but the slot stays a skeleton (never blank / Hidden while the surface is on).
            if (_gameNative.value is NativeAdSlotState.Hidden) _gameNative.value = NativeAdSlotState.Failed
            return
        }
        _gameNative.value = NativeAdSlotState.Loading
        gameNativeJob = viewModelScope.launch {
            val level = surfaces.value.playerLevel.coerceAtLeast(screenLevel.value)
            // Preloaded ad first (instant bind); fall back to a live request when the cache has nothing.
            val handle = try {
                takeGameNative(placement, level) ?: loadNative(placement, level)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                null
            }
            if (!isActive) {
                handle?.destroy() // released / cleared while loading
                return@launch
            }
            if (handle == null) {
                gameNativeFailedAtMs = time.monotonicMillis()
                _gameNative.value = NativeAdSlotState.Failed
            } else {
                gameNativeFailedAtMs = null
                _gameNative.value = NativeAdSlotState.Loaded(handle)
                preloadGameNative(placement) // next game of this mode (no-op when gated / already cached / loading)
            }
        }
    }

    /**
     * Slot is on but a request is not allowed (offline / no consent / SDK not ready): cancel any load, drop a held ad and show
     * the skeleton. NO request is made (no retry spent). The caller re-runs [requestGameNative] when allowed again.
     */
    fun showGameNativeSkeleton() {
        gameNativeJob?.cancel()
        gameNativeJob = null
        (_gameNative.value as? NativeAdSlotState.Loaded)?.ad?.destroy()
        gameNativeFailedAtMs = null // offline is not a failed load: the reconnect request must not be throttled
        _gameNative.value = NativeAdSlotState.Loading
    }

    /** Destroy the held native ad and cancel an in-flight load (surface turned off / screen left). */
    fun releaseGameNative() {
        gameNativeJob?.cancel()
        gameNativeJob = null
        (_gameNative.value as? NativeAdSlotState.Loaded)?.ad?.destroy()
        _gameNative.value = NativeAdSlotState.Hidden
    }

    // ------------------------------------------------------------ Level Completed native (levels_game_completed_native_ad_show)

    private val _completeNative = MutableStateFlow<NativeAdSlotState>(NativeAdSlotState.Hidden)

    /** Native for the Level Completed / Puzzle solved screen's sticky bottom slot (placement per caller). Separate from [gameNative] (a native view binds once). */
    val completeNative: StateFlow<NativeAdSlotState> = _completeNative.asStateFlow()

    private var completeNativeJob: Job? = null

    /** Warm the cache while the level is played so the win screen binds instantly. No-op when gated (use case checks every gate). */
    fun preloadCompleteNative(placement: AdPlacement = AdPlacement.LEVELS_COMPLETE) {
        preloadGameNative(placement)
    }

    /**
     * Win time: take the preloaded ad (or load live) for the completion screen. No-op when one is held / loading. Gated or no
     * fill = Failed (UI keeps the skeleton only if the slot is reserved; Remove Ads / switch off reserve nothing).
     */
    fun requestCompleteNative(placement: AdPlacement = AdPlacement.LEVELS_COMPLETE) {
        if (_completeNative.value is NativeAdSlotState.Loaded || completeNativeJob?.isActive == true) return
        _completeNative.value = NativeAdSlotState.Loading
        completeNativeJob = viewModelScope.launch {
            val level = surfaces.value.playerLevel.coerceAtLeast(screenLevel.value)
            val handle = try {
                takeGameNative(placement, level) ?: loadNative(placement, level)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                null
            }
            if (!isActive) {
                handle?.destroy()
                return@launch
            }
            _completeNative.value = if (handle == null) NativeAdSlotState.Failed else NativeAdSlotState.Loaded(handle)
        }
    }

    /** Next level / leaving the win screen: destroy the held ad and cancel a load; next win requests a fresh one. */
    fun releaseCompleteNative() {
        completeNativeJob?.cancel()
        completeNativeJob = null
        (_completeNative.value as? NativeAdSlotState.Loaded)?.ad?.destroy()
        _completeNative.value = NativeAdSlotState.Hidden
    }

    override fun onCleared() {
        releaseCompleteNative()
        releaseGameNative()
    }

    private companion object {
        const val NATIVE_RETRY_AFTER_FAIL_MS = 60_000L
    }
}
