package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.engine.ScoreCalculator
import com.threetech.catpuzzle.domain.engine.ScoreInput
import com.threetech.catpuzzle.domain.model.GameResult
import com.threetech.catpuzzle.domain.model.GameState
import com.threetech.catpuzzle.domain.model.GameStatus
import com.threetech.catpuzzle.domain.model.Puzzle

/** Final [GameResult] for a finished (or abandoned) level. [elapsedSeconds] is supplied by the caller. */
class ComputeScore(private val calculator: ScoreCalculator) {
    operator fun invoke(puzzle: Puzzle, state: GameState, elapsedSeconds: Int): GameResult {
        val won = state.status == GameStatus.SOLVED
        val score = calculator.compute(
            ScoreInput(
                size = puzzle.size,
                correctPlacements = state.correctPlacements,
                heartsLost = state.heartsLost,
                hintsUsed = state.hintsUsed,
                elapsedSeconds = elapsedSeconds,
                won = won,
            ),
        )
        return GameResult(
            levelIndex = puzzle.levelIndex,
            won = won,
            score = score,
            heartsLeft = state.hearts,
            hintsUsed = state.hintsUsed,
            elapsedSeconds = elapsedSeconds,
        )
    }
}
