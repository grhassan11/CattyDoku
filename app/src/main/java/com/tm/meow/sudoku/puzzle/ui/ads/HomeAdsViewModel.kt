package com.tm.meow.sudoku.puzzle.ui.ads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tm.meow.sudoku.puzzle.domain.ads.NativeAdSlotState
import com.tm.meow.sudoku.puzzle.domain.time.TimeProvider
import com.tm.meow.sudoku.puzzle.domain.usecase.AdPlacement
import com.tm.meow.sudoku.puzzle.domain.usecase.LoadNativeAd
import com.tm.meow.sudoku.puzzle.domain.usecase.MarkStartFlowComplete
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveAdsRemoved
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Ads on the Home screen: the bottom native ad. Use cases only; no SDK type is touched here (the ad is an opaque
 * NativeAdHandle). [slot] drives the screen: Hidden (ads not allowed) and Failed collapse the slot; Loading reserves it
 * (skeleton); Loaded shows the ad.
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
                if (allowed && screenStarted && _slot.value !is NativeAdSlotState.Loaded && loadJob?.isActive != true) onScreenStart()
            }
        }
    }

    fun onScreenStart() {
        screenStarted = true
        markStartFlowComplete()
        val now = time.monotonicMillis()
        if (_slot.value is NativeAdSlotState.Loaded) {
            if (now - loadedAtMs < MAX_AD_AGE_MS) return
            release()
        }
        if (!loadNativeAd.isAllowed(placement)) {
            _slot.value = NativeAdSlotState.Hidden
            return
        }
        if (loadJob?.isActive == true) return
        val failedAt = lastFailAtMs
        if (failedAt != null && now - failedAt < RETRY_AFTER_FAIL_MS) {
            _slot.value = NativeAdSlotState.Failed
            return
        }
        _slot.value = NativeAdSlotState.Loading
        loadJob = viewModelScope.launch {
            // Bounded skeleton: past the cap the load is cancelled (a late ad is destroyed by the repo) and the slot collapses.
            val handle = try {
                withTimeoutOrNull(SKELETON_TIMEOUT_MS) { loadNativeAd(placement) }
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
                _slot.value = if (loadNativeAd.isAllowed(placement)) NativeAdSlotState.Failed else NativeAdSlotState.Hidden
            } else {
                lastFailAtMs = null
                loadedAtMs = time.monotonicMillis()
                _slot.value = NativeAdSlotState.Loaded(handle)
            }
        }
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
        if (loadNativeAd.isAllowed(placement)) NativeAdSlotState.Loading else NativeAdSlotState.Hidden

    override fun onCleared() {
        release()
    }

    private companion object {
        /** Native ads expire after 1 hour; replace a little earlier. */
        const val MAX_AD_AGE_MS = 50 * 60 * 1000L
        const val RETRY_AFTER_FAIL_MS = 60_000L

        /** Max time the skeleton is shown before the slot collapses (Failed). */
        const val SKELETON_TIMEOUT_MS = 6_000L
    }
}

/** Levels Home bottom native ad (`levels_home_native_ad_show`). */
@HiltViewModel
class LevelsHomeAdsViewModel @Inject constructor(
    loadNativeAd: LoadNativeAd,
    markStartFlowComplete: MarkStartFlowComplete,
    time: TimeProvider,
    observeAdsRemoved: ObserveAdsRemoved,
) : HomeAdsViewModel(AdPlacement.LEVELS_HOME, loadNativeAd, markStartFlowComplete, time, observeAdsRemoved)

/** Sudoku entry bottom native ad (`sudoku_home_native_ad_show`). */
@HiltViewModel
class SudokuHomeAdsViewModel @Inject constructor(
    loadNativeAd: LoadNativeAd,
    markStartFlowComplete: MarkStartFlowComplete,
    time: TimeProvider,
    observeAdsRemoved: ObserveAdsRemoved,
) : HomeAdsViewModel(AdPlacement.SUDOKU_HOME, loadNativeAd, markStartFlowComplete, time, observeAdsRemoved)
