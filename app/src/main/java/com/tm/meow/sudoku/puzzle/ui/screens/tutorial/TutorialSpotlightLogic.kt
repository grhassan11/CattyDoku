package com.tm.meow.sudoku.puzzle.ui.screens.tutorial

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/** Pure placement + geometry for the tutorial overlay. All values in px (any consistent unit). */
object SpotlightLogic {

    /**
     * Top of the instruction card: [gap] above the board, but never above [areaTop] (a tall card then
     * overlaps the board top rather than leaving the screen). A null [boardTop] (not measured yet) parks it at [areaTop].
     */
    fun cardTop(areaTop: Float, boardTop: Float?, cardHeight: Float, gap: Float): Float {
        if (boardTop == null) return areaTop
        return (boardTop - gap - cardHeight).coerceAtLeast(areaTop)
    }

    /**
     * Top of the block under the board: [gap] below the board, but pulled up so it ends at or above
     * [areaBottom] (never above [areaTop]). A null [boardBottom] parks it at the bottom of the area.
     */
    fun bottomTop(areaTop: Float, areaBottom: Float, boardBottom: Float?, blockHeight: Float, gap: Float): Float {
        val maxTop = (areaBottom - blockHeight).coerceAtLeast(areaTop)
        if (boardBottom == null) return maxTop
        return (boardBottom + gap).coerceIn(areaTop, maxTop)
    }

    /**
     * Rect of one cell on a board whose top-left is ([boardLeft], [boardTop]): [pad] card padding,
     * [cell] cell side, [gap] between cells.
     */
    fun cellRect(
        boardLeft: Float,
        boardTop: Float,
        pad: Float,
        cell: Float,
        gap: Float,
        row: Int,
        col: Int,
    ): Rect {
        val pitch = cell + gap
        val left = boardLeft + pad + col * pitch
        val top = boardTop + pad + row * pitch
        return Rect(left, top, left + cell, top + cell)
    }

    /** Flat indices of cells that must sit under the touch gate: every cell not in [interactive]. */
    fun gatedCells(boardSize: Int, interactive: Set<Int>): List<Int> =
        (0 until boardSize * boardSize).filter { it !in interactive }

    /**
     * Cells that stay bright (cut out of the dim): the ringed [spotlight] cells plus every cell that
     * already holds a mark ([marked], flat indices). Empty set when [light] (no dim at all).
     */
    fun brightCells(light: Boolean, spotlight: Set<Int>, marked: Set<Int>): Set<Int> =
        if (light) emptySet() else spotlight + marked

    /** Point at progress [k] (0..1) along the polyline [points] (equal time per segment). Single point: that point. */
    fun along(points: List<Offset>, k: Float): Offset {
        if (points.size == 1) return points.first()
        val x = k.coerceIn(0f, 1f) * (points.size - 1)
        val i = x.toInt().coerceAtMost(points.size - 2)
        val f = x - i
        val a = points[i]
        val b = points[i + 1]
        return Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
    }
}
