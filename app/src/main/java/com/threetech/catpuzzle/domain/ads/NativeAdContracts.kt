package com.threetech.catpuzzle.domain.ads

import kotlinx.coroutines.flow.StateFlow

/** Opaque loaded native ad. The SDK object lives behind it; the UI adapter (ui/ads) binds it to a view. */
interface NativeAdHandle {
    /** Release the SDK ad. Idempotent. Call exactly when the ad leaves the screen for good. */
    fun destroy()
}

/** Native ad SDK boundary. Impl lives in data/ads. */
interface NativeAdRepository {
    /**
     * False when this build can never serve a native ad (SDK not initialized, placeholder unit ID). Cheap, no I/O.
     * Default true so test fakes need not override it.
     */
    val isConfigured: Boolean get() = true

    /** One native ad, or null (SDK not ready, placeholder ID, no fill, timeout). Never throws. */
    suspend fun load(): NativeAdHandle?
}

/**
 * Home native slot lifecycle.
 * [Hidden] ads not allowed (consent, purchase, kill switch, SDK off): no slot at all.
 * [Loading] request in flight: the slot is reserved and the UI shows a skeleton.
 * [Loaded] ad ready. [Failed] no fill / timeout: slot collapsed, retried later by the owner.
 */
sealed interface NativeAdSlotState {
    data object Hidden : NativeAdSlotState
    data object Loading : NativeAdSlotState
    data class Loaded(val ad: NativeAdHandle) : NativeAdSlotState
    data object Failed : NativeAdSlotState

    /** True while the fixed-height bottom slot occupies layout space (skeleton or real ad). */
    val reservesSlot: Boolean get() = this is Loading || this is Loaded
}

/** Whether `MobileAds.initialize` has been called (after UMP allowed ads). Shared by native + app-open. */
interface AdsSdkStatus {
    val isInitialized: StateFlow<Boolean>
}
