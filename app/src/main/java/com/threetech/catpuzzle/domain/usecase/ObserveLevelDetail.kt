package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.engine.DifficultyCurve
import com.threetech.catpuzzle.domain.model.LevelDetail
import com.threetech.catpuzzle.domain.model.LevelDifficulty
import com.threetech.catpuzzle.domain.model.LevelProgress
import com.threetech.catpuzzle.domain.model.LevelStars
import com.threetech.catpuzzle.domain.model.WorldRules
import com.threetech.catpuzzle.domain.repository.ProgressRepository
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
