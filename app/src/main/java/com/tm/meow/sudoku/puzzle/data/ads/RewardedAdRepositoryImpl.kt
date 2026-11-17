package com.tm.meow.sudoku.puzzle.data.ads

import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.PreloadCallback
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo
import com.google.android.libraries.ads.mobile.sdk.rewarded.OnUserEarnedRewardListener
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardItem
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdPreloader
import com.tm.meow.sudoku.puzzle.BuildConfig
import com.tm.meow.sudoku.puzzle.data.platform.CurrentActivityProvider
import com.tm.meow.sudoku.puzzle.di.MainDispatcher
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentRepository
import com.tm.meow.sudoku.puzzle.domain.ads.AdSessionTracker
import com.tm.meow.sudoku.puzzle.domain.ads.AdsSdkStatus
import com.tm.meow.sudoku.puzzle.domain.ads.RewardedAdRepository
import com.tm.meow.sudoku.puzzle.domain.ads.RewardedLoadState
import com.tm.meow.sudoku.puzzle.domain.ads.RewardedResult
import java.util.concurrent.atomic.AtomicBoolean
import com.tm.meow.sudoku.puzzle.domain.analytics.AdFormat
import com.tm.meow.sudoku.puzzle.domain.analytics.AdFunnelTracker
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.analytics.NoopAnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.crash.CrashReporter
import com.tm.meow.sudoku.puzzle.domain.crash.NoopCrashReporter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * GMA Next-Gen rewarded (com.google.android.libraries.ads.mobile.sdk.rewarded) via the preload API
 * ([RewardedAdPreloader.start] / [RewardedAdPreloader.pollAd]). The preloader keeps its buffer (size 1) filled, so after a
 * poll the next ad loads on its own ("preload next after dismissal"). Started lazily by [preload], not at SDK init:
 * the game only asks once a booster is empty / last heart / lose dialog. Release placeholder unit ID = off.
 * Reward = [OnUserEarnedRewardListener] fired; a plain dismiss is [RewardedResult.DISMISSED] (no reward).
 */
