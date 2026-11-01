package com.threetech.catpuzzle.ui.screens.game

import androidx.compose.runtime.Immutable
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.domain.model.HintRule

/** UI-side cell mark (kept separate so UI never depends on engine enums for rendering decisions). */
enum class CellMarkUi { EMPTY, X, CAT }

/** How a hint relates to a cell. */
enum class HintRole { NONE, TARGET, REASON }

@Immutable
data class CellUi(
    val row: Int,
    val col: Int,
    /** Region id 0 until size. Color + pattern derive from it in the UI layer. */
    val region: Int,
    val mark: CellMarkUi,
    val conflict: Boolean,
    /** Booster-placed cat: cannot be changed. */
    val locked: Boolean,
    val hintRole: HintRole,
    /** Which placed-cat image this cell's region uses (0 until CatVariants.UNIQUE). */
    val catVariant: Int = 0,
)

@Immutable
data class BoardUi(val size: Int, val cells: List<CellUi>) {
    fun cell(row: Int, col: Int): CellUi = cells[row * size + col]
}

@Immutable
data class RegionStatusUi(val region: Int, val solved: Boolean, val catVariant: Int = 0)

@Immutable
data class BoostersUi(
    val catCharges: Int,
    val hintCharges: Int,
    val thirdLocked: Boolean,
    val thirdUnlockLevel: Int,
    /** Mouse stock (third booster slot badge once unlocked). */
    val mouseCharges: Int = 0,
)

/** Hint text keys; the composable maps them to string resources. */
enum class HintMessage {
    WRONG_CAT,
    X_ON_SOLUTION,
    PLACE_SINGLE,
    PLACE_REVEAL,
    ELIMINATE_CONFINEMENT,
    ELIMINATE_CROWDING,
    ELIMINATE_LOOKAHEAD,
}

@Immutable
data class HintUi(val message: HintMessage, val rule: HintRule? = null)

/**
 * Hint dim overlay: everything dimmed except the [spotlight] cells (flat board indices), plus the rule card
 * (rule line from [HintUi.rule], explanation from [HintUi.message]).
 */
@Immutable
data class HintOverlayUi(val hint: HintUi, val spotlight: Set<Int>)

/** Finished-level result driving the win / lose dialogs. Lives in ViewModel state, so it survives rotation. */
sealed interface OutcomeUi {
    @Immutable
    data class Won(
        val score: Int,
        val elapsedSeconds: Int,
        val heartsLeft: Int,
        val hintsUsed: Int,
    ) : OutcomeUi

    @Immutable
    data class Lost(val score: Int) : OutcomeUi
}

/** Settings as the dialog shows them. */
@Immutable
data class SettingsUi(
    val music: Boolean,
    val sfx: Boolean,
    val voice: Boolean,
    val haptics: Boolean,
    val patternMode: Boolean,
    val streakReminders: Boolean = true,
    val dailyReminders: Boolean = true,
)

/** Which setting a dialog toggle flips. */
enum class SettingToggle { MUSIC, SFX, VOICE, HAPTICS, PATTERN_MODE, STREAK_REMINDERS, DAILY_REMINDERS }

@Immutable
data class GameContentUi(
    val levelNumber: Int,
    val score: Int,
    val hearts: Int,
    val maxHearts: Int,
    val board: BoardUi,
    val regions: List<RegionStatusUi>,
    val boosters: BoostersUi,
    val hint: HintUi?,
    /** Non-null while the dim + rule card is up (hint just used, not yet dismissed). */
    val hintOverlay: HintOverlayUi? = null,
    val canUndo: Boolean,
    val patternMode: Boolean,
    val settings: SettingsUi,
    val settingsBadge: Boolean,
    val outcome: OutcomeUi?,
    /** Daily Challenge mode: top bar shows the date, win dialog says "Daily complete!". [levelNumber] is 0. */
    val isDaily: Boolean = false,
    /** Local date (epoch day) of the daily being played; null for a normal level. */
    val dailyEpochDay: Long? = null,
    /** Mouse booster flight in progress (X marks already in [board]; the screen hides them until the mouse lands). */
    val mouseRun: MouseRunUi? = null,
    /** Latest correct cat placement (drives the board's pop / sparkle / ripple / flash). Null until the first one this session. */
    val lastCorrectPlacement: CorrectPlacementUi? = null,
)

/**
 * A cat the player (or the cat booster) just placed on its solution cell with no conflict. [token] is unique and
 * increasing per placement: the board plays the effect once per new token and never for restored / loaded state.
 */
@Immutable
data class CorrectPlacementUi(val row: Int, val col: Int, val token: Int)

/** One mouse booster flight: [id] is unique per use, [cells] the target tiles in visit order. */
@Immutable
data class MouseRunUi(val id: Int, val cells: List<com.threetech.catpuzzle.domain.model.Pos>)

sealed interface GameUiState {
    @Immutable
    data class Loading(val levelNumber: Int?) : GameUiState

    @Immutable
    data class Failed(val levelNumber: Int, val reason: String) : GameUiState

    @Immutable
    data class Ready(val content: GameContentUi) : GameUiState
}

enum class HapticKind { TICK, PLACE, WRONG, WIN, LOSE }

/** Sound cues the UI can play. */
enum class SfxKind {
    /** Cat placement that is not a clean correct one (e.g. conflicting); tutorial placement. */
    MEOW,

    /** Soft chime on a correct, conflict-free cat placement (non-winning). Reuses the hint sample, quieter + higher pitch. */
    CHIME,

    /** Cat placed on a non-solution cell. */
    WRONG,

    /** Hint used. */
    HINT,

    /** Level won. */
    WIN,

    /** Milestone win (levels 1 / 5 / 10 / 25), played instead of [WIN]. */
    AWARD,

    /** Action refused (no booster charge, locked cell, tutorial wrong action). */
    BLOCKED,

    /** UI button / dialog / toggle tap. Played via the audio controller, not from the game events. */
    UI_TAP,

    /** X mark placed on a board cell. */
    CROSS,

    /** Mouse booster used. */
    MOUSE,
}

/** One-shot effects (delivered through a Channel). */
sealed interface GameUiEvent {
    /** Already filtered by the haptics setting: UI just performs it. */
    data class Haptic(val kind: HapticKind) : GameUiEvent

    /** Already filtered by the sfx setting: UI just plays it. */
    data class Sfx(val kind: SfxKind) : GameUiEvent

    /** Booster tapped with no charges left. */
    data class NoCharges(val booster: Booster) : GameUiEvent

    data class LevelWon(val level: Int, val score: Int) : GameUiEvent

    data class LevelLost(val level: Int) : GameUiEvent
}

/** Ad surfaces the game screen may draw right now. Policy is already folded in (ad-free levels, consent, kill switches). */
@Immutable
data class GameAdsUi(
    /** Booster play badge / rewarded dialog / lose revive CTA allowed. */
    val rewardedSurface: Boolean = false,
    val rewardedLoad: com.threetech.catpuzzle.domain.ads.RewardedLoadState = com.threetech.catpuzzle.domain.ads.RewardedLoadState.IDLE,
    /** A rewarded request / ad is in flight: CTAs inert. */
    val rewardedBusy: Boolean = false,
    /** Fixed-height banner slot is reserved at the bottom (layout stays stable even before the ad loads). */
    val bannerReserved: Boolean = false,
    /** Loaded banner to host, or null (still loading / failed / hidden behind a dialog). */
    val bannerAd: com.threetech.catpuzzle.domain.ads.BannerAdHandle? = null,
)
