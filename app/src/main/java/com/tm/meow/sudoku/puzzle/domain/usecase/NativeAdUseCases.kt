package com.tm.meow.sudoku.puzzle.domain.usecase

import com.tm.meow.sudoku.puzzle.domain.ads.AdFormat
import com.tm.meow.sudoku.puzzle.domain.ads.AdFreePolicy
import com.tm.meow.sudoku.puzzle.domain.ads.AdPolicyConfig
import com.tm.meow.sudoku.puzzle.domain.ads.AdSessionTracker
import com.tm.meow.sudoku.puzzle.domain.ads.NativeAdHandle
import com.tm.meow.sudoku.puzzle.domain.ads.NativeAdRepository
import com.tm.meow.sudoku.puzzle.domain.billing.AdsRemovedRepository
import com.tm.meow.sudoku.puzzle.domain.billing.NoAdsRemoved
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** Screens that host the bottom native ad; each has its own remote switch. */
enum class AdPlacement { LEVELS_HOME, SUDOKU_HOME }

/** Load the native ad for an [AdPlacement] if policy + consent allow. Null = show nothing. Never throws. */
class LoadNativeAd(
    private val consent: ConsentRepository,
    private val repository: NativeAdRepository,
    private val config: AdPolicyConfig,
    private val adsRemoved: AdsRemovedRepository = NoAdsRemoved,
) {
    /** Single policy gate: false = no slot at all (kill switch, Remove Ads, no consent, SDK off / placeholder ID). */
    fun isAllowed(placement: AdPlacement): Boolean {
        val unitOn = when (placement) {
            AdPlacement.LEVELS_HOME -> config.levelsHomeNativeShow
            AdPlacement.SUDOKU_HOME -> config.sudokuHomeNativeShow
        }
        if (!unitOn) return false
        // Remove Ads: never even request the ad.
        if (AdFreePolicy.isSuppressedByPurchase(AdFormat.NATIVE_HOME, adsRemoved.adsRemoved.value)) return false
        if (!consent.state.value.canRequestAds) return false
        return repository.isConfigured
    }

    /** [isAllowed] re-evaluated when consent, Remove Ads or SDK init change (so a late SDK init still gets an ad). */
    fun allowedChanges(placement: AdPlacement): Flow<Boolean> =
        combine(consent.state, adsRemoved.adsRemoved, repository.configuredChanges) { _, _, _ -> isAllowed(placement) }
            .distinctUntilChanged()

    suspend operator fun invoke(placement: AdPlacement): NativeAdHandle? {
        if (!isAllowed(placement)) return null
        return try {
            repository.load()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            null
        }
    }
}

/** Home reached: the start flow (Splash, Terms, consent, notification prompt, Tutorial) is over, app-open may now fire. */
class MarkStartFlowComplete(private val tracker: AdSessionTracker) {
    operator fun invoke() {
        tracker.startFlowComplete = true
    }
}
