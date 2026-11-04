package com.tm.meow.sudoku.puzzle.domain.sudoku.tutorial

import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuGeometry
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuSize
import com.tm.meow.sudoku.puzzle.domain.sudoku.model.SudokuTier

/** What a tutorial step teaches. Rule kinds highlight one unit; trick kinds highlight a block plus the digits that block cells. */
enum class SudokuTutorialKind { ROW, COLUMN, BLOCK, LAST_REMAINING, CROSS_HATCH }

/** One scripted move: tap [cell], then tap [digit]. Cell index = r * n + c (n = the board size of the lesson). */
data class SudokuTutorialStep(val kind: SudokuTutorialKind, val cell: Int, val digit: Int)

/** [index] 1 = The Rules, 2 = Solving Tricks. [initial] = the n * n digits shown at the start (0 = empty). */
class SudokuTutorialLesson(val index: Int, val initial: List<Int>, val steps: List<SudokuTutorialStep>)

/**
 * Fixed boards and steps for the two tutorial lessons of every board size (4x4, 6x6, 8x8, 9x9). Pure data + derived
 * highlight helpers: no generator, no persistence, no Android. Every trick step's target is the only legal cell of its
 * block for its digit (tests enforce this). Lesson 1 = 3 rule steps (row, column, block); lesson 2 = 2 trick steps with
 * the same digit, so the first placement is what makes the second target unique.
 */
object SudokuTutorialScript {
    const val LESSON_COUNT = 2

    /**
     * One size: solved grid, lesson 1 moves, lesson 2 moves. Cells are written as (row, col). Lesson 2 board = either
     * [trickEmpties] cut out of the solution, or (when [trickGivens] is set) only those cells filled in.
     */
    private class SizeScript(
        val size: SudokuSize,
        solutionRows: List<String>,
        rules: List<Triple<SudokuTutorialKind, Pair<Int, Int>, Int>>,
        tricks: List<Triple<SudokuTutorialKind, Pair<Int, Int>, Int>>,
        trickEmpties: List<Pair<Int, Int>> = emptyList(),
        trickGivens: List<Pair<Int, Int>>? = null,
    ) {
        private val n = size.n
        val solution: List<Int> = solutionRows.flatMap { row -> row.map { it - '0' } }

        private fun cell(rc: Pair<Int, Int>) = rc.first * n + rc.second

        private fun steps(list: List<Triple<SudokuTutorialKind, Pair<Int, Int>, Int>>) = list.map { SudokuTutorialStep(it.first, cell(it.second), it.third) }

        val lesson1: SudokuTutorialLesson = steps(rules).let { s ->
            SudokuTutorialLesson(1, List(n * n) { i -> if (s.any { it.cell == i }) 0 else solution[i] }, s)
        }

        val lesson2: SudokuTutorialLesson = run {
            val marked = (trickGivens ?: trickEmpties).map { cell(it) }.toSet()
            val initial = List(n * n) { i ->
                val filled = if (trickGivens != null) i in marked else i !in marked
                if (filled) solution[i] else 0
            }
            SudokuTutorialLesson(2, initial, steps(tricks))
        }
    }

    private val ROW = SudokuTutorialKind.ROW
    private val COL = SudokuTutorialKind.COLUMN
    private val BLK = SudokuTutorialKind.BLOCK
    private val LAST = SudokuTutorialKind.LAST_REMAINING
    private val CROSS = SudokuTutorialKind.CROSS_HATCH

    /** 4x4, 2x2 blocks. Lesson 2 digit 3: top-left block (last remaining), then bottom-left block (cross-hatch, needs the 3 at (1,0)). */
    private val S4 = SizeScript(
        size = SudokuSize.S4,
        solutionRows = listOf("1234", "3412", "2143", "4321"),
        rules = listOf(Triple(ROW, 0 to 3, 4), Triple(COL, 2 to 1, 1), Triple(BLK, 3 to 2, 2)),
        tricks = listOf(Triple(LAST, 1 to 0, 3), Triple(CROSS, 3 to 1, 3)),
        trickEmpties = listOf(0 to 0, 0 to 1, 1 to 0, 2 to 0, 3 to 0, 3 to 1),
    )

    /** 6x6, blocks 2 rows x 3 cols. Lesson 2 digit 4: top-left block (last remaining), then top-right block (cross-hatch, needs the 4 at (1,0)). */
    private val S6 = SizeScript(
        size = SudokuSize.S6,
        solutionRows = listOf("123456", "456123", "231564", "564231", "312645", "645312"),
        rules = listOf(Triple(ROW, 0 to 4, 5), Triple(COL, 4 to 1, 1), Triple(BLK, 5 to 3, 3)),
        tricks = listOf(Triple(LAST, 1 to 0, 4), Triple(CROSS, 0 to 3, 4)),
        trickEmpties = listOf(1 to 0, 1 to 1, 1 to 2, 0 to 3, 1 to 3, 1 to 4, 1 to 5),
    )

