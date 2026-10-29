package com.threetech.catpuzzle.ads

import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.threetech.catpuzzle.domain.ads.BannerAdHandle

/**
 * Loaded GMA Next-Gen banner. Lives in the neutral `ads` package (like [GmaNativeAdHandle]) so the data layer can create
 * it and the UI host (ui/ads/BannerAdSlot) can attach [ad]'s view without the UI importing the data layer.
 */
class GmaBannerAdHandle(val ad: BannerAd) : BannerAdHandle {
    @Volatile private var destroyed = false

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        try {
            ad.destroy()
        } catch (t: Throwable) {
            // already gone
        }
    }
}
