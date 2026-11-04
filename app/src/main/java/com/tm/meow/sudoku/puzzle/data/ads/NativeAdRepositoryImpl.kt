package com.tm.meow.sudoku.puzzle.data.ads

import com.google.android.libraries.ads.mobile.sdk.common.AdChoicesPlacement
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.VideoOptions
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.tm.meow.sudoku.puzzle.BuildConfig
import com.tm.meow.sudoku.puzzle.ads.GmaNativeAdHandle
import com.tm.meow.sudoku.puzzle.domain.ads.AdSessionTracker
import com.tm.meow.sudoku.puzzle.domain.ads.AdsSdkStatus
import com.tm.meow.sudoku.puzzle.domain.ads.NativeAdHandle
import com.tm.meow.sudoku.puzzle.domain.ads.NativeAdRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * GMA Next-Gen native loader (com.google.android.libraries.ads.mobile.sdk.nativead). One ad per [load]; the caller owns
 * and destroys it. Needs [AdsSdkStatus.isInitialized] (UMP allowed ads). Release placeholder unit ID = ads off.
 */
@Singleton
class NativeAdRepositoryImpl @Inject constructor(
    private val status: AdsSdkStatus,
    private val tracker: AdSessionTracker,
) : NativeAdRepository {

    override val isConfigured: Boolean
        get() = status.isInitialized.value && !BuildConfig.ADMOB_NATIVE_ID.contains(PLACEHOLDER_MARK)

    override val configuredChanges: Flow<Boolean> = status.isInitialized.map { isConfigured }

    override suspend fun load(): NativeAdHandle? {
        if (!isConfigured) return null
        return try {
            withContext(Dispatchers.Main.immediate) { withTimeoutOrNull(LOAD_TIMEOUT_MS) { request() } }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            null
        }
    }

    private suspend fun request(): NativeAdHandle? = suspendCancellableCoroutine { cont ->
        val request = NativeAdRequest.Builder(BuildConfig.ADMOB_NATIVE_ID, listOf(NativeAd.NativeAdType.NATIVE))
            .setVideoOptions(VideoOptions.Builder().setStartMuted(true).build())
            // Top-right: admob_native_hundred.xml keeps that corner clear (headline row ends 20dp from the edge; "Ad" badge sits left of the headline; icon is top-left).
            .setAdChoicesPlacement(AdChoicesPlacement.TOP_RIGHT)
            .build()
        NativeAdLoader.load(
            request,
            object : NativeAdLoaderCallback {
                override fun onNativeAdLoaded(nativeAd: NativeAd) {
                    nativeAd.adEventCallback = object : NativeAdEventCallback {
                        // Click-out (browser / store): the matching foreground must not trigger an app-open ad.
                        override fun onAdClicked() = tracker.markAdClicked()
                    }
                    val handle = GmaNativeAdHandle(nativeAd)
                    if (cont.isActive) cont.resume(handle) else handle.destroy() // timed out / cancelled meanwhile
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    if (cont.isActive) cont.resume(null)
                }
            },
        )
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 15_000L

        /** Release placeholder IDs contain this (see app/build.gradle.kts). */
        const val PLACEHOLDER_MARK = "0000000000000000"
    }
}
