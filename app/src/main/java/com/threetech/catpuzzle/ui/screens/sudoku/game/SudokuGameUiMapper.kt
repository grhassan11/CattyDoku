package com.threetech.catpuzzle.ui.screens.sudoku.game

import com.threetech.catpuzzle.domain.sudoku.model.BoxShape
import com.threetech.catpuzzle.domain.sudoku.model.SudokuGameState
import com.threetech.catpuzzle.domain.sudoku.model.SudokuGeometry
import com.threetech.catpuzzle.domain.sudoku.model.SudokuStatus
import com.threetech.catpuzzle.domain.sudoku.usecase.SudokuCompletion
import com.threetech.catpuzzle.ui.screens.sudoku.entry.SudokuEntryUiMapper

/** Pure domain -> UI mapping for the Sudoku game screen. */
object SudokuGameUiMapper {

    fun map(game: SudokuGameState, hintCharges: Int, userPaused: Boolean, dialog: SudokuDialogUi): SudokuGameUiState {
        val box = game.size.box
        return SudokuGameUiState(
            loading = false,
            tier = game.tier,
            n = game.size.n,
            boxRows = box.rows,
            boxCols = box.cols,
            cells = cells(game),
            selected = game.selected,
            score = game.score,
            hearts = game.hearts,
            maxHearts = SudokuGameState.MAX_HEARTS,
            paused = userPaused && game.status == SudokuStatus.PLAYING,
            notesMode = game.notesMode,
            canUndo = game.canUndo,
            canErase = canErase(game),
            inputEnabled = game.isActive,
            hintCharges = hintCharges.coerceAtLeast(0),
            padKeys = padKeys(game),
            dialog = dialog,
        )
    }

    fun cells(game: SudokuGameState): List<SudokuCellUi> {
        val geometry = SudokuGeometry.of(game.size)
        val sel = game.selected
        val selValue = if (sel in 0 until game.size.cells) game.values[sel] else 0
        val peers = if (sel in 0 until game.size.cells) BooleanArray(game.size.cells).also { p ->
            for (i in geometry.peers[sel]) p[i] = true
        } else null
        return List(game.size.cells) { i ->
            val v = game.values[i]
            val given = game.isGiven(i)
            SudokuCellUi(
                value = v,
                notes = game.notes[i],
                given = given,
                wrong = v != 0 && !given && v != game.puzzle.solution[i],
                highlight = when {
                    i == sel -> CellHighlight.SELECTED
                    selValue != 0 && v == selValue -> CellHighlight.SAME_DIGIT
                    peers != null && peers[i] -> CellHighlight.PEER
                    else -> CellHighlight.NONE
                },
            )
        }
    }

    /** Same rule as SudokuGameEngine.erase: a wrong digit, or notes in an empty cell. */
    fun canErase(game: SudokuGameState): Boolean {
        if (!game.isActive) return false
        val cell = game.selected
        if (cell !in 0 until game.size.cells || game.isGiven(cell)) return false
        val v = game.values[cell]
        return if (v != 0) v != game.puzzle.solution[cell] else game.notes[cell] != 0
    }

    /** Per digit: n minus the correct placements of it (givens included). Wrong digits do not count. */
    fun padKeys(game: SudokuGameState): List<SudokuPadKeyUi> {
        val n = game.size.n
        val placed = IntArray(n + 1)
        for (i in game.values.indices) {
            val v = game.values[i]
            if (v != 0 && v == game.puzzle.solution[i]) placed[v]++
        }
        return List(n) { SudokuPadKeyUi(it + 1, (n - placed[it + 1]).coerceAtLeast(0)) }
    }

    /** Row / column of [digit] inside a cell's notes grid (grid = the box shape: 2x2, 2x3, 3x3). */
    fun noteSlot(digit: Int, box: BoxShape): Pair<Int, Int> = (digit - 1) / box.cols to (digit - 1) % box.cols

    fun noteDigits(mask: Int, n: Int): List<Int> = (1..n).filter { mask and (1 shl (it - 1)) != 0 }

    fun win(game: SudokuGameState, completion: SudokuCompletion): SudokuWinUi = SudokuWinUi(
        timeText = SudokuEntryUiMapper.formatTime(game.elapsedMs),
        score = game.score,
        mistakes = game.mistakes,
        hintsUsed = game.hintsUsed,
        newBestTime = completion.isNewBestTime,
        newBestScore = completion.isNewBestScore,
        unlocked = completion.justUnlocked,
    )
}
