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

    /** Device has no validated internet connection (set by ObserveAdSurfaces, not the repository). CTAs stay visible, disabled, with a helper text. */
    OFFLINE,
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
    /** Levels game-screen compact native slot may be reserved. Driven by remote `levels_game_native_ad_show`. */
    val levelsGameNative: Boolean = false,
    /** Sudoku game-screen compact native slot may be reserved. Independent of [levelsGameNative] and of the ad-free level gate. Remote `sudoku_game_native_ad_show`. */
    val sudokuGameNative: Boolean = false,
    /**
     * Game-screen native slot is shown (skeleton or ad): ONLY remote `levels_game_native_ad_show` / `sudoku_game_native_ad_show`
     * on and no Remove Ads purchase. Stays true offline / no consent / SDK not ready (skeleton stays, no request:
     * [levelsGameNative] / [sudokuGameNative] = "may request").
     */
    val levelsGameNativeSlot: Boolean = false,
    val sudokuGameNativeSlot: Boolean = false,
    /** Remove Ads owned. The game native slots are already folded to false; rewarded is unaffected. Informational for the UI. */
    val adsRemoved: Boolean = false,
    /** Validated internet connection present. Rewarded CTAs stay visible but show a helper text and are disabled while false. */
    val online: Boolean = true,
)
