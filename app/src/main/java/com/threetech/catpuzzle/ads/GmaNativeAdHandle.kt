package com.threetech.catpuzzle.ads

import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.threetech.catpuzzle.domain.ads.NativeAdHandle

/**
 * Loaded GMA Next-Gen native ad. Lives in the neutral `ads` package (not data, not ui) so the data layer can create it
 * and the UI view adapter (ui/ads/NativeAdBar) can bind [ad] without the UI importing the data layer.
 */
class GmaNativeAdHandle(val ad: NativeAd) : NativeAdHandle {
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
