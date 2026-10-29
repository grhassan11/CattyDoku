package com.threetech.catpuzzle.data.ads

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
import com.threetech.catpuzzle.BuildConfig
import com.threetech.catpuzzle.data.platform.CurrentActivityProvider
import com.threetech.catpuzzle.domain.ads.AdSessionTracker
import com.threetech.catpuzzle.domain.ads.AdsSdkStatus
import com.threetech.catpuzzle.domain.ads.RewardedAdRepository
import com.threetech.catpuzzle.domain.ads.RewardedLoadState
import com.threetech.catpuzzle.domain.ads.RewardedResult
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
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
) : RewardedAdRepository {

    private val unitId: String get() = BuildConfig.ADMOB_REWARDED_ID

    override val isConfigured: Boolean get() = !unitId.contains(PLACEHOLDER_MARK)

    private val _loadState = MutableStateFlow(RewardedLoadState.IDLE)
    override val loadState: StateFlow<RewardedLoadState> = _loadState

    private val lock = Any()
    private var started = false
    private var startedAtMs = 0L

    override fun preload() {
        if (!isConfigured || !status.isInitialized.value) return
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
            _loadState.value = RewardedLoadState.FAILED
        }
    }

    private fun startPreloader() {
        started = true
        startedAtMs = tracker.now()
        _loadState.value = RewardedLoadState.LOADING
        val config = PreloadConfiguration(AdRequest.Builder(unitId).build(), BUFFER_SIZE)
        RewardedAdPreloader.start(
            unitId,
            config,
            object : PreloadCallback {
                override fun onAdPreloaded(preloadId: String, responseInfo: ResponseInfo) {
                    _loadState.value = RewardedLoadState.READY
                }

                override fun onAdFailedToPreload(preloadId: String, adError: LoadAdError) {
                    _loadState.value = RewardedLoadState.FAILED
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
                suspendCancellableCoroutine { cont ->
                    val earned = AtomicBoolean(false)
                    ad.adEventCallback = object : RewardedAdEventCallback {
                        override fun onAdShowedFullScreenContent() = Unit
                        override fun onAdImpression() = Unit

                        // Click-out (browser / store): the matching foreground must not trigger an app-open ad.
                        override fun onAdClicked() = tracker.markAdClicked()

                        override fun onAdDismissedFullScreenContent() {
                            if (cont.isActive) cont.resume(if (earned.get()) RewardedResult.EARNED else RewardedResult.DISMISSED)
                        }

                        override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
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
