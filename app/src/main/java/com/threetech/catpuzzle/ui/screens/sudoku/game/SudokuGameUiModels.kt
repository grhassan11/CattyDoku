package com.threetech.catpuzzle.ui.screens.sudoku.game

import androidx.compose.runtime.Immutable
import com.threetech.catpuzzle.domain.sudoku.model.SudokuTier

/** Background class of a cell. Priority when several apply: SELECTED > SAME_DIGIT > PEER. */
enum class CellHighlight { NONE, PEER, SAME_DIGIT, SELECTED }

/** One board cell. [notes] = bitmask, bit (d - 1) set = pencil digit d. [wrong] = player digit differing from the solution. */
@Immutable
data class SudokuCellUi(
    val value: Int,
    val notes: Int,
    val given: Boolean,
    val wrong: Boolean,
    val highlight: CellHighlight,
)

/** Number pad key: [remaining] = how many of [digit] are still to be placed correctly. */
@Immutable
data class SudokuPadKeyUi(val digit: Int, val remaining: Int) {
    val enabled: Boolean get() = remaining > 0
}

@Immutable
data class SudokuWinUi(
    val timeText: String,
    val score: Int,
    val mistakes: Int,
    val hintsUsed: Int,
    val newBestTime: Boolean,
    val newBestScore: Boolean,
    val unlocked: List<SudokuTier>,
)

/** Modal shown over the board. Restart / Help / HintNeeded pause the clock while open. */
sealed interface SudokuDialogUi {
    data object None : SudokuDialogUi
    data object ConfirmRestart : SudokuDialogUi
    data object Help : SudokuDialogUi
    data object HintNeeded : SudokuDialogUi
    data class Won(val win: SudokuWinUi) : SudokuDialogUi
    data class GameOver(val mistakes: Int) : SudokuDialogUi

    /** True for the dialogs that dismiss back to the board. */
    val pausesClock: Boolean get() = this is ConfirmRestart || this is Help || this is HintNeeded
}

/** Everything the stateless game screen draws, except the clock text (separate flow: ticks every second). */
@Immutable
data class SudokuGameUiState(
    val loading: Boolean = true,
    val tier: SudokuTier = SudokuTier.EASY,
    val n: Int = 4,
    val boxRows: Int = 2,
    val boxCols: Int = 2,
    val cells: List<SudokuCellUi> = emptyList(),
    val selected: Int = -1,
    val score: Int = 0,
    val hearts: Int = 3,
    val maxHearts: Int = 3,
    /** User pause overlay (board hidden). Lifecycle / dialog pauses do not set it. */
    val paused: Boolean = false,
    val notesMode: Boolean = false,
    val canUndo: Boolean = false,
    val canErase: Boolean = false,
    /** Playing and not paused: board + tools accept input. */
    val inputEnabled: Boolean = false,
    val hintCharges: Int = 0,
    val padKeys: List<SudokuPadKeyUi> = emptyList(),
    val dialog: SudokuDialogUi = SudokuDialogUi.None,
)

enum class SudokuExitReason { NONE, LOCKED, FAILED }

/** One-shot events (Channel.receiveAsFlow). */
sealed interface SudokuGameEvent {
    /** Hint tapped with an empty shared HINT stock: the route decides ad dialog vs message. */
    data object NoHintCharge : SudokuGameEvent

    /** Leave the screen (emitted at most once). */
    data class Exit(val reason: SudokuExitReason) : SudokuGameEvent
}
