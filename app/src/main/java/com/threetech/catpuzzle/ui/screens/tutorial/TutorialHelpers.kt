package com.threetech.catpuzzle.ui.screens.tutorial

import androidx.annotation.StringRes
import com.threetech.catpuzzle.R
import com.threetech.catpuzzle.domain.tutorial.TutorialNotice
import com.threetech.catpuzzle.domain.tutorial.TutorialTextKey
import kotlin.math.PI
import kotlin.math.sin

@StringRes
fun TutorialTextKey.res(): Int = when (this) {
    TutorialTextKey.PLACE_FIRST -> R.string.tutorial_place_first
    TutorialTextKey.ONE_PER_COLOR -> R.string.tutorial_one_per_color
    TutorialTextKey.ROW_COLUMN -> R.string.tutorial_row_column
    TutorialTextKey.NO_TOUCH -> R.string.tutorial_no_touch
    TutorialTextKey.LAST_SPOT -> R.string.tutorial_last_spot
    TutorialTextKey.HEARTS -> R.string.tutorial_hearts
    TutorialTextKey.TRY_HINT -> R.string.tutorial_try_hint
    TutorialTextKey.HINT_SHOWN -> R.string.tutorial_hint_shown
    TutorialTextKey.TRY_CAT_BOOSTER -> R.string.tutorial_try_cat_booster
    TutorialTextKey.DONE -> R.string.tutorial_done
}

@StringRes
fun TutorialNotice.res(): Int = when (this) {
    TutorialNotice.WRONG_ACTION -> R.string.tutorial_notice_wrong
    TutorialNotice.TAP_TWICE -> R.string.tutorial_notice_tap_twice
}

/** `**word**` marks a red accent word. Unbalanced markers: the trailing text is plain. */
object AccentText {
    data class Segment(val text: String, val accent: Boolean)

    fun split(raw: String): List<Segment> {
        val parts = raw.split("**")
        val out = ArrayList<Segment>(parts.size)
        for ((i, part) in parts.withIndex()) {
            if (part.isEmpty()) continue
            // Odd parts sit between two markers. A dangling last odd part has no closing marker.
            val accent = i % 2 == 1 && i != parts.lastIndex
            out.add(Segment(part, accent))
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
