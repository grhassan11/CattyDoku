package com.tm.meow.sudoku.puzzle.ui.ads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tm.meow.sudoku.puzzle.domain.ads.NativeAdSlotState
import com.tm.meow.sudoku.puzzle.domain.time.TimeProvider
import com.tm.meow.sudoku.puzzle.domain.usecase.AdPlacement
import com.tm.meow.sudoku.puzzle.domain.usecase.LoadNativeAd
import com.tm.meow.sudoku.puzzle.domain.usecase.MarkStartFlowComplete
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveAdsRemoved
import com.tm.meow.sudoku.puzzle.domain.usecase.PreloadGameNativeAd
import com.tm.meow.sudoku.puzzle.domain.usecase.TakeGameNativeAd
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Ads on the Home screen: the bottom native ad. Use cases only; no SDK type is touched here (the ad is an opaque
 * NativeAdHandle). [slot] drives the screen: Hidden (kill switch / Remove Ads only) = no slot; Loading and Failed = skeleton
 * (also offline / no consent / SDK not ready: no request, skeleton stays); Loaded shows the ad.
 *
 * Lifecycle: [onScreenStart] loads when no ad is held; an ad older than [MAX_AD_AGE_MS] (native ads expire after 1 h)
 * is replaced; a failed load is retried at most every [RETRY_AFTER_FAIL_MS]. [release] destroys the ad when Home leaves
 * composition (and in [onCleared]); the next [onScreenStart] loads a fresh one, so a destroyed ad is never re-bound.
 * One concrete subclass per placement (own remote switch, own Hilt instance). Also marks the start flow complete: reaching Home is what unlocks app-open ads.
 */
