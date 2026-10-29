package com.threetech.catpuzzle.data.ads

import android.content.Context
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.threetech.catpuzzle.BuildConfig
import com.threetech.catpuzzle.ads.GmaBannerAdHandle
import com.threetech.catpuzzle.domain.ads.AdSessionTracker
import com.threetech.catpuzzle.domain.ads.AdsSdkStatus
import com.threetech.catpuzzle.domain.ads.BannerAdHandle
import com.threetech.catpuzzle.domain.ads.BannerAdRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * GMA Next-Gen anchored adaptive banner (com.google.android.libraries.ads.mobile.sdk.banner). One ad per [load]; the
 * caller owns and destroys it. Size = current-orientation anchored adaptive for the given width. Refresh is left to the
 * AdMob console setting. Needs [AdsSdkStatus.isInitialized]. Release placeholder unit ID = off.
 */
@Singleton
class BannerAdRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val status: AdsSdkStatus,
    private val tracker: AdSessionTracker,
) : BannerAdRepository {

    override val isConfigured: Boolean get() = !BuildConfig.ADMOB_BANNER_ID.contains(PLACEHOLDER_MARK)

    override suspend fun load(widthDp: Int): BannerAdHandle? {
        if (!status.isInitialized.value || !isConfigured) return null
        return try {
            withContext(Dispatchers.Main.immediate) { withTimeoutOrNull(LOAD_TIMEOUT_MS) { request(widthDp) } }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            null
        }
    }

    private suspend fun request(widthDp: Int): BannerAdHandle? = suspendCancellableCoroutine { cont ->
        val size = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
        val request = BannerAdRequest.Builder(BuildConfig.ADMOB_BANNER_ID, size).build()
        BannerAd.load(
            request,
            object : AdLoadCallback<BannerAd> {
                override fun onAdLoaded(ad: BannerAd) {
                    ad.adEventCallback = object : BannerAdEventCallback {
                        // Click-out (browser / store): the matching foreground must not trigger an app-open ad.
                        override fun onAdClicked() = tracker.markAdClicked()
                    }
                    val handle = GmaBannerAdHandle(ad)
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
