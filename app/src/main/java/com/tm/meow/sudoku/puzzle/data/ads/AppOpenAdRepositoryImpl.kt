package com.tm.meow.sudoku.puzzle.data.ads

import android.app.Activity
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.tm.meow.sudoku.puzzle.BuildConfig
import com.tm.meow.sudoku.puzzle.data.platform.CurrentActivityProvider
import com.tm.meow.sudoku.puzzle.di.MainDispatcher
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentRepository
import com.tm.meow.sudoku.puzzle.domain.ads.AdSessionTracker
import com.tm.meow.sudoku.puzzle.domain.ads.AdShowResult
import com.tm.meow.sudoku.puzzle.domain.ads.AdsSdkStatus
import com.tm.meow.sudoku.puzzle.domain.ads.AppOpenAdRepository
import com.tm.meow.sudoku.puzzle.domain.analytics.AdFormat
import com.tm.meow.sudoku.puzzle.domain.analytics.AdFunnelTracker
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.analytics.NoopAnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.crash.CrashReporter
import com.tm.meow.sudoku.puzzle.domain.crash.NoopCrashReporter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * GMA Next-Gen app-open (com.google.android.libraries.ads.mobile.sdk.appopen). Holds at most one loaded ad; each ad is
 * single-use. Fields are @Volatile: preload / show run on Main, load callbacks may arrive elsewhere.
 * A placeholder unit ID (contains zeros mark) = off; all build types currently use Google TEST ids. Foreground Activity comes from [CurrentActivityProvider].
 */
@Singleton
class AppOpenAdRepositoryImpl @Inject constructor(
    private val status: AdsSdkStatus,
    private val activities: CurrentActivityProvider,
    private val tracker: AdSessionTracker,
    private val consent: ConsentRepository,
    @MainDispatcher private val main: CoroutineDispatcher,
    private val crash: CrashReporter = NoopCrashReporter,
    analytics: AnalyticsLogger = NoopAnalyticsLogger,
) : AppOpenAdRepository {

    private val funnel = AdFunnelTracker(analytics, crash)

    @Volatile private var ad: AppOpenAd? = null

    @Volatile private var loadedAtMs: Long = 0L

    @Volatile private var loading = false

    private val idUsable: Boolean get() = !BuildConfig.ADMOB_APP_OPEN_ID.contains(PLACEHOLDER_MARK)

    private val scope = CoroutineScope(SupervisorJob() + main)
    private var consentWatch = false

    /** Consent withdrawn mid-session: drop the held ad (loads only start again through the gated [preload]). */
    private fun watchConsent() {
        if (consentWatch) return
        consentWatch = true
        scope.launch {
            consent.state.map { it.canRequestAds }.distinctUntilChanged().collect { allowed ->
                if (!allowed) discard()
            }
        }
    }

    override fun preload() {
        if (!consent.state.value.canRequestAds) {
            dbg("preload skip: no consent")
            return
        }
        watchConsent()
        if (!status.isInitialized.value || !idUsable) {
            dbg("preload skip: sdkInit=${status.isInitialized.value} idUsable=$idUsable")
            return
        }
        if (loading || ad != null) {
            dbg("preload skip: loading=$loading held=${ad != null}")
            return
        }
        loading = true
        dbg("preload: AppOpenAd.load start")
        val requestMs = tracker.now()
        funnel.requested(AdFormat.APP_OPEN)
        try {
            AppOpenAd.load(
                AdRequest.Builder(BuildConfig.ADMOB_APP_OPEN_ID).build(),
                object : AdLoadCallback<AppOpenAd> {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        if (!consent.state.value.canRequestAds) { // consent withdrawn while the load was in flight
                            ad.destroy()
                            loading = false
                            return
                        }
                        this@AppOpenAdRepositoryImpl.ad?.destroy()
                        this@AppOpenAdRepositoryImpl.ad = ad
                        loadedAtMs = tracker.now()
                        loading = false
                        funnel.loaded(AdFormat.APP_OPEN, loadedAtMs - requestMs)
                        dbg("preload: loaded")
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        loading = false
                        funnel.failed(AdFormat.APP_OPEN, adError.code.toString(), tracker.now() - requestMs)
                        dbg("preload failed: ${adError.code} ${adError.message}")
                    }
                },
            )
        } catch (t: Throwable) {
            dbg("preload threw: $t")
            crash.recordNonFatal(t, "appopen.preload")
            loading = false
        }
    }

    override fun isLoading(): Boolean = loading

    override fun readyAdAgeMs(): Long? = if (ad == null) null else tracker.now() - loadedAtMs

    override fun discard() {
        ad?.destroy()
        ad = null
    }

    override suspend fun show(): AdShowResult {
        val taken = ad ?: run {
            dbg("show NOT_READY: no held ad")
            return AdShowResult.NOT_READY
        }
        ad = null // single-use: consumed even if the show fails
        return try {
            withContext(main) {
                val activity = awaitResumedActivity()
                if (activity == null) {
                    dbg("show FAILED: no RESUMED activity within ${RESUME_WAIT_MS}ms")
                    taken.destroy()
                    return@withContext AdShowResult.FAILED
                }
                val showed = CompletableDeferred<Unit>()
                val result = CompletableDeferred<AdShowResult>()
                taken.adEventCallback = object : AppOpenAdEventCallback {
                    override fun onAdShowedFullScreenContent() {
                        funnel.shown(AdFormat.APP_OPEN, BuildConfig.ADMOB_APP_OPEN_ID)
                        showed.complete(Unit)
                    }

                    // Click-out (browser / store): the matching foreground must not trigger another app-open ad.
                    override fun onAdClicked() {
                        tracker.markAdClicked()
                        funnel.clicked(AdFormat.APP_OPEN)
                    }

                    override fun onAdDismissedFullScreenContent() {
                        funnel.dismissed(AdFormat.APP_OPEN)
                        result.complete(AdShowResult.SHOWN)
                    }

                    override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                        funnel.showFailed()
                        dbg("show failed: ${fullScreenContentError.message}")
                        result.complete(AdShowResult.FAILED)
                    }
                }
                try {
                    taken.show(activity)
                } catch (t: Throwable) {
                    crash.recordNonFatal(t, "appopen.show")
                    taken.destroy()
                    return@withContext AdShowResult.FAILED
                }
                // Watchdog: if the SDK never reports showed / failed, do not leave the "ad showing" flag stuck.
                val started = withTimeoutOrNull(SHOW_START_TIMEOUT_MS) {
                    while (!showed.isCompleted && !result.isCompleted) delay(POLL_MS)
                    true
                }
                val outcome = if (started == null) AdShowResult.FAILED else result.await()
                taken.destroy()
                outcome
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            crash.recordNonFatal(t, "appopen.show.outer")
            AdShowResult.FAILED
        }
    }

    /** Process ON_START fires before Activity.onResume: wait briefly for a RESUMED app activity. */
    private suspend fun awaitResumedActivity(): Activity? {
        var waited = 0L
        while (waited <= RESUME_WAIT_MS) {
            val a = activities.current()
            val resumed = (a as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
            if (a != null && resumed) return a
            delay(POLL_MS)
            waited += POLL_MS
        }
        return null
    }

    private fun dbg(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "app-open repo: $msg")
    }

    private companion object {
        const val TAG = "MeowAds"
        const val POLL_MS = 50L
        const val RESUME_WAIT_MS = 1_500L
        const val SHOW_START_TIMEOUT_MS = 8_000L

        /** Release placeholder IDs contain this (see app/build.gradle.kts). */
        const val PLACEHOLDER_MARK = "0000000000000000"
    }
}