@Singleton
class RewardedAdRepositoryImpl @Inject constructor(
    private val status: AdsSdkStatus,
    private val activities: CurrentActivityProvider,
    private val tracker: AdSessionTracker,
    private val consent: ConsentRepository,
    @MainDispatcher main: CoroutineDispatcher,
    private val crash: CrashReporter = NoopCrashReporter,
    analytics: AnalyticsLogger = NoopAnalyticsLogger,
) : RewardedAdRepository {

    private val funnel = AdFunnelTracker(analytics, crash)
    private var requestAtMs = 0L

    private val unitId: String get() = BuildConfig.ADMOB_REWARDED_ID

    override val isConfigured: Boolean get() = !unitId.contains(PLACEHOLDER_MARK)

    private val _loadState = MutableStateFlow(RewardedLoadState.IDLE)
    override val loadState: StateFlow<RewardedLoadState> = _loadState

    private val lock = Any()
    private var started = false
    private var startedAtMs = 0L
    private val scope = CoroutineScope(SupervisorJob() + main)
    private var consentWatch = false

    /** Consent withdrawn mid-session: destroy the preloader so it stops refilling. [preload] restarts it once consent is back. */
    private fun watchConsent() {
        if (consentWatch) return
        consentWatch = true
        scope.launch {
            consent.state.map { it.canRequestAds }.distinctUntilChanged().collect { allowed ->
                if (allowed) return@collect
                synchronized(lock) {
                    if (!started) return@collect
                    try {
                        RewardedAdPreloader.destroy(unitId)
                    } catch (t: Throwable) {
                        crash.recordNonFatal(t, "rewarded.stop")
                    }
                    started = false
                    _loadState.value = RewardedLoadState.IDLE
                }
            }
        }
    }

    override fun preload() {
        if (!isConfigured || !status.isInitialized.value) return
        if (!consent.state.value.canRequestAds) return
        watchConsent()
        try {
            synchronized(lock) {
                if (!started) {
                    startPreloader()
                    return
                }
                if (RewardedAdPreloader.isAdAvailable(unitId)) {
                    _loadState.value = RewardedLoadState.READY
                    return
                }
                // Failed earlier: restart the preloader, but not more often than every RETRY_MS.
                if (_loadState.value == RewardedLoadState.FAILED && tracker.now() - startedAtMs >= RETRY_MS) {
                    RewardedAdPreloader.destroy(unitId)
                    startPreloader()
                }
            }
        } catch (t: Throwable) {
            crash.recordNonFatal(t, "rewarded.preload")
            _loadState.value = RewardedLoadState.FAILED
        }
    }

    private fun startPreloader() {
        started = true
        startedAtMs = tracker.now()
        requestAtMs = startedAtMs
        funnel.requested(AdFormat.REWARDED)
        _loadState.value = RewardedLoadState.LOADING
        val config = PreloadConfiguration(AdRequest.Builder(unitId).build(), BUFFER_SIZE)
        RewardedAdPreloader.start(
            unitId,
            config,
            object : PreloadCallback {
                override fun onAdPreloaded(preloadId: String, responseInfo: ResponseInfo) {
                    _loadState.value = RewardedLoadState.READY
                    funnel.loaded(AdFormat.REWARDED, tracker.now() - requestAtMs)
                }

                override fun onAdFailedToPreload(preloadId: String, adError: LoadAdError) {
                    _loadState.value = RewardedLoadState.FAILED
                    funnel.failed(AdFormat.REWARDED, adError.code.toString(), tracker.now() - requestAtMs)
                }

                override fun onAdsExhausted(preloadId: String) {
                    if (_loadState.value == RewardedLoadState.READY) _loadState.value = RewardedLoadState.LOADING
                }
            },
        )
    }

    override suspend fun show(): RewardedResult {
        if (!status.isInitialized.value || !isConfigured) return RewardedResult.NOT_READY
        return try {
            withContext(Dispatchers.Main.immediate) {
                val activity = activities.current() ?: return@withContext RewardedResult.NOT_READY
                val ad = RewardedAdPreloader.pollAd(unitId)
                // Single-use ad is consumed; the preloader refills. Reflect the new buffer state right away.
                _loadState.value =
                    if (RewardedAdPreloader.isAdAvailable(unitId)) RewardedLoadState.READY else RewardedLoadState.LOADING
                if (ad == null) return@withContext RewardedResult.NOT_READY
                requestAtMs = tracker.now() // preloader refills after the poll
                funnel.requested(AdFormat.REWARDED)
                suspendCancellableCoroutine { cont ->
                    val earned = AtomicBoolean(false)
                    ad.adEventCallback = object : RewardedAdEventCallback {
                        override fun onAdShowedFullScreenContent() = funnel.shown(AdFormat.REWARDED, unitId)
                        override fun onAdImpression() = Unit

                        // Click-out (browser / store): the matching foreground must not trigger an app-open ad.
                        override fun onAdClicked() {
                            tracker.markAdClicked()
                            funnel.clicked(AdFormat.REWARDED)
                        }

                        override fun onAdDismissedFullScreenContent() {
                            funnel.dismissed(AdFormat.REWARDED)
                            if (cont.isActive) cont.resume(if (earned.get()) RewardedResult.EARNED else RewardedResult.DISMISSED)
                        }

                        override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                            funnel.showFailed()
                            if (cont.isActive) cont.resume(RewardedResult.FAILED)
                        }
                    }
                    ad.show(
                        activity,
                        object : OnUserEarnedRewardListener {
                            override fun onUserEarnedReward(rewardItem: RewardItem) {
                                earned.set(true)
                            }
                        },
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            crash.recordNonFatal(t, "rewarded.show")
            RewardedResult.FAILED
        }
    }

    private companion object {
        const val BUFFER_SIZE = 1
        const val RETRY_MS = 5_000L

        /** Release placeholder IDs contain this (see app/build.gradle.kts). */
        const val PLACEHOLDER_MARK = "0000000000000000"
    }
}
