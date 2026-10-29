package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.engine.GameEngine
import com.threetech.catpuzzle.domain.engine.StarterCatPicker
import com.threetech.catpuzzle.domain.engine.StarterCatPolicy
import com.threetech.catpuzzle.domain.model.GameState
import com.threetech.catpuzzle.domain.model.Puzzle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fresh-start step: place the deterministic free starter cat on [state] (locked, no score, no undo).
 * Call ONLY for a fresh start or restart of a game level, never for a restored save (it already holds
 * the cat) and never for the scripted tutorial board. Picker runs on [dispatcher].
 * Returns [state] unchanged (same instance) if no starter cell exists or the level is outside
 * [StarterCatPolicy] (level > 10, daily). An existing starter in an old save is never stripped.
 */
class ApplyStarterCat(
    private val picker: StarterCatPicker,
    private val engine: GameEngine,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend operator fun invoke(puzzle: Puzzle, state: GameState): GameState {
        if (!StarterCatPolicy.appliesTo(puzzle.levelIndex)) return state
        val pos = withContext(dispatcher) { picker.pick(puzzle) } ?: return state
        return engine.placeStarter(puzzle, state, pos).state
    }
}
