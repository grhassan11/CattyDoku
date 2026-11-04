package com.tm.meow.sudoku.puzzle.ui.screens.tutorial

import androidx.compose.runtime.Immutable
import com.tm.meow.sudoku.puzzle.domain.model.Pos
import com.tm.meow.sudoku.puzzle.domain.tutorial.TutorialBottomKey
import com.tm.meow.sudoku.puzzle.domain.tutorial.TutorialNotice
import com.tm.meow.sudoku.puzzle.domain.tutorial.TutorialTextKey
import com.tm.meow.sudoku.puzzle.ui.screens.game.BoardUi
import com.tm.meow.sudoku.puzzle.ui.screens.game.HapticKind
import com.tm.meow.sudoku.puzzle.ui.screens.game.SfxKind

enum class PointerKind { TAP, DOUBLE_TAP, SWIPE }

/** Animated finger over board cells. SWIPE travels along [path] (cell centres in order). */
@Immutable
data class TutorialPointerUi(
    val kind: PointerKind,
    val from: Pos,
    val to: Pos,
    val path: List<Pos> = listOf(from, to),
)

@Immutable
data class TutorialUiState(
    val stepIndex: Int,
    val stepCount: Int,
    val text: TutorialTextKey,
    val bottom: TutorialBottomKey?,
    /** Region id painting the tinted words of the card text, or null. */
    val tintRegion: Int?,
    val notice: TutorialNotice?,
    val board: BoardUi,
    /** Flat indices of the ringed cells (empty = nothing ringed). */
    val spotlight: Set<Int>,
    /** Flat indices of the cells that react to touch; every other cell is gated off. */
    val interactive: Set<Int>,
    val pointer: TutorialPointerUi?,
    /** Bright background, no dim. */
    val light: Boolean,
    /** Show the grey "Tap here for a hint" pill. */
    val hintPill: Boolean,
    val showContinue: Boolean,
    /** Continue button is the final "Start Game" (also shows confetti + Replay). */
    val continueIsFinal: Boolean,
    val showSkip: Boolean,
    /** Bumps on every wrong action; the UI shakes on change. */
    val rejectCount: Int,
    val patternMode: Boolean,
)

/** One-shot effects (Channel). */
sealed interface TutorialUiEvent {
    /** Already filtered by the haptics setting. */
    data class Haptic(val kind: HapticKind) : TutorialUiEvent

    /** Already filtered by the sfx setting. */
    data class Sfx(val kind: SfxKind) : TutorialUiEvent

    /** Flag is saved: leave the tutorial. */
    data object Finished : TutorialUiEvent
}
