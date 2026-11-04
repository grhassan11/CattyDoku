package com.tm.meow.sudoku.puzzle.ui.screens.tutorial

import androidx.annotation.StringRes
import com.tm.meow.sudoku.puzzle.R
import com.tm.meow.sudoku.puzzle.domain.tutorial.TutorialBottomKey
import com.tm.meow.sudoku.puzzle.domain.tutorial.TutorialNotice
import com.tm.meow.sudoku.puzzle.domain.tutorial.TutorialTextKey
import kotlin.math.PI
import kotlin.math.sin

@StringRes
fun TutorialTextKey.res(): Int = when (this) {
    TutorialTextKey.PLACE_FIRST -> R.string.tutorial_place_first
    TutorialTextKey.ONE_PER_COLOR -> R.string.tutorial_one_per_color
    TutorialTextKey.ROW_COLUMN -> R.string.tutorial_row_column
    TutorialTextKey.ROSE_LEFT -> R.string.tutorial_rose_left
    TutorialTextKey.ADJACENT -> R.string.tutorial_adjacent
    TutorialTextKey.BLUE_LEFT -> R.string.tutorial_blue_left
    TutorialTextKey.ROW_DONE -> R.string.tutorial_row_done
    TutorialTextKey.FIND_LAST -> R.string.tutorial_find_last
    TutorialTextKey.EXCELLENT -> R.string.tutorial_excellent
}

/** Smaller second line under the main card text, or null. */
@StringRes
fun TutorialTextKey.subtitleRes(): Int? = when (this) {
    TutorialTextKey.FIND_LAST -> R.string.tutorial_find_last_sub
    else -> null
}

@StringRes
fun TutorialBottomKey.res(): Int = when (this) {
    TutorialBottomKey.TAP_EMPTY -> R.string.tutorial_bottom_tap_empty
    TutorialBottomKey.SWIPE_ACROSS -> R.string.tutorial_bottom_swipe
}

@StringRes
fun TutorialNotice.res(): Int = when (this) {
    TutorialNotice.WRONG_ACTION -> R.string.tutorial_notice_wrong
    TutorialNotice.TAP_TWICE -> R.string.tutorial_notice_tap_twice
}

/**
 * Card text markup: the double-star pair marks a red accent word, the double-tilde pair marks a word
 * painted in the step's region colour. Unbalanced markers: the trailing text is plain.
 */
object AccentText {
    data class Segment(val text: String, val accent: Boolean, val tint: Boolean = false)

    fun split(raw: String): List<Segment> {
        val out = ArrayList<Segment>()
        for (seg in splitBy(raw, "**")) {
            if (seg.second) {
                out.add(Segment(seg.first, accent = true))
            } else {
                for (inner in splitBy(seg.first, "~~")) out.add(Segment(inner.first, accent = false, tint = inner.second))
            }
        }
        return out
    }

    /** Parts of [raw] split on [marker]; the flag is true for a part between two markers. Empty parts dropped. */
    private fun splitBy(raw: String, marker: String): List<Pair<String, Boolean>> {
        val parts = raw.split(marker)
        val out = ArrayList<Pair<String, Boolean>>(parts.size)
        for ((i, part) in parts.withIndex()) {
            if (part.isEmpty()) continue
            // Odd parts sit between two markers. A dangling last odd part has no closing marker.
            out.add(part to (i % 2 == 1 && i != parts.lastIndex))
        }
        return out
    }
}

/** Looping finger animation, pure so it is testable. [t] is loop progress 0..1. */
object PointerMotion {
    private const val PRESSED_SCALE = 0.78f

    /** Finger scale: 1 = lifted, [PRESSED_SCALE] = pressed. */
    fun pressScale(kind: PointerKind, t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return when (kind) {
            PointerKind.TAP -> 1f - (1f - PRESSED_SCALE) * bump(x, 0.35f, 0.55f)
            PointerKind.DOUBLE_TAP -> 1f - (1f - PRESSED_SCALE) *
                maxOf(bump(x, 0.20f, 0.35f), bump(x, 0.45f, 0.60f))
            PointerKind.SWIPE -> if (x in 0.12f..0.88f) PRESSED_SCALE else 1f
        }
    }

    /** Progress 0..1 along the swipe path; 0 for tap kinds. Holds at both ends. */
    fun travel(kind: PointerKind, t: Float): Float {
        if (kind != PointerKind.SWIPE) return 0f
        val x = ((t.coerceIn(0f, 1f) - 0.15f) / 0.70f).coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private fun bump(x: Float, from: Float, to: Float): Float {
        if (x < from || x > to) return 0f
        return sin(PI.toFloat() * (x - from) / (to - from))
    }
}
