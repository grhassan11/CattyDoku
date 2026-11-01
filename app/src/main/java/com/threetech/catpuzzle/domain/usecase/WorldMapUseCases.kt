package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.model.LevelProgress
import com.threetech.catpuzzle.domain.model.LevelStars
import com.threetech.catpuzzle.domain.model.WorldMapProgress
import com.threetech.catpuzzle.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Live Home-map progress from stored level results (daily rows are already excluded by the repository). */
class ObserveWorldMap(private val progress: ProgressRepository) {
    operator fun invoke(): Flow<WorldMapProgress> = progress.observeAll().map(::build)

    internal fun build(all: List<LevelProgress>): WorldMapProgress {
        val stars = HashMap<Int, Int>(all.size)
        for (p in all) if (p.levelIndex >= 1) stars[p.levelIndex] = LevelStars.of(p)
        var current = 1
        while (stars.containsKey(current)) current++
        return WorldMapProgress(current, stars)
    }
}
