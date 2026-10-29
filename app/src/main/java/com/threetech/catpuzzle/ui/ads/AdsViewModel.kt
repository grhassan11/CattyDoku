package com.threetech.catpuzzle.ui.ads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.threetech.catpuzzle.domain.analytics.AnalyticsEvent
import com.threetech.catpuzzle.domain.analytics.AnalyticsLogger
import com.threetech.catpuzzle.domain.analytics.NoopAnalyticsLogger
import com.threetech.catpuzzle.domain.ads.AdSurfaces
import com.threetech.catpuzzle.domain.ads.AdTrigger
import com.threetech.catpuzzle.domain.ads.BannerAdHandle
import com.threetech.catpuzzle.domain.ads.RewardedResult
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.domain.time.TimeProvider
import com.threetech.catpuzzle.domain.usecase.GrantBoosterReward
import com.threetech.catpuzzle.domain.usecase.LoadBannerAd
import com.threetech.catpuzzle.domain.usecase.ObserveAdSurfaces
import com.threetech.catpuzzle.domain.usecase.PreloadRewardedAd
import com.threetech.catpuzzle.domain.usecase.RecordLevelWon
import com.threetech.catpuzzle.domain.usecase.ShowInterstitialIfAllowed
import com.threetech.catpuzzle.domain.usecase.ShowRewardedAd
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
 * Banner: held here (survives recomposition / dialogs), destroyed when the surface turns off or the VM clears.
 */
@HiltViewModel
class AdsViewModel @Inject constructor(
    private val recordLevelWon: RecordLevelWon,
    private val showInterstitial: ShowInterstitialIfAllowed,
    observeAdSurfaces: ObserveAdSurfaces,
    private val preloadRewarded: PreloadRewardedAd,
    private val showRewarded: ShowRewardedAd,
    private val grantBoosterReward: GrantBoosterReward,
    private val loadBanner: LoadBannerAd,
    private val time: TimeProvider,
    private val analytics: AnalyticsLogger = NoopAnalyticsLogger,
) : ViewModel() {

    private val busy = AtomicBoolean(false)

    private val _rewardedBusy = MutableStateFlow(false)

    /** True while a rewarded request / ad is in flight (UI disables the CTAs). */
    val rewardedBusy: StateFlow<Boolean> = _rewardedBusy.asStateFlow()

    private val screenLevel = MutableStateFlow(0)

    /** Level on screen (0 = daily / loading). Combined with persisted progress for the ad-free rule. */
    fun setScreenLevel(level: Int) {
        screenLevel.value = level
    }

    /** Live ad surfaces (rewarded / banner) after kill switches, ad-free levels, consent and SDK state. */
    val surfaces: StateFlow<AdSurfaces> = observeAdSurfaces(screenLevel)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdSurfaces())

    private val _events = Channel<AdsUiEvent>(Channel.BUFFERED)
    val events: Flow<AdsUiEvent> = _events.receiveAsFlow()

    // ------------------------------------------------------------ interstitial

    /** Call when a level is WON (counts toward every-Nth-win). Daily wins are ignored. */
    fun onLevelWon(isDaily: Boolean) = recordLevelWon(isDaily)

    /** Win dialog "Level N+1": maybe show an interstitial BEFORE [onDone] (navigates). Ad-free through Lv 9. */
    fun afterWin(level: Int, isDaily: Boolean, onDone: () -> Unit) =
        run(AdTrigger.LEVEL_WON_CONTINUE, level, isDaily, onDone)

    /** Lose dialog Retry: policy keeps this OFF by default; still routed so the seam is live. */
    fun beforeRetry(level: Int, isDaily: Boolean, onDone: () -> Unit) =
        run(AdTrigger.LOSE_RETRY, level, isDaily, onDone)

    private fun run(trigger: AdTrigger, level: Int, isDaily: Boolean, onDone: () -> Unit) {
        if (!busy.compareAndSet(false, true)) return
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
    }

    // ------------------------------------------------------------ rewarded

    /**
     * Keep a rewarded ad loaded. Call when any booster is empty, on the last heart, and when the lose dialog shows.
     * Policy (ad-free levels, consent, SDK) is decided in the use case; the preloader refills itself after each show.
     */
    fun preloadRewardedAd() = preloadRewarded(surfaces.value.playerLevel.coerceAtLeast(screenLevel.value))

    /** Booster dialog "Watch Video": on EARNED grant +1 [booster] to inventory, then [onGranted] (game refreshes charges). */
    fun watchBoosterAd(booster: Booster, onGranted: () -> Unit) {
        watch(onEarned = {
            grantBoosterReward(booster)
            analytics.log(AnalyticsEvent.RewardedEarned("booster_${booster.name.lowercase()}"))
            onGranted()
        })
    }

    /** Lose dialog "Watch Ad": on EARNED call [onGranted] (game revives with +1 life and continues). */
    fun watchReviveAd(onGranted: () -> Unit) {
        watch(onEarned = {
            analytics.log(AnalyticsEvent.RewardedEarned("revive"))
            onGranted()
        })
    }

    private fun watch(onEarned: suspend () -> Unit) {
        if (!busy.compareAndSet(false, true)) return
        _rewardedBusy.value = true
        viewModelScope.launch {
            try {
                when (showRewarded(surfaces.value.playerLevel.coerceAtLeast(screenLevel.value))) {
                    RewardedResult.EARNED -> onEarned()
                    RewardedResult.DISMISSED -> _events.trySend(AdsUiEvent.RewardNotEarned)
                    RewardedResult.NOT_READY, RewardedResult.FAILED -> _events.trySend(AdsUiEvent.RewardedUnavailable)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _events.trySend(AdsUiEvent.RewardedUnavailable)
            } finally {
                busy.set(false)
                _rewardedBusy.value = false
                preloadRewardedAd() // next one, if a booster is still empty the screen keeps asking too
            }
        }
    }

    // ------------------------------------------------------------ banner

    private val _banner = MutableStateFlow<BannerAdHandle?>(null)

    /** Loaded sticky banner, or null (loading, failed, no consent, ad-free level, off). */
    val banner: StateFlow<BannerAdHandle?> = _banner.asStateFlow()

    private var bannerJob: Job? = null
    private var bannerFailedAtMs: Long? = null

    /** Load the banner once for [widthDp] (no-op when held / loading / failed within the last minute). */
    fun requestBanner(widthDp: Int) {
        if (_banner.value != null || bannerJob?.isActive == true) return
        val failedAt = bannerFailedAtMs
        if (failedAt != null && time.monotonicMillis() - failedAt < BANNER_RETRY_AFTER_FAIL_MS) return
        bannerJob = viewModelScope.launch {
            val handle = try {
                loadBanner(surfaces.value.playerLevel.coerceAtLeast(screenLevel.value), widthDp)
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
                bannerFailedAtMs = time.monotonicMillis()
            } else {
                bannerFailedAtMs = null
                _banner.value = handle
            }
        }
    }

    /** Destroy the held banner and cancel an in-flight load (surface turned off). */
    fun releaseBanner() {
        bannerJob?.cancel()
        bannerJob = null
        _banner.value?.destroy()
        _banner.value = null
    }

    override fun onCleared() {
        releaseBanner()
    }

    private companion object {
        const val BANNER_RETRY_AFTER_FAIL_MS = 60_000L
    }
}
