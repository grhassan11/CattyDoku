package com.tm.meow.sudoku.puzzle.domain.ads

import kotlinx.coroutines.flow.StateFlow

/** Preload state of the rewarded ad, as the UI needs it. */
enum class RewardedLoadState {
    /** Nothing requested yet (or SDK off). */
    IDLE,

    /** Preload in flight. */
    LOADING,

    /** An ad is ready to show now. */
    READY,

    /** Last preload failed (no fill / network). A new [RewardedAdRepository.preload] retries. */
    FAILED,
}

/** Outcome of showing a rewarded ad. Reward is granted ONLY on [EARNED], never on dismiss. */
enum class RewardedResult {
    /** Shown and the SDK reported earned-reward before close. */
    EARNED,

    /** Shown but closed before the reward was earned. */
    DISMISSED,

    /** No ad ready / SDK not initialised / no foreground activity. Nothing was shown. */
    NOT_READY,

    /** SDK reported a show failure. Nothing usable was shown. */
    FAILED,
}

/** Rewarded ad SDK boundary (Next-Gen preload API). Impl lives in data/ads. */
interface RewardedAdRepository {
    /** False when the release unit id is still the placeholder (ads off for this format). */
    val isConfigured: Boolean

    val loadState: StateFlow<RewardedLoadState>

    /** Idempotent. Starts the preloader on first call; after a failure it restarts it (throttled). Never throws. */
    fun preload()

    /** Polls the preloaded ad and shows it. Never throws. The preloader refills itself afterwards. */
    suspend fun show(): RewardedResult
}

/** Opaque loaded banner. The SDK object lives behind it; the UI adapter (ui/ads) hosts its view. */
interface BannerAdHandle {
    /** Release the SDK ad. Idempotent. */
    fun destroy()

    /** True once [destroy] ran (also when the UI host destroyed it on Activity recreation). Destroyed handles must not be hosted. */
    val isDestroyed: Boolean get() = false
}

/** Which screen hosts a banner. Each placement has its own remote kill switch and gate (see LoadBannerAd / ObserveAdSurfaces). */
enum class BannerPlacement {
    /** Levels (Queens) game screen: ad-free early levels apply. */
    LEVELS_GAME,

    /** Sudoku game screen: no level gate (Sudoku has no level number). */
    SUDOKU_GAME,
}

/** Anchored adaptive banner SDK boundary. Impl lives in data/ads. */
interface BannerAdRepository {
    /** False when the release unit id is still the placeholder. */
    val isConfigured: Boolean

    /** One anchored adaptive banner [widthDp] wide, or null (SDK not ready, no fill, timeout). Never throws. */
    suspend fun load(widthDp: Int): BannerAdHandle?
}

/**
 * Which ad surfaces the game screen may show right now. Everything (kill switch, ad-free early levels, consent, SDK init,
 * real unit ids) is already folded in: UI shows a surface iff its flag is true.
 */
data class AdSurfaces(
    /** Level used for the ad-free rule (max of the screen's level and the last played level). */
    val playerLevel: Int = 0,
    /** Levels booster play badge / dialog may appear. */
    val levelsBoosters: Boolean = false,
    /** Levels lose-screen revive CTA may appear. */
    val levelsRevive: Boolean = false,
    /** Sudoku out-of-hints dialog Watch button may appear. */
    val sudokuBoosters: Boolean = false,
    /** Sudoku game-over revive CTA may appear. */
    val sudokuRevive: Boolean = false,
    val rewardedLoad: RewardedLoadState = RewardedLoadState.IDLE,
    /** Sticky banner slot may be reserved on the game screen. */
    val banner: Boolean = false,
    /** Sudoku game-screen banner slot may be reserved. Independent of [banner] and of the ad-free level gate. */
    val sudokuBanner: Boolean = false,
    /** Remove Ads owned. Banner is already folded to false; rewarded is unaffected. Informational for the UI. */
    val adsRemoved: Boolean = false,
) {
    /** Any Levels rewarded surface is live (drives Levels preload). */
    val levelsRewarded: Boolean get() = levelsBoosters || levelsRevive

    /** Any Sudoku rewarded surface is live (drives Sudoku preload). */
    val sudokuRewarded: Boolean get() = sudokuBoosters || sudokuRevive
}