abstract class HomeAdsViewModel(
    private val placement: AdPlacement,
    private val loadNativeAd: LoadNativeAd,
    private val markStartFlowComplete: MarkStartFlowComplete,
    private val time: TimeProvider,
    observeAdsRemoved: ObserveAdsRemoved,
    private val preloadGameNative: PreloadGameNativeAd,
    private val gamePlacement: AdPlacement,
    private val takeNativeAd: TakeGameNativeAd,
) : ViewModel() {

    // Loading from the start when ads are allowed, so the slot is reserved on the very first frame (no shift).
    private val _slot = MutableStateFlow(idleState())
    val slot: StateFlow<NativeAdSlotState> = _slot

    private var loadJob: Job? = null
    private var loadedAtMs = 0L
    private var lastFailAtMs: Long? = null
    private var screenStarted = false

    // Declared after the fields it touches: Main.immediate runs the first collection inline.
    init {
        // Remove Ads bought (or restored) while Home is alive: drop the held / loading ad right away. Later loads are
        // refused by LoadNativeAd itself.
        viewModelScope.launch {
            observeAdsRemoved().collect { removed -> if (removed) release() }
        }
        // SDK init / consent can finish after the screen started (slot stayed Hidden): load once allowed.
        viewModelScope.launch {
            loadNativeAd.allowedChanges(placement).collect { allowed ->
                if (!screenStarted) return@collect
                when {
                    // Slot switched off (kill switch / Remove Ads): the ONLY reason the slot disappears.
                    !loadNativeAd.isSlotEnabled(placement) -> collapse()
                    allowed -> if (_slot.value !is NativeAdSlotState.Loaded && loadJob?.isActive != true) onScreenStart()
                    // Not allowed to REQUEST (offline, consent, SDK not ready): drop the ad / load, keep the skeleton. screenStarted
                    // stays true so it loads by itself when allowed again (connectivity returns).
                    else -> toSkeleton()
                }
            }
        }
    }

    fun onScreenStart() {
        screenStarted = true
        markStartFlowComplete()
        val now = time.monotonicMillis()
        if (_slot.value is NativeAdSlotState.Loaded) {
            if (now - loadedAtMs < MAX_AD_AGE_MS) {
                preloadGameNative(gamePlacement)
                return
            }
            release()
        }
        if (!loadNativeAd.isSlotEnabled(placement)) {
            _slot.value = NativeAdSlotState.Hidden // kill switch / Remove Ads: no slot at all
            preloadGameNative(gamePlacement) // the game slot has its own switch
            return
        }
        if (!loadNativeAd.isAllowed(placement)) {
            // Offline / no consent / SDK not ready: skeleton stays, NO request. allowedChanges reloads when allowed again.
            _slot.value = NativeAdSlotState.Loading
            preloadGameNative(gamePlacement)
            return
        }
        if (loadJob?.isActive == true) return // its completion kicks the game preload
        val failedAt = lastFailAtMs
        if (failedAt != null && now - failedAt < RETRY_AFTER_FAIL_MS) {
            _slot.value = NativeAdSlotState.Failed // skeleton stays (NativeAdSlot renders Failed as the skeleton)
            preloadGameNative(gamePlacement)
            return
        }
        _slot.value = NativeAdSlotState.Loading
        loadJob = viewModelScope.launch {
            // No skeleton timeout: the skeleton stays until the ad arrives. Request time is bounded by the repo (15 s) and the preload wait.
            val handle = try {
                // Splash-preloaded ad first (waits for an in-flight preload); else load now.
                takeNativeAd(placement, Int.MAX_VALUE) ?: loadNativeAd(placement)
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
                lastFailAtMs = time.monotonicMillis()
                // Skeleton stays either way; Hidden only if the slot was switched off meanwhile.
                _slot.value = if (loadNativeAd.isSlotEnabled(placement)) NativeAdSlotState.Failed else NativeAdSlotState.Hidden
            } else {
                lastFailAtMs = null
                loadedAtMs = time.monotonicMillis()
                _slot.value = NativeAdSlotState.Loaded(handle)
            }
            // Home native settled: only now preload the game ad of this mode (never two native loads at once on the shared unit).
            preloadGameNative(gamePlacement)
        }
    }

    /** Request no longer allowed (offline / consent / SDK): drop the held / loading ad, show the skeleton, stay "started" so a later allow reloads. */
    private fun toSkeleton() {
        loadJob?.cancel()
        loadJob = null
        (_slot.value as? NativeAdSlotState.Loaded)?.ad?.destroy()
        lastFailAtMs = null
        _slot.value = NativeAdSlotState.Loading
    }

    /** Hide the slot (no layout space; kill switch / Remove Ads only) and drop the held / loading ad, but keep the screen "started". */
    private fun collapse() {
        loadJob?.cancel()
        loadJob = null
        (_slot.value as? NativeAdSlotState.Loaded)?.ad?.destroy()
        lastFailAtMs = null
        _slot.value = NativeAdSlotState.Hidden
    }

    /** Destroy the held ad and cancel an in-flight load. Safe to call repeatedly. Slot resets to its idle state. */
    fun release() {
        screenStarted = false
        loadJob?.cancel()
        loadJob = null
        (_slot.value as? NativeAdSlotState.Loaded)?.ad?.destroy()
        _slot.value = idleState()
    }

    private fun idleState(): NativeAdSlotState =
        if (loadNativeAd.isSlotEnabled(placement)) NativeAdSlotState.Loading else NativeAdSlotState.Hidden

    override fun onCleared() {
        release()
    }

    private companion object {
        /** Native ads expire after 1 hour; replace a little earlier. */
        const val MAX_AD_AGE_MS = 50 * 60 * 1000L
        const val RETRY_AFTER_FAIL_MS = 60_000L
    }
}

/** Levels Home bottom native ad (`level_home_native_ad_show`). */
@HiltViewModel
class LevelsHomeAdsViewModel @Inject constructor(
    loadNativeAd: LoadNativeAd,
    markStartFlowComplete: MarkStartFlowComplete,
    time: TimeProvider,
    observeAdsRemoved: ObserveAdsRemoved,
    preloadGameNative: PreloadGameNativeAd,
    takeNativeAd: TakeGameNativeAd,
) : HomeAdsViewModel(
    AdPlacement.LEVELS_HOME, loadNativeAd, markStartFlowComplete, time, observeAdsRemoved,
    preloadGameNative, AdPlacement.LEVELS_GAME, takeNativeAd,
)

/** Sudoku entry bottom native ad (`sudoku_home_native_ad_show`). */
@HiltViewModel
class SudokuHomeAdsViewModel @Inject constructor(
    loadNativeAd: LoadNativeAd,
    markStartFlowComplete: MarkStartFlowComplete,
    time: TimeProvider,
    observeAdsRemoved: ObserveAdsRemoved,
    preloadGameNative: PreloadGameNativeAd,
    takeNativeAd: TakeGameNativeAd,
) : HomeAdsViewModel(
    AdPlacement.SUDOKU_HOME, loadNativeAd, markStartFlowComplete, time, observeAdsRemoved,
    preloadGameNative, AdPlacement.SUDOKU_GAME, takeNativeAd,
)
