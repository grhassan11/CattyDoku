package com.tm.meow.sudoku.puzzle.domain.usecase

import com.tm.meow.sudoku.puzzle.domain.ads.AdFormat
import com.tm.meow.sudoku.puzzle.domain.ads.AdFreePolicy
import com.tm.meow.sudoku.puzzle.domain.ads.AdLogger
import com.tm.meow.sudoku.puzzle.domain.ads.NoopAdLogger
import com.tm.meow.sudoku.puzzle.domain.ads.AdPolicySource
import com.tm.meow.sudoku.puzzle.domain.ads.AdSessionTracker
import com.tm.meow.sudoku.puzzle.domain.ads.AdSurfaces
import com.tm.meow.sudoku.puzzle.domain.ads.AdsSdkStatus
import com.tm.meow.sudoku.puzzle.domain.ads.NativeAdRepository
import com.tm.meow.sudoku.puzzle.domain.ads.RewardedLoadState
import com.tm.meow.sudoku.puzzle.domain.ads.RewardedAdRepository
import com.tm.meow.sudoku.puzzle.domain.ads.RewardedPlacement
import com.tm.meow.sudoku.puzzle.domain.ads.RewardedResult
import com.tm.meow.sudoku.puzzle.domain.billing.AdsRemovedRepository
import com.tm.meow.sudoku.puzzle.domain.billing.NoAdsRemoved
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentRepository
import com.tm.meow.sudoku.puzzle.domain.network.AlwaysOnline
import com.tm.meow.sudoku.puzzle.domain.network.ConnectivityObserver
import com.tm.meow.sudoku.puzzle.domain.model.Booster
import com.tm.meow.sudoku.puzzle.domain.model.GameState
import com.tm.meow.sudoku.puzzle.domain.model.GameStatus
import com.tm.meow.sudoku.puzzle.domain.repository.AppFlagsRepository
import com.tm.meow.sudoku.puzzle.domain.repository.InventoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/*
 * Game-screen ad use cases: rewarded (booster refill + lose revive) and the compact game-screen native slot (loaded by LoadNativeAd).
 * Rewarded and the game native slots have no early-level gate (adFreeThroughLevel is now unused by game ads).
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
    private val nativeAds: NativeAdRepository,
    private val config: AdPolicySource,
    private val adsRemovedRepository: AdsRemovedRepository = NoAdsRemoved,
    private val logger: AdLogger = NoopAdLogger,
    private val connectivity: ConnectivityObserver = AlwaysOnline,
) {
    operator fun invoke(screenLevel: Flow<Int>): Flow<AdSurfaces> =
        combine(
            flags.flags.map { it.lastPlayedLevel }.distinctUntilChanged(),
            screenLevel,
            combine(consent.state, adsRemovedRepository.adsRemoved, connectivity.isOnline) { c, removed, online -> Triple(c, removed, online) },
            sdk.isInitialized,
            rewarded.loadState,
        ) { flagLevel, screen, consentAndRemoved, initialized, load ->
            val consentState = consentAndRemoved.first
            val removed = consentAndRemoved.second
            val online = consentAndRemoved.third
            val level = maxOf(flagLevel, screen)
            val cfg = config.current() // one read per emission; re-evaluated on every upstream change
            // Game native slots (Levels + Sudoku): no level gate, Levels shows from Level 1. Remove Ads still suppresses them.
            val sudokuBase = consentState.canRequestAds && initialized
            // Rewarded: no early-level gate (Levels + Sudoku); only consent + SDK init + configured.
            val rewardedBase = sudokuBase && rewarded.isConfigured
            logger.log(
                "surfaces: level=$level adFreeThrough=${cfg.adFreeThroughLevel} canRequestAds=${consentState.canRequestAds} " +
                    "sdkInit=$initialized rewardedConfigured=${rewarded.isConfigured} boostersShow=${cfg.levelsBoostersRewardedShow} " +
                    "reviveShow=${cfg.levelsReviveRewardedShow} load=$load => levelsBoosters=${rewardedBase && cfg.levelsBoostersRewardedShow}",
            )
            AdSurfaces(
                playerLevel = level,
                levelsBoosters = rewardedBase && cfg.levelsBoostersRewardedShow,
                levelsRevive = rewardedBase && cfg.levelsReviveRewardedShow,
                sudokuBoosters = rewardedBase && cfg.sudokuBoostersRewardedShow,
                sudokuRevive = rewardedBase && cfg.sudokuReviveRewardedShow,
                rewardedLoad = if (online) load else RewardedLoadState.OFFLINE,
                // Remove Ads suppresses the game native slots; rewarded is never suppressed (AdFreePolicy.isSuppressedByPurchase).
                levelsGameNative = sudokuBase && cfg.levelsGameNativeShow && nativeAds.isConfigured && online &&
                    !AdFreePolicy.isSuppressedByPurchase(AdFormat.NATIVE_GAME, removed),
                sudokuGameNative = sudokuBase && cfg.sudokuGameNativeShow && nativeAds.isConfigured && online &&
                    !AdFreePolicy.isSuppressedByPurchase(AdFormat.NATIVE_GAME, removed),
                levelsGameNativeSlot = cfg.levelsGameNativeShow && !AdFreePolicy.isSuppressedByPurchase(AdFormat.NATIVE_GAME, removed),
                sudokuGameNativeSlot = cfg.sudokuGameNativeShow && !AdFreePolicy.isSuppressedByPurchase(AdFormat.NATIVE_GAME, removed),
                adsRemoved = removed,
                online = online,
            )
        }.distinctUntilChanged()
}

/** Start (or retry) the rewarded preload when policy allows. Idempotent, never throws. */
class PreloadRewardedAd(
    private val consent: ConsentRepository,
    private val sdk: AdsSdkStatus,
    private val repository: RewardedAdRepository,
    private val config: AdPolicySource,
    private val connectivity: ConnectivityObserver = AlwaysOnline,
) {
    operator fun invoke(playerLevel: Int) {
        try {
            if (!config.current().anyRewardedShow || !repository.isConfigured) return
            if (!connectivity.isOnline.value) return
            if (!consent.state.value.canRequestAds || !sdk.isInitialized.value) return
            repository.preload()
        } catch (t: Throwable) {
            // fail open
        }
    }
}

/**
 * Show the rewarded ad. Re-checks policy at the moment of the tap (kill switch, consent, SDK). Marks the
 * process full-screen-ad-showing while it runs (blocks app-open) and, when an ad actually showed, starts the shared
 * full-screen cool-down ([AdSessionTracker.recordRewardedShown]). Returns [RewardedResult.NOT_READY] when blocked.
 * The caller grants the reward iff the result is [RewardedResult.EARNED]. Never throws (except cancellation).
 */
class ShowRewardedAd(
    private val consent: ConsentRepository,
    private val sdk: AdsSdkStatus,
    private val repository: RewardedAdRepository,
    private val tracker: AdSessionTracker,
    private val config: AdPolicySource,
    private val connectivity: ConnectivityObserver = AlwaysOnline,
) {
    suspend operator fun invoke(playerLevel: Int, placement: RewardedPlacement): RewardedResult {
        if (!config.current().rewardedShowFor(placement) || !repository.isConfigured) return RewardedResult.NOT_READY
        if (!connectivity.isOnline.value) return RewardedResult.NOT_READY // offline: never attempt a load or show
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
