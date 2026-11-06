package com.tm.meow.sudoku.puzzle.ui.screens.game

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Jiggle for freshly player-placed crosses: damped rotation wobble + scale pop-and-settle (~[DURATION_MS]).
 * One shared millisecond [clock] (own `nanoTime` base) is read ONLY in cell draw lambdas of jiggling cells, so the board
 * never recomposes per frame. Start times are plain longs in the same base ([nowMs]).
 */
@Stable
internal class CrossJiggle {
    var clock by mutableLongStateOf(nowMs())

    fun tick() {
        clock = nowMs()
    }

    /** Wobble in degrees for [elapsedMs] since start (0 once finished). */
    fun angle(elapsedMs: Long): Float {
        val t = progress(elapsedMs) ?: return 0f
        return (MAX_DEG * exp(-DECAY * t) * sin(2.0 * PI * ROT_CYCLES * t)).toFloat()
    }

    /** Scale for [elapsedMs] since start: pops up then settles to 1 (damped). */
    fun scale(elapsedMs: Long): Float {
        val t = progress(elapsedMs) ?: return 1f
        return (1.0 + POP * exp(-DECAY * t) * sin(2.0 * PI * SCALE_CYCLES * t)).toFloat()
    }

    private fun progress(elapsedMs: Long): Double? {
        if (elapsedMs >= DURATION_MS) return null
        return (elapsedMs.coerceAtLeast(0L).toDouble() / DURATION_MS)
    }

    companion object {
        const val DURATION_MS = 380L
        /** Max concurrent jiggling crosses; extra cells in one bulk change are skipped. */
        const val MAX_CONCURRENT = 16
        private const val MAX_DEG = 16.0
        private const val POP = 0.28
        private const val DECAY = 4.0
        private const val ROT_CYCLES = 2.0
        private const val SCALE_CYCLES = 1.5

        fun nowMs(): Long = System.nanoTime() / 1_000_000L
    }
}
