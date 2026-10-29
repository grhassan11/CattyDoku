package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.model.GameState
import com.threetech.catpuzzle.domain.model.Puzzle
import com.threetech.catpuzzle.domain.model.SavedGame
import com.threetech.catpuzzle.domain.repository.PuzzleRepository
import com.threetech.catpuzzle.domain.repository.SavedGameRepository
import com.threetech.catpuzzle.domain.time.TimeProvider

/**
 * Autosave one move. Caller debounces. Only a PLAYING state is stored; a finished state clears the
 * slot instead, so a solved/lost level is never "restored".
 */
class SaveGame(
    private val repo: SavedGameRepository,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(levelIndex: Int, state: GameState, elapsedMs: Long) {
        if (!state.isPlaying) {
            repo.clear(levelIndex)
            return
        }
        repo.save(SavedGame(levelIndex, state, elapsedMs.coerceAtLeast(0L), time.nowMillis()))
    }
}

/** Puzzle plus the restored play state. */
data class RestoredGame(val puzzle: Puzzle, val saved: SavedGame)

/**
 * Restore an autosave for [levelIndex] together with its cached puzzle. If the puzzle is missing or its
 * size disagrees with the save, the save is inconsistent: it is cleared and null returned.
 */
class RestoreGame(
    private val saved: SavedGameRepository,
    private val puzzles: PuzzleRepository,
) {
    suspend operator fun invoke(levelIndex: Int): RestoredGame? {
        val game = saved.get(levelIndex) ?: return null
        val puzzle = puzzles.get(levelIndex)
        if (puzzle == null || puzzle.size != game.state.board.size) {
            saved.clear(levelIndex)
            return null
        }
        return RestoredGame(puzzle, game)
    }
}
