package com.threetech.catpuzzle.domain.engine

import com.threetech.catpuzzle.domain.model.Analysis
import com.threetech.catpuzzle.domain.model.BoardState
import com.threetech.catpuzzle.domain.model.CellMark
import com.threetech.catpuzzle.domain.model.Puzzle
import kotlin.math.abs

/** Pure board checker. X marks are ignored; only CAT cells count. */
object BoardValidator {

    fun analyse(puzzle: Puzzle, board: BoardState): Analysis =
        analyse(puzzle.size, puzzle.regions, board.marks)

    fun analyse(size: Int, regions: IntArray, marks: List<CellMark>): Analysis {
        require(marks.size == size * size) { "marks size" }
        require(regions.size == size * size) { "regions size" }

        val cats = ArrayList<Int>()
        for (i in marks.indices) if (marks[i] == CellMark.CAT) cats.add(i)

        val conflicts = LinkedHashSet<Int>()
        for (i in 0 until cats.size) {
            val a = cats[i]
            val ar = a / size
            val ac = a % size
            for (j in i + 1 until cats.size) {
                val b = cats[j]
                val br = b / size
                val bc = b % size
                val clash = ar == br ||
                    ac == bc ||
                    regions[a] == regions[b] ||
                    (abs(ar - br) <= 1 && abs(ac - bc) <= 1)
                if (clash) {
                    conflicts.add(a)
                    conflicts.add(b)
                }
            }
        }

        val rowCount = IntArray(size)
        val colCount = IntArray(size)
        val regCount = IntArray(size)
        for (c in cats) {
            rowCount[c / size]++
            colCount[c % size]++
            regCount[regions[c]]++
        }
        val rowDone = BooleanArray(size)
        val colDone = BooleanArray(size)
        val regDone = BooleanArray(size)
        for (c in cats) {
            if (c in conflicts) continue
            if (rowCount[c / size] == 1) rowDone[c / size] = true
            if (colCount[c % size] == 1) colDone[c % size] = true
            if (regCount[regions[c]] == 1) regDone[regions[c]] = true
        }

        // size cats with no clashing pair => exactly one per row, col and region.
        val solved = cats.size == size && conflicts.isEmpty()

        return Analysis(
            size = size,
            conflictCells = conflicts,
            regionDone = regDone.toList(),
            rowDone = rowDone.toList(),
            colDone = colDone.toList(),
            solved = solved,
        )
    }
}