    /** 8x8, blocks 2 rows x 4 cols. Lesson 2 digit 1: rows 4-5 / cols 4-7 block (last remaining), then rows 4-5 / cols 0-3 block (cross-hatch, needs the 1 at (4,6)). */
    private val S8 = SizeScript(
        size = SudokuSize.S8,
        solutionRows = listOf(
            "12345678",
            "56781234",
            "23456781",
            "67812345",
            "34567812",
            "78123456",
            "45678123",
            "81234567",
        ),
        rules = listOf(Triple(ROW, 0 to 5, 6), Triple(COL, 5 to 2, 1), Triple(BLK, 3 to 6, 4)),
        tricks = listOf(Triple(LAST, 4 to 6, 1), Triple(CROSS, 5 to 2, 1)),
        trickEmpties = listOf(4 to 0, 4 to 1, 4 to 2, 5 to 2, 4 to 5, 4 to 6, 4 to 7, 5 to 4, 5 to 5),
    )

    /** 9x9, 3x3 blocks. Lesson 2 digit 2 (the original tutorial, unchanged): only the listed givens are shown. */
    private val S9 = SizeScript(
        size = SudokuSize.S9,
        solutionRows = listOf(
            "534678912",
            "672195348",
            "198342567",
            "859761423",
            "426853791",
            "713924856",
            "961537284",
            "287419635",
            "345286179",
        ),
        rules = listOf(Triple(ROW, 0 to 4, 7), Triple(COL, 5 to 8, 6), Triple(BLK, 7 to 1, 8)),
        tricks = listOf(Triple(LAST, 5 to 4, 2), Triple(CROSS, 8 to 3, 2)),
        trickGivens = listOf(
            4 to 1, 2 to 5,
            3 to 3, 3 to 4, 3 to 5,
            5 to 3, 5 to 5,
            6 to 3, 6 to 4, 6 to 5,
            7 to 3, 7 to 4, 7 to 5,
        ),
    )

    private val scripts: List<SizeScript> = listOf(S4, S6, S8, S9)

    private fun script(size: SudokuSize): SizeScript = scripts.first { it.size == size }

    /** The solved grid every lesson board of [size] is cut from. */
    fun solution(size: SudokuSize): List<Int> = script(size).solution

    fun lessons(size: SudokuSize): List<SudokuTutorialLesson> = script(size).let { listOf(it.lesson1, it.lesson2) }

    fun lesson(size: SudokuSize, index: Int): SudokuTutorialLesson =
        lessons(size)[(index - 1).coerceIn(0, LESSON_COUNT - 1)]

    /** Lowest tier that plays on [size]; its gameplay theme + ink is the tutorial's look (4x4 Easy, 6x6 Medium, 8x8 Hard, 9x9 Master). */
    fun themeTier(size: SudokuSize): SudokuTier = SudokuTier.entries.first { it.size == size }

    /** The unit the step is about: its row, its column, or its block. Trick steps use the block. */
    fun unitCells(size: SudokuSize, step: SudokuTutorialStep): List<Int> {
        val g = SudokuGeometry.of(size)
        val u = g.unitsOf[step.cell]
        val unit = when (step.kind) {
            SudokuTutorialKind.ROW -> u[0]
            SudokuTutorialKind.COLUMN -> u[1]
            else -> u[2]
        }
        return g.units[unit].toList()
    }

    /** Trick steps only: empty cells of the block (other than the target) that already see [SudokuTutorialStep.digit]. */
    fun marks(size: SudokuSize, values: List<Int>, step: SudokuTutorialStep): Set<Int> {
        if (!isTrick(step.kind)) return emptySet()
        return unitCells(size, step)
            .filter { it != step.cell && values[it] == 0 && sees(size, values, it, step.digit) }
            .toSet()
    }

    /** Trick steps only: placed copies of the step digit that block a marked cell. */
    fun blockers(size: SudokuSize, values: List<Int>, step: SudokuTutorialStep): Set<Int> {
        val g = SudokuGeometry.of(size)
        val out = LinkedHashSet<Int>()
        for (m in marks(size, values, step)) {
            for (p in g.peers[m]) if (values[p] == step.digit) out.add(p)
        }
        return out
    }

    /** Cells drawn bright: the unit plus its blockers. Everything else is dimmed. */
    fun spotlight(size: SudokuSize, values: List<Int>, step: SudokuTutorialStep): Set<Int> =
        unitCells(size, step).toSet() + blockers(size, values, step)

    /** Empty block cells where the step digit can still go (no peer holds it). A good trick step has exactly the target. */
    fun candidates(size: SudokuSize, values: List<Int>, step: SudokuTutorialStep): List<Int> =
        unitCells(size, step).filter { values[it] == 0 && !sees(size, values, it, step.digit) }

    fun isTrick(kind: SudokuTutorialKind): Boolean =
        kind == SudokuTutorialKind.LAST_REMAINING || kind == SudokuTutorialKind.CROSS_HATCH

    private fun sees(size: SudokuSize, values: List<Int>, cell: Int, digit: Int): Boolean =
        SudokuGeometry.of(size).peers[cell].any { values[it] == digit }
}
