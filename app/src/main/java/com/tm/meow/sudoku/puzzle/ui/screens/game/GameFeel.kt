package com.tm.meow.sudoku.puzzle.ui.screens.game

/** Short praise word shown near the top of the board. The composable maps each to a string resource. */
enum class PraiseKind { NICE, PURRFECT, MEOWVELOUS, CLAWSOME, CLEAR }

/**
 * Cosmetic in-run streak of consecutive correct placements. Never persisted, never part of the score.
 * Pure Kotlin, unit tested.
 */
object StreakLogic {
    const val PITCH_MIN = 1.0f
    const val PITCH_MAX = 1.35f
    private const val PITCH_STEP = 0.05f

    /** Streak after a step: wrong move resets, a clean correct placement adds one, anything else (X, undo, hint) keeps it. */
    fun next(streak: Int, correct: Boolean, wrong: Boolean): Int = when {
        wrong -> 0
        correct -> streak + 1
        else -> streak
    }

    /** Praise word earned by reaching exactly [streak]; null between thresholds. */
    fun praiseAt(streak: Int): PraiseKind? = when (streak) {
        3 -> PraiseKind.NICE
        5 -> PraiseKind.PURRFECT
        8 -> PraiseKind.MEOWVELOUS
        12 -> PraiseKind.CLAWSOME
        else -> null
    }

    /** Sound rate multiplier for the layered chime / meow: 1.0 at streak 0..1, rising 5% per step, clamped. */
    fun pitch(streak: Int): Float = (PITCH_MIN + PITCH_STEP * (streak - 1).coerceAtLeast(0)).coerceIn(PITCH_MIN, PITCH_MAX)

    /** Combo chip next to the score shows from streak 2. */
    fun comboVisible(streak: Int): Boolean = streak >= 2
}

/** Region-complete detection. Pure Kotlin, unit tested. */
object RegionClear {
    /** Region ids that are done in [after] but were not in [before]. Lists may differ in length; missing = not done. */
    fun newlyDone(before: List<Boolean>, after: List<Boolean>): List<Int> {
        if (after.isEmpty()) return emptyList()
        val out = ArrayList<Int>(1)
        for (i in after.indices) {
            if (after[i] && !(i < before.size && before[i])) out.add(i)
        }
        return out
    }
}

/**
 * Combo-level look of the "+N" popup and the combo chip. Cosmetic only (never score). Pure Kotlin, unit tested.
 * Colours are plain ARGB longs so this stays free of Compose types.
 */
object ComboStyle {
    const val SCALE_MIN = 1.0f
    const val SCALE_MAX = 1.4f
    private const val SCALE_FROM_STREAK = 2
    private const val SCALE_FULL_AT = 12

    private const val GOLD = 0xFFFFF176L // streak < 3: soft gold
    private const val AMBER = 0xFFFFB300L // 3..4
    private const val ORANGE = 0xFFFF7043L // 5..7
    private const val PINK = 0xFFFF4081L // 8+

    /** Popup size multiplier: 1.0 up to streak 2, rising linearly to 1.4 at streak 12, clamped. */
    fun scale(streak: Int): Float {
        val q = ((streak - SCALE_FROM_STREAK).coerceAtLeast(0).toFloat() / (SCALE_FULL_AT - SCALE_FROM_STREAK)).coerceIn(0f, 1f)
        return SCALE_MIN + (SCALE_MAX - SCALE_MIN) * q
    }

    /** 0 = gold, 1 = amber, 2 = orange, 3 = pink. */
    fun tier(streak: Int): Int = when {
        streak >= 8 -> 3
        streak >= 5 -> 2
        streak >= 3 -> 1
        else -> 0
    }

    fun colorArgb(streak: Int): Long = when (tier(streak)) {
        3 -> PINK
        2 -> ORANGE
        1 -> AMBER
        else -> GOLD
    }
}

/** Which placed cats touch a wrong placement. Pure Kotlin, unit tested. */
object WrongMoveLogic {
    /**
     * Flat indices of cats in the 8-neighbourhood of [idx] on an [n] x [n] board ([isCat] by flat index). Empty = the wrong move is
     * not a "touching" violation. [idx] itself is never included.
     */
    fun touchingCats(n: Int, idx: Int, isCat: (Int) -> Boolean): Set<Int> {
        if (n <= 0 || idx !in 0 until n * n) return emptySet()
        val r = idx / n
        val c = idx % n
        var out: MutableSet<Int>? = null
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val rr = r + dr
            val cc = c + dc
            if (rr !in 0 until n || cc !in 0 until n) continue
            val j = rr * n + cc
            if (isCat(j)) (out ?: HashSet<Int>().also { out = it }).add(j)
        }
        return out ?: emptySet()
    }
}

/** Shooting-star flight maths (region clear -> tab icon). Pure Kotlin, unit tested. */
object StarFlight {
    const val DURATION_MS = 550

    /** Point on a quadratic Bezier at [t] (0..1) between ([x0],[y0]) and ([x1],[y1]) with control ([cx],[cy]); writes into [out] (x, y). */
    fun bezier(t: Float, x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float, out: FloatArray) {
        val q = t.coerceIn(0f, 1f)
        val u = 1f - q
        out[0] = u * u * x0 + 2f * u * q * cx + q * q * x1
        out[1] = u * u * y0 + 2f * u * q * cy + q * q * y1
    }

    /** Ease-in-out for the flight (slow start, fast middle, soft landing). */
    fun ease(t: Float): Float {
        val q = t.coerceIn(0f, 1f)
        return q * q * (3f - 2f * q)
    }
}
