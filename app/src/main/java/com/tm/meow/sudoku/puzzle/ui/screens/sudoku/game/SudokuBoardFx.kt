package com.tm.meow.sudoku.puzzle.ui.screens.sudoku.game

import androidx.compose.runtime.Immutable

/**
 * One board effect burst. [id] grows per event (0 = none) and doubles as the animation token of the cells involved.
 * [popCell] = correct digit just placed, [shakeCell] = wrong digit just placed (-1 = none),
 * [flashCells] = cells of every row, column and box this move completed (all digits placed, none wrong).
 */
@Immutable
data class SudokuFxEvent(
    val id: Int,
    val popCell: Int,
    val shakeCell: Int,
    val flashCells: Set<Int>,
) {
    companion object {
        val NONE = SudokuFxEvent(0, -1, -1, emptySet())
    }
}

/** Pure detection of board effects from two consecutive cell lists. No Compose runtime use, JVM-testable. */
object SudokuBoardFx {

    /**
     * Event for the move between [prev] and [next], or null. Only a move that changes EXACTLY ONE cell to a non-given digit
     * counts (a new puzzle or a restart changes many cells and yields nothing).
     */
    fun detect(
        prev: List<SudokuCellUi>,
        next: List<SudokuCellUi>,
        n: Int,
        boxRows: Int,
        boxCols: Int,
        id: Int,
    ): SudokuFxEvent? {
        if (prev.size != next.size || next.size != n * n) return null
        var changed = -1
        for (i in next.indices) {
            if (prev[i].value != next[i].value) {
                if (changed != -1) return null
                changed = i
            }
        }
        if (changed == -1) return null
        val cell = next[changed]
        if (cell.value == 0 || cell.given) return null
        if (cell.wrong) return SudokuFxEvent(id, -1, changed, emptySet())
        val flash = LinkedHashSet<Int>()
        for (unit in unitsOf(changed, n, boxRows, boxCols)) {
            if (isComplete(next, unit) && !isComplete(prev, unit)) unit.forEach { flash.add(it) }
        }
        return SudokuFxEvent(id, changed, -1, flash)
    }

    /** Row, column and box (boxRows x boxCols cells) of [cell], as cell-index arrays. */
    fun unitsOf(cell: Int, n: Int, boxRows: Int, boxCols: Int): List<IntArray> {
        val r = cell / n
        val c = cell % n
        val row = IntArray(n) { r * n + it }
        val col = IntArray(n) { it * n + c }
        val top = r / boxRows * boxRows
        val left = c / boxCols * boxCols
        val box = IntArray(boxRows * boxCols) { (top + it / boxCols) * n + left + it % boxCols }
        return listOf(row, col, box)
    }

    private fun isComplete(cells: List<SudokuCellUi>, unit: IntArray): Boolean =
        unit.all { cells[it].value != 0 && !cells[it].wrong }
}
