package com.tm.meow.sudoku.puzzle.domain.usecase

import com.tm.meow.sudoku.puzzle.domain.ads.AdFormat
import com.tm.meow.sudoku.puzzle.domain.ads.AdFreePolicy
import com.tm.meow.sudoku.puzzle.domain.ads.AdPolicyConfig
import com.tm.meow.sudoku.puzzle.domain.ads.AdSessionTracker
import com.tm.meow.sudoku.puzzle.domain.ads.GameNativeAdCache
import com.tm.meow.sudoku.puzzle.domain.ads.NativeAdHandle
import com.tm.meow.sudoku.puzzle.domain.ads.NativeAdRepository
import com.tm.meow.sudoku.puzzle.domain.repository.AppFlagsRepository
import com.tm.meow.sudoku.puzzle.domain.billing.AdsRemovedRepository
import com.tm.meow.sudoku.puzzle.domain.billing.NoAdsRemoved
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentRepository
import com.tm.meow.sudoku.puzzle.domain.network.AlwaysOnline
import com.tm.meow.sudoku.puzzle.domain.network.ConnectivityObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Screens that host a native ad; each has its own remote switch.
 * [LEVELS_GAME] / [SUDOKU_GAME] = the compact native slot on the game screens. Each has its own
 * switch (remote keys `levels_game_native_ad_show` / `sudoku_game_native_ad_show` -> AdPolicyConfig.levelsGameNativeShow /
 * sudokuGameNativeShow). Neither has a level gate ([LEVELS_GAME] shows from Level 1).
 */
enum class AdPlacement { LEVELS_HOME, SUDOKU_HOME, LEVELS_GAME, SUDOKU_GAME }

/** Load the native ad for an [AdPlacement] if policy + consent allow. Null = show nothing. Never throws. */
class LoadNativeAd(
    private val consent: ConsentRepository,
    private val repository: NativeAdRepository,
    private val config: AdPolicyConfig,
    private val adsRemoved: AdsRemovedRepository = NoAdsRemoved,
    private val connectivity: ConnectivityObserver = AlwaysOnline,
) {
    /** Single policy gate: false = no slot at all (kill switch, Remove Ads, offline, no consent, SDK off / placeholder ID). */
    fun isAllowed(placement: AdPlacement): Boolean {
        val unitOn = when (placement) {
            AdPlacement.LEVELS_HOME -> config.levelsHomeNativeShow
            AdPlacement.SUDOKU_HOME -> config.sudokuHomeNativeShow
            AdPlacement.LEVELS_GAME -> config.levelsGameNativeShow
            AdPlacement.SUDOKU_GAME -> config.sudokuGameNativeShow
        }
        if (!unitOn) return false
        // Remove Ads: never even request the ad.
        val format = when (placement) {
            AdPlacement.LEVELS_HOME, AdPlacement.SUDOKU_HOME -> AdFormat.NATIVE_HOME
            AdPlacement.LEVELS_GAME, AdPlacement.SUDOKU_GAME -> AdFormat.NATIVE_GAME
        }
        if (AdFreePolicy.isSuppressedByPurchase(format, adsRemoved.adsRemoved.value)) return false
        if (!connectivity.isOnline.value) return false
        if (!consent.state.value.canRequestAds) return false
        return repository.isConfigured
    }

    /** [isAllowed] re-evaluated when consent, Remove Ads, SDK init or connectivity change (so a late SDK init still gets an ad). */
    fun allowedChanges(placement: AdPlacement): Flow<Boolean> =
        combine(consent.state, adsRemoved.adsRemoved, repository.configuredChanges, connectivity.isOnline) { _, _, _, _ -> isAllowed(placement) }
            .distinctUntilChanged()

    /**
     * [playerLevel] is ignored (native ads have no level gate; kept for call-site compatibility).
     */
    suspend operator fun invoke(placement: AdPlacement, playerLevel: Int = Int.MAX_VALUE): NativeAdHandle? {
        if (!isAllowedAt(placement, playerLevel)) return null
        return try {
            repository.load()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Same rule [invoke] applies. No level gate for any placement: the Levels game native shows from Level 1
     * ([playerLevel] is kept for call-site compatibility only and is ignored).
     */
    @Suppress("UNUSED_PARAMETER")
    fun isAllowedAt(placement: AdPlacement, playerLevel: Int = Int.MAX_VALUE): Boolean = isAllowed(placement)
}

/**
 * Preload the native ad for any [AdPlacement] (Home placements from Splash, game placements from Home) into
 * [GameNativeAdCache]. Same gates as [LoadNativeAd] run BEFORE any request (consent, Remove Ads, remote switch, offline,
 * SDK; no level gate); gated = nothing requested. Idempotent, never throws.
 */
class PreloadGameNativeAd(
    private val loadNative: LoadNativeAd,
    private val cache: GameNativeAdCache,
    private val flags: AppFlagsRepository,
) {
    operator fun invoke(placement: AdPlacement) {
        if (!loadNative.isAllowed(placement)) return // cheap gate: no coroutine, no flags read
        cache.preload(placement) {
            loadNative(placement) // re-checks every gate right before the request
        }
    }
}

/**
 * Home / game-screen entry: hand over the cached native ad if the gates still allow it (waits briefly for a preload that is
 * still in flight instead of racing it with a second request). Null = caller falls back to [LoadNativeAd].
 */
class TakeGameNativeAd(
    private val loadNative: LoadNativeAd,
    private val cache: GameNativeAdCache,
) {
    suspend operator fun invoke(placement: AdPlacement, playerLevel: Int): NativeAdHandle? {
        if (!loadNative.isAllowedAt(placement, playerLevel)) return null
        cache.take(placement)?.let { return it }
        withTimeoutOrNull(AWAIT_PRELOAD_MS) { cache.awaitPreload(placement) }
        return cache.take(placement)
    }

    private companion object {
        /** Same cap as the skeleton on Home. */
        const val AWAIT_PRELOAD_MS = 6_000L
    }
}

/** Home reached: the start flow (Splash with consent, Tutorial) is over, app-open may now fire. */
class MarkStartFlowComplete(private val tracker: AdSessionTracker) {
    operator fun invoke() {
        tracker.startFlowComplete = true
    }
}
