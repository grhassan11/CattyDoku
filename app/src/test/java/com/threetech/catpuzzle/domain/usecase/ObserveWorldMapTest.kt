package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.model.LevelProgress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ObserveWorldMapTest {

    private fun won(level: Int, mistakes: Int = 0, hints: Int = 0) =
        LevelProgress(level, 100, 1_000L, 1L, mistakes, hints)

    @Test
    fun freshInstallIsAtLevelOneWithSixWorlds() = runTest {
        val map = ObserveWorldMap(FakeProgressRepository())().first()
        assertEquals(1, map.currentLevel)
        assertEquals(60, map.visibleLevels)
        assertEquals(emptyMap<Int, Int>(), map.starsByLevel)
    }

    @Test
    fun currentIsFirstUnwonLevelAndStarsAreDerived() = runTest {
        val repo = FakeProgressRepository()
        repo.save(won(1))
        repo.save(won(2, mistakes = 1))
        repo.save(won(3, mistakes = 2, hints = 2))
        val map = ObserveWorldMap(repo)().first()
        assertEquals(4, map.currentLevel)
        assertEquals(mapOf(1 to 3, 2 to 2, 3 to 1), map.starsByLevel)
    }

    @Test
    fun gapInProgressKeepsCurrentAtTheGap() {
        val map = ObserveWorldMap(FakeProgressRepository()).build(listOf(won(1), won(2), won(4)))
        assertEquals(3, map.currentLevel)
    }

    @Test
    fun dailyRowsDoNotMoveTheMap() = runTest {
        val repo = FakeProgressRepository()
        repo.save(won(-20_000))
        assertEquals(1, ObserveWorldMap(repo)().first().currentLevel)
    }

    @Test
    fun reachingLevel51OpensWorldsUpToNinety() {
        val uc = ObserveWorldMap(FakeProgressRepository())
        val upTo50 = uc.build((1..49).map { won(it) })
        assertEquals(50, upTo50.currentLevel)
        assertEquals(60, upTo50.visibleLevels)
        val at51 = uc.build((1..50).map { won(it) })
        assertEquals(51, at51.currentLevel)
        assertEquals(90, at51.visibleLevels)
        val at81 = uc.build((1..80).map { won(it) })
        assertEquals(120, at81.visibleLevels)
        assertFalse(at81.currentLevel > at81.visibleLevels)
    }
}
