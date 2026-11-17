package com.tm.meow.sudoku.puzzle.domain.usecase

import com.tm.meow.sudoku.puzzle.domain.engine.StarterCatPolicy
import com.tm.meow.sudoku.puzzle.domain.model.GameState
import com.tm.meow.sudoku.puzzle.domain.model.SavedGame
import com.tm.meow.sudoku.puzzle.domain.repository.SavedGameRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Live "this level has a real in-progress session" flag for the Home level sheet (Play -> Resume).
 * The game restores ANY stored save, but a save also exists right after a restart (starter cat only),
 * so "in progress" here = a PLAYING save where the player actually did something. Level-specific
 * (own row only); a finished game is cleared by [SaveGame] / CompleteLevel, so it never matches.
 */
class ObserveLevelResumable(private val savedGames: SavedGameRepository) {
    operator fun invoke(level: Int): Flow<Boolean> =
        savedGames.observe(level).map { it != null && hasProgress(it) }.distinctUntilChanged()

    internal companion object {
        fun hasProgress(saved: SavedGame): Boolean {
            val s: GameState = saved.state
            if (!s.isPlaying) return false
            // Starter cat (levels 1..20) is one free locked cell that is not player progress.
            val freeLocked = if (StarterCatPolicy.appliesTo(saved.levelIndex)) 1 else 0
            return s.undoStack.isNotEmpty() ||
                s.correctPlacements > 0 ||
                s.hintsUsed > 0 ||
                s.heartsLost > 0 ||
                s.lockedCells.size > freeLocked
        }
    }
}
