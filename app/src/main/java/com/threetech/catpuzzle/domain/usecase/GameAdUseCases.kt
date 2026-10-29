package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.ads.AdFormat
import com.threetech.catpuzzle.domain.ads.AdFreePolicy
import com.threetech.catpuzzle.domain.ads.AdPolicyConfig
import com.threetech.catpuzzle.domain.ads.AdSessionTracker
import com.threetech.catpuzzle.domain.ads.AdSurfaces
import com.threetech.catpuzzle.domain.ads.AdsSdkStatus
import com.threetech.catpuzzle.domain.ads.BannerAdHandle
import com.threetech.catpuzzle.domain.ads.BannerAdRepository
import com.threetech.catpuzzle.domain.ads.RewardedAdRepository
import com.threetech.catpuzzle.domain.ads.RewardedResult
import com.threetech.catpuzzle.domain.billing.AdsRemovedRepository
import com.threetech.catpuzzle.domain.billing.NoAdsRemoved
import com.threetech.catpuzzle.domain.consent.ConsentRepository
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.domain.model.GameState
import com.threetech.catpuzzle.domain.model.GameStatus
import com.threetech.catpuzzle.domain.repository.AppFlagsRepository
import com.threetech.catpuzzle.domain.repository.InventoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/*
 * Game-screen ad use cases: rewarded (booster refill + lose revive) and the sticky banner.
 * Every format shares ONE early-level rule: AdFreePolicy / AdPolicyConfig.adFreeThroughLevel.
 */

/**
 * Which ad surfaces are live for the game screen. [screenLevel] = level on screen (0 = unknown / daily); the player's
 * last played level (persisted progress) is folded in with max(), so Daily can never show ads earlier than levels do.
 * Re-emits when consent, SDK init, the last played level or the rewarded preload state changes.
 */
class ObserveAdSurfaces(
    private val flags: AppFlagsRepository,
    private val consent: ConsentRepository,
    private val sdk: AdsSdkStatus,
    private val rewarded: RewardedAdRepository,
    private val banner: BannerAdRepository,
    private val config: AdPolicyConfig,
    private val adsRemovedRepository: AdsRemovedRepository = NoAdsRemoved,
) {
    operator fun invoke(screenLevel: Flow<Int>): Flow<AdSurfaces> =
        combine(
            flags.flags.map { it.lastPlayedLevel }.distinctUntilChanged(),
            screenLevel,
            combine(consent.state, adsRemovedRepository.adsRemoved) { c, removed -> c to removed },
            sdk.isInitialized,
            rewarded.loadState,
        ) { flagLevel, screen, consentAndRemoved, initialized, load ->
            val consentState = consentAndRemoved.first
            val removed = consentAndRemoved.second
            val level = maxOf(flagLevel, screen)
            val base = config.enabled && consentState.canRequestAds && initialized &&
                AdFreePolicy.adsAllowed(level, config.adFreeThroughLevel)
            AdSurfaces(
                playerLevel = level,
                rewarded = base && config.rewardedEnabled && rewarded.isConfigured,
                rewardedLoad = load,
                // Remove Ads suppresses the banner; rewarded is never suppressed (AdFreePolicy.isSuppressedByPurchase).
                banner = base && config.bannerGameEnabled && banner.isConfigured &&
                    !AdFreePolicy.isSuppressedByPurchase(AdFormat.BANNER, removed),
                adsRemoved = removed,
            )
        }.distinctUntilChanged()
}

/** Start (or retry) the rewarded preload when policy allows. Idempotent, never throws. */
class PreloadRewardedAd(
    private val consent: ConsentRepository,
    private val sdk: AdsSdkStatus,
    private val repository: RewardedAdRepository,
    private val config: AdPolicyConfig,
) {
    operator fun invoke(playerLevel: Int) {
        try {
            if (!config.enabled || !config.rewardedEnabled || !repository.isConfigured) return
            if (AdFreePolicy.isAdFree(playerLevel, config.adFreeThroughLevel)) return
            if (!consent.state.value.canRequestAds || !sdk.isInitialized.value) return
            repository.preload()
        } catch (t: Throwable) {
            // fail open
        }
    }
}

/**
 * Show the rewarded ad. Re-checks policy at the moment of the tap (kill switch, ad-free level, consent, SDK). Marks the
 * process full-screen-ad-showing while it runs (blocks app-open) and, when an ad actually showed, starts the shared
 * full-screen cool-down ([AdSessionTracker.recordRewardedShown]). Returns [RewardedResult.NOT_READY] when blocked.
 * The caller grants the reward iff the result is [RewardedResult.EARNED]. Never throws (except cancellation).
 */
class ShowRewardedAd(
    private val consent: ConsentRepository,
    private val sdk: AdsSdkStatus,
    private val repository: RewardedAdRepository,
    private val tracker: AdSessionTracker,
    private val config: AdPolicyConfig,
) {
    suspend operator fun invoke(playerLevel: Int): RewardedResult {
        if (!config.enabled || !config.rewardedEnabled || !repository.isConfigured) return RewardedResult.NOT_READY
        if (AdFreePolicy.isAdFree(playerLevel, config.adFreeThroughLevel)) return RewardedResult.NOT_READY
        if (!consent.state.value.canRequestAds || !sdk.isInitialized.value) return RewardedResult.NOT_READY
        return try {
            tracker.fullScreenAdShowing = true
            val result = try {
                repository.show()
            } finally {
                tracker.fullScreenAdShowing = false
            }
            if (result == RewardedResult.EARNED || result == RewardedResult.DISMISSED) tracker.recordRewardedShown()
            result
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            RewardedResult.FAILED
        }
    }
}

/** Reward: +[amount] of a [Booster] into persistent inventory (the source of truth for charges). */
class GrantBoosterReward(private val inventory: InventoryRepository) {
    suspend operator fun invoke(booster: Booster, amount: Int = 1) {
        if (amount > 0) inventory.add(booster, amount)
    }
}

/**
 * Reward: revive a lost game with one life (+1 fish) so the level continues. Only a LOST state revives; anything else
 * returns null (no-op). Board, undo history, score inputs and charges are kept.
 */
class ReviveGame {
    operator fun invoke(state: GameState): GameState? =
        if (state.status == GameStatus.LOST) state.copy(hearts = REVIVE_HEARTS, status = GameStatus.PLAYING) else null

    companion object {
        const val REVIVE_HEARTS = 1
    }
}

/** Load the game-screen anchored adaptive banner if policy + consent allow. Null = show nothing. Never throws. */
class LoadBannerAd(
    private val consent: ConsentRepository,
    private val sdk: AdsSdkStatus,
    private val repository: BannerAdRepository,
    private val config: AdPolicyConfig,
    private val adsRemoved: AdsRemovedRepository = NoAdsRemoved,
) {
    suspend operator fun invoke(playerLevel: Int, widthDp: Int): BannerAdHandle? {
        if (!config.enabled || !config.bannerGameEnabled || !repository.isConfigured) return null
        // Remove Ads: never even request the banner.
        if (AdFreePolicy.isSuppressedByPurchase(AdFormat.BANNER, adsRemoved.adsRemoved.value)) return null
        if (AdFreePolicy.isAdFree(playerLevel, config.adFreeThroughLevel)) return null
        if (!consent.state.value.canRequestAds || !sdk.isInitialized.value || widthDp <= 0) return null
        return try {
            repository.load(widthDp)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            null
        }
    }
}
