package com.threetech.catpuzzle.ui.screens.game

import com.threetech.catpuzzle.domain.model.Pos
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** What the board gesture layer asks the ViewModel to do. */
sealed interface BoardAction {
    /** Single tap: toggle X. Fires immediately (no waiting for a possible second tap). */
    data class Tap(val pos: Pos) : BoardAction

    /**
     * Second tap on the same cell inside the window: place a cat (replaces the X the first tap made)
     * or, on a cat, remove it.
     */
    data class DoubleTap(val pos: Pos) : BoardAction

    /** Swipe path (first cell decides MARK vs ERASE, engine side). Always 2+ distinct cells. */
    data class Drag(val path: List<Pos>) : BoardAction
}

/** Drag mode as the UI predicts it for the live preview (the engine decides for real). */
enum class DragIntent { MARK, ERASE }

/**
 * Pure tap / double-tap / swipe resolver. No Compose, no Android: the pointer layer feeds it cell
 * positions and timestamps, it returns [BoardAction]s.
 *
 * Tap vs double-tap ambiguity (MEMORY.md): a tap acts at once ([BoardAction.Tap] on release). If the
 * next press lands on the SAME cell within [doubleTapWindowMs] of the previous tap's release, that
 * gesture becomes [BoardAction.DoubleTap]. So a double-tap = X toggle, then cat placement over it.
 * A completed double-tap resets the chain, so a third quick tap is a fresh tap.
 *
 * A gesture becomes a swipe the moment the pointer enters a cell other than the one it started on.
 */
class BoardGestureResolver(private val doubleTapWindowMs: Long = DEFAULT_DOUBLE_TAP_WINDOW_MS) {

    private var start: Pos? = null
    private var downAtMs: Long = 0L
    private var current: Pos? = null
    private var dragging = false
    private val path = ArrayList<Pos>()

    private var lastTapPos: Pos? = null
    private var lastTapUpMs: Long = 0L

    /** Finger down on [pos]. Ignored gestures: call [cancel] or simply never call [end]. */
    fun begin(pos: Pos, downMs: Long) {
        start = pos
        current = pos
        downAtMs = downMs
        dragging = false
        path.clear()
    }

    /**
     * Finger now over [pos]. Returns the swipe path so far once the gesture is a swipe, else null.
     * Skipped cells (fast finger) are filled in so a stroke never leaves holes.
     */
    fun move(pos: Pos): List<Pos>? {
        val cur = current ?: return null
        val s = start ?: return null
        if (pos != cur) {
            if (!dragging) {
                dragging = true
                path.clear()
                path.add(s)
            }
            for (cell in cellsBetween(cur, pos)) if (path.lastOrNull() != cell) path.add(cell)
            current = pos
        }
        return if (dragging) path.toList() else null
    }

    val isDragging: Boolean get() = dragging

    /** Finger up. Returns the resolved action, or null when nothing started. */
    fun end(upMs: Long): BoardAction? {
        val s = start ?: return null
        val action: BoardAction = if (dragging) {
            lastTapPos = null
            BoardAction.Drag(path.toList())
        } else {
            val prev = lastTapPos
            if (prev == s && downAtMs - lastTapUpMs <= doubleTapWindowMs) {
                lastTapPos = null
                BoardAction.DoubleTap(s)
            } else {
                lastTapPos = s
                lastTapUpMs = upMs
                BoardAction.Tap(s)
            }
        }
        reset()
        return action
    }

    /** Gesture aborted (second pointer, lifecycle). Breaks any tap chain. */
    fun cancel() {
        lastTapPos = null
        reset()
    }

    private fun reset() {
        start = null
        current = null
        dragging = false
        path.clear()
    }

    companion object {
        const val DEFAULT_DOUBLE_TAP_WINDOW_MS = 300L

        /** Cells on the straight line from [a] (exclusive) to [b] (inclusive). */
        fun cellsBetween(a: Pos, b: Pos): List<Pos> {
            val dr = b.row - a.row
            val dc = b.col - a.col
            val steps = max(abs(dr), abs(dc))
            if (steps == 0) return emptyList()
            return List(steps) { i ->
                val t = (i + 1).toFloat() / steps
                Pos((a.row + dr * t).roundToInt(), (a.col + dc * t).roundToInt())
            }
        }

        /** Predicted drag mode from the first cell's mark; null = a drag from a cat does nothing. */
        fun dragIntentFor(firstCellMark: CellMarkUi): DragIntent? = when (firstCellMark) {
            CellMarkUi.EMPTY -> DragIntent.MARK
            CellMarkUi.X -> DragIntent.ERASE
            CellMarkUi.CAT -> null
        }

        /** Flat indices of the cells a drag of [intent] over [path] would change (for the live preview). */
        fun previewIndices(board: BoardUi, path: List<Pos>, intent: DragIntent?): Set<Int> {
            if (intent == null) return emptySet()
            val from = if (intent == DragIntent.MARK) CellMarkUi.EMPTY else CellMarkUi.X
            val out = LinkedHashSet<Int>()
            for (p in path) {
                if (p.row !in 0 until board.size || p.col !in 0 until board.size) continue
                val cell = board.cell(p.row, p.col)
                if (cell.mark == from && !cell.locked) out.add(p.row * board.size + p.col)
            }
            return out
        }
    }
}

/** Layout numbers for the board, in dp as plain floats (pure, testable). */
data class BoardMetrics(val cardPaddingDp: Float, val gapDp: Float, val cellDp: Float) {
    /** Side of the cell grid (cells + gaps), dp. */
    fun gridSideDp(size: Int): Float = cellDp * size + gapDp * (size - 1)
}

object BoardLayoutCalc {
    /** Visual cell floor (touch pitch incl. gap is a bit larger: the gap belongs to the hit area). */
    const val MIN_CELL_DP = 44f
    const val MAX_CELL_DP = 96f
    private val PADDING_STEPS = floatArrayOf(16f, 12f, 8f, 4f)

    /**
     * Size the board from [availableWidthDp]. Prefers 16dp card padding and shrinks it (down to 4dp)
     * before letting cells fall under [MIN_CELL_DP]; on very narrow widths cells may still go lower.
     */
    fun metrics(availableWidthDp: Float, size: Int): BoardMetrics {
        require(size >= 1) { "size" }
        val gap = if (size >= 6) 3f else 4f
        var last = BoardMetrics(PADDING_STEPS.last(), gap, 0f)
        for (pad in PADDING_STEPS) {
            val cell = (availableWidthDp - 2 * pad - gap * (size - 1)) / size
            last = BoardMetrics(pad, gap, cell.coerceIn(0f, MAX_CELL_DP))
            if (cell >= MIN_CELL_DP) return last
        }
        return last
    }

    /**
     * Cell under a touch point inside a square grid of side [sidePx]. The grid is divided evenly, so
     * the gap between two cells belongs to one of them (no dead zones = built-in hit-slop).
     * Null outside the grid.
     */
    fun cellAt(x: Float, y: Float, sidePx: Float, size: Int): Pos? {
        if (sidePx <= 0f || x < 0f || y < 0f || x >= sidePx || y >= sidePx) return null
        val pitch = sidePx / size
        val col = (x / pitch).toInt().coerceIn(0, size - 1)
        val row = (y / pitch).toInt().coerceIn(0, size - 1)
        return Pos(row, col)
    }
}
