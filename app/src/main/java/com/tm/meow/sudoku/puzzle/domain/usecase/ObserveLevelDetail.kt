package com.tm.meow.sudoku.puzzle.domain.usecase

import com.tm.meow.sudoku.puzzle.domain.engine.DifficultyCurve
import com.tm.meow.sudoku.puzzle.domain.model.LevelDetail
import com.tm.meow.sudoku.puzzle.domain.model.LevelDifficulty
import com.tm.meow.sudoku.puzzle.domain.model.LevelProgress
import com.tm.meow.sudoku.puzzle.domain.model.LevelStars
import com.tm.meow.sudoku.puzzle.domain.model.WorldRules
import com.tm.meow.sudoku.puzzle.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Live detail (size, difficulty, stars, best score / time) of one level for the Home level sheet. */
class ObserveLevelDetail(private val progress: ProgressRepository) {
    operator fun invoke(level: Int): Flow<LevelDetail> = progress.observeAll().map { all ->
        build(level, all.firstOrNull { it.levelIndex == level })
    }

    internal fun build(level: Int, stored: LevelProgress?): LevelDetail {
        val lvl = level.coerceAtLeast(1)
        return LevelDetail(
            level = lvl,
            worldNumber = WorldRules.worldNumber(lvl),
            boardSize = DifficultyCurve.sizeFor(lvl),
            difficulty = LevelDifficulty.of(lvl),
            completed = stored != null,
            stars = stored?.let { LevelStars.of(it) } ?: 0,
            bestScore = stored?.bestScore?.takeIf { it > 0 },
            bestTimeMs = stored?.bestTimeMs?.takeIf { it > 0L },
        )
    }
}
