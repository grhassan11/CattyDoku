package com.threetech.catpuzzle.data.ads

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
import com.threetech.catpuzzle.BuildConfig
import com.threetech.catpuzzle.data.platform.CurrentActivityProvider
import com.threetech.catpuzzle.domain.ads.AdSessionTracker
import com.threetech.catpuzzle.domain.ads.AdShowResult
import com.threetech.catpuzzle.domain.ads.AdsSdkStatus
import com.threetech.catpuzzle.domain.ads.AppOpenAdRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * GMA Next-Gen app-open (com.google.android.libraries.ads.mobile.sdk.appopen). Holds at most one loaded ad; each ad is
 * single-use. Fields are @Volatile: preload / show run on Main, load callbacks may arrive elsewhere.
 * Release placeholder unit ID = off. Foreground Activity comes from [CurrentActivityProvider].
 */
@Singleton
class AppOpenAdRepositoryImpl @Inject constructor(
    private val status: AdsSdkStatus,
    private val activities: CurrentActivityProvider,
    private val tracker: AdSessionTracker,
) : AppOpenAdRepository {

    @Volatile private var ad: AppOpenAd? = null

    @Volatile private var loadedAtMs: Long = 0L

    @Volatile private var loading = false

    private val idUsable: Boolean get() = !BuildConfig.ADMOB_APP_OPEN_ID.contains(PLACEHOLDER_MARK)

    override fun preload() {
        if (!status.isInitialized.value || !idUsable) return
        if (loading || ad != null) return
        loading = true
        try {
            AppOpenAd.load(
                AdRequest.Builder(BuildConfig.ADMOB_APP_OPEN_ID).build(),
                object : AdLoadCallback<AppOpenAd> {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        this@AppOpenAdRepositoryImpl.ad?.destroy()
                        this@AppOpenAdRepositoryImpl.ad = ad
                        loadedAtMs = tracker.now()
                        loading = false
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        loading = false
                        if (BuildConfig.DEBUG) Log.d(TAG, "preload failed: ${adError.code} ${adError.message}")
                    }
                },
            )
        } catch (t: Throwable) {
            loading = false
        }
    }

    override fun readyAdAgeMs(): Long? = if (ad == null) null else tracker.now() - loadedAtMs

    override fun discard() {
        ad?.destroy()
        ad = null
    }

    override suspend fun show(): AdShowResult {
        val taken = ad ?: return AdShowResult.NOT_READY
        ad = null // single-use: consumed even if the show fails
        return try {
            withContext(Dispatchers.Main.immediate) {
                val activity = awaitResumedActivity()
                if (activity == null) {
                    taken.destroy()
                    return@withContext AdShowResult.FAILED
                }
                val showed = CompletableDeferred<Unit>()
                val result = CompletableDeferred<AdShowResult>()
                taken.adEventCallback = object : AppOpenAdEventCallback {
                    override fun onAdShowedFullScreenContent() {
                        showed.complete(Unit)
                    }

                    // Click-out (browser / store): the matching foreground must not trigger another app-open ad.
                    override fun onAdClicked() = tracker.markAdClicked()

                    override fun onAdDismissedFullScreenContent() {
                        result.complete(AdShowResult.SHOWN)
                    }

                    override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                        result.complete(AdShowResult.FAILED)
                    }
                }
                try {
                    taken.show(activity)
                } catch (t: Throwable) {
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

    private companion object {
        const val TAG = "AppOpenAd"
        const val POLL_MS = 50L
        const val RESUME_WAIT_MS = 1_500L
        const val SHOW_START_TIMEOUT_MS = 8_000L

        /** Release placeholder IDs contain this (see app/build.gradle.kts). */
        const val PLACEHOLDER_MARK = "0000000000000000"
    }
}
