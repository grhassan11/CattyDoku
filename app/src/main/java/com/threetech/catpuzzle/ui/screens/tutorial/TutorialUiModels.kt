package com.threetech.catpuzzle.ui.screens.tutorial

import androidx.compose.runtime.Immutable
import com.threetech.catpuzzle.domain.model.Pos
import com.threetech.catpuzzle.domain.tutorial.TutorialFocus
import com.threetech.catpuzzle.domain.tutorial.TutorialNotice
import com.threetech.catpuzzle.domain.tutorial.TutorialTextKey
import com.threetech.catpuzzle.ui.screens.game.BoardUi
import com.threetech.catpuzzle.ui.screens.game.HapticKind
import com.threetech.catpuzzle.ui.screens.game.SfxKind

enum class PointerKind { TAP, DOUBLE_TAP, SWIPE }

/**
 * Animated finger. [from] / [to] null = the finger sits on the focused booster button instead of a
 * board cell. SWIPE travels from [from] to [to].
 */
@Immutable
data class TutorialPointerUi(val kind: PointerKind, val from: Pos?, val to: Pos?)

@Immutable
data class TutorialUiState(
    val stepIndex: Int,
    val stepCount: Int,
    val text: TutorialTextKey,
    val notice: TutorialNotice?,
    val board: BoardUi,
    /** Flat indices of cells kept bright. Empty = no per-cell dimming. */
    val spotlight: Set<Int>,
    val pointer: TutorialPointerUi?,
    val focus: TutorialFocus,
    val hearts: Int,
    val maxHearts: Int,
    val hintCharges: Int,
    val catCharges: Int,
    val showContinue: Boolean,
    /** Continue button is the final "Let's play". */
    val continueIsFinal: Boolean,
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
