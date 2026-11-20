package com.tm.meow.sudoku.puzzle.ui.screens.game

import androidx.compose.runtime.Immutable
import com.tm.meow.sudoku.puzzle.domain.model.Booster
import com.tm.meow.sudoku.puzzle.domain.model.HintRule
import com.tm.meow.sudoku.puzzle.domain.model.PieceSkin

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
    /** X left by a wrong cat: drawn in the wrong-move red instead of the normal X colour. UI-only. */
    val wrongX: Boolean = false,
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
    val sudokuReminders: Boolean = true,
    val reduceEffects: Boolean = false,
)

/** Which setting a dialog toggle flips. */
enum class SettingToggle { MUSIC, SFX, VOICE, HAPTICS, PATTERN_MODE, STREAK_REMINDERS, DAILY_REMINDERS, SUDOKU_REMINDERS, REDUCE_EFFECTS }

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
    val outcome: OutcomeUi?,
    /** Daily Challenge mode: top bar shows the date, win dialog says "Daily complete!". [levelNumber] is 0. */
    val isDaily: Boolean = false,
    /** Local date (epoch day) of the daily being played; null for a normal level. */
    val dailyEpochDay: Long? = null,
    /** Mouse booster flight in progress (X marks already in [board]; the screen hides them until the mouse lands). */
    val mouseRun: MouseRunUi? = null,
    /** Latest correct cat placement (drives the board's pop / sparkle / ripple / flash). Null until the first one this session. */
    val lastCorrectPlacement: CorrectPlacementUi? = null,
    /** What the pieces look like: cats, or one animal set on a boss level (board pieces + Cat booster icon + region row). */
    val pieceSkin: PieceSkin = PieceSkin.cats(),
    /** Bumped on every Retry of the same level so the board intro wave replays. */
    val restartCount: Int = 0,
    /** Latest region completed by a clean placement (drives clear wave / burst / tab pop). Token-gated like [lastCorrectPlacement]. */
    val regionCleared: RegionClearedUi? = null,
    /** Cosmetic in-run streak of consecutive correct placements (combo chip). Not persisted, not in the score. */
    val comboStreak: Int = 0,
)

/**
 * A cat the player (or the cat booster) just placed on its solution cell with no conflict. [token] is unique and
 * increasing per placement: the board plays the effect once per new token and never for restored / loaded state.
 */
@Immutable
data class CorrectPlacementUi(
    val row: Int,
    val col: Int,
    val token: Int,
    /** Score gained by this placement (domain delta, before vs after). 0 = no popup. */
    val points: Int = 0,
    /** Praise word to flash with this placement (streak milestone, else "Clear!"). Null = none. */
    val praise: PraiseKind? = null,
    /** Combo streak after this placement (0 for the cat booster): scales / recolours the "+N" popup. */
    val streak: Int = 0,
)

/**
 * A region just completed by the placement at ([row], [col]). [token] is unique and increasing; the UI plays the clear
 * effects once per new token and never for restored / loaded state.
 */
@Immutable
data class RegionClearedUi(val region: Int, val token: Int, val row: Int = -1, val col: Int = -1)

/**
 * Transient wrong-move feedback for the board (animation only, never persisted). [token] is unique and increasing per
 * move; [cells] are flat board indices. Only a cat placed on a wrong cell fires it (never an X).
 * [partners]: placed cats touching the wrong cell (a "no-touch" violation): they shake and pulse red too. Empty otherwise.
 */
@Immutable
data class WrongMoveUi(val token: Int, val cells: Set<Int>, val partners: Set<Int> = emptySet())

/** One mouse booster flight: [id] is unique per use, [cells] the target tiles in visit order. */
@Immutable
data class MouseRunUi(val id: Int, val cells: List<com.tm.meow.sudoku.puzzle.domain.model.Pos>)

sealed interface GameUiState {
    @Immutable
    data class Loading(val levelNumber: Int?) : GameUiState

    @Immutable
    data class Failed(val levelNumber: Int, val reason: String) : GameUiState

    @Immutable
    data class Ready(val content: GameContentUi) : GameUiState
}

enum class HapticKind { TICK, CORRECT, PLACE, WRONG, WIN, LOSE }

/** Sound cues the UI can play. */
enum class SfxKind {
    /** Cat placement that is not a clean correct one (e.g. conflicting); tutorial placement. */
    MEOW,

    /** Soft chime on a correct, conflict-free cat placement (non-winning). Reuses the hint sample, quieter + higher pitch. */
    CHIME,

    /** Same chime as [CHIME] but with no cat meow layered on it (animal pieces on boss levels). */
    CHIME_SOLO,

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

    /** Already filtered by the sfx setting: UI just plays it. [rate] = playback-rate multiplier (streak pitch rise), 1 = normal. */
    data class Sfx(val kind: SfxKind, val rate: Float = 1f) : GameUiEvent

    /** Booster tapped with no charges left. */
    data class NoCharges(val booster: Booster) : GameUiEvent

    /** Mouse tapped but no empty non-cat cell is left to cross out. No charge spent. */
    data object MouseNoTarget : GameUiEvent

    /**
     * Visual-only wrong-move cue (red flash + broken heart on [cells], flat indices). Not filtered by sound / haptic settings;
     * the UI skips it when system animations are off. Never changes game state.
     */
    data class WrongMove(val cells: Set<Int>) : GameUiEvent

    data class LevelWon(val level: Int, val score: Int) : GameUiEvent

    data class LevelLost(val level: Int) : GameUiEvent
}

/** Ad surfaces the game screen may draw right now. Policy is already folded in (ad-free levels, consent, kill switches). */
@Immutable
data class GameAdsUi(
    /** Booster play badge / rewarded dialog allowed (remote `levels_game_boosters_rewarded_ad_show`). */
    val rewardedSurface: Boolean = false,
    /** Lose-screen revive CTA allowed (remote `levels_game_revive_rewarded_ad_show`). */
    val reviveSurface: Boolean = false,
    val rewardedLoad: com.tm.meow.sudoku.puzzle.domain.ads.RewardedLoadState = com.tm.meow.sudoku.puzzle.domain.ads.RewardedLoadState.IDLE,
    /** A rewarded request / ad is in flight: CTAs inert. */
    val rewardedBusy: Boolean = false,
    /** Fixed-height (banner-sized) compact native slot is reserved at the bottom (layout stays stable even before the ad loads). */
    val nativeReserved: Boolean = false,
    /** Native slot content: Loading = skeleton, Loaded = ad, Hidden / Failed = blank (also while a dialog is open). */
    val nativeSlot: com.tm.meow.sudoku.puzzle.domain.ads.NativeAdSlotState = com.tm.meow.sudoku.puzzle.domain.ads.NativeAdSlotState.Hidden,
    /** Level Completed screen sticky native reserved (remote `levels_game_completed_native_ad_show`; Remove Ads folds it to false). */
    val completeNativeReserved: Boolean = false,
    val completeNativeSlot: com.tm.meow.sudoku.puzzle.domain.ads.NativeAdSlotState = com.tm.meow.sudoku.puzzle.domain.ads.NativeAdSlotState.Hidden,
)

/** Marks the given cell indices as red wrong-cat X's (UI-only flag). Returns this when empty. */
internal fun GameContentUi.withWrongX(indices: Set<Int>): GameContentUi {
    if (indices.isEmpty()) return this
    val cells = board.cells.mapIndexed { i, c -> if (i in indices && c.mark == CellMarkUi.X) c.copy(wrongX = true) else c }
    return copy(board = board.copy(cells = cells))
}
