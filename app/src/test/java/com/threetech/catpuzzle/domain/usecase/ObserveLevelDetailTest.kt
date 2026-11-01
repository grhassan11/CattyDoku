package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.model.LevelDifficulty
import com.threetech.catpuzzle.domain.model.LevelProgress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObserveLevelDetailTest {

    @Test
    fun unplayedLevelHasNoResults() = runTest {
        val d = ObserveLevelDetail(FakeProgressRepository())(13).first()
        assertFalse(d.completed)
        assertEquals(0, d.stars)
        assertEquals(6, d.boardSize)
        assertEquals(2, d.worldNumber)
        assertNull(d.bestScore)
        assertNull(d.bestTimeMs)
    }

    @Test
    fun completedLevelCarriesBestValuesAndStars() = runTest {
        val repo = FakeProgressRepository()
        repo.save(LevelProgress(5, 420, 83_000L, 1L, mistakes = 3, hintsUsed = 0))
        val d = ObserveLevelDetail(repo)(5).first()
        assertTrue(d.completed)
        assertEquals(1, d.stars)
        assertEquals(420, d.bestScore)
        assertEquals(83_000L, d.bestTimeMs)
        assertEquals(5, d.boardSize)
    }

    @Test
    fun unusableStoredValuesBecomeNull() {
        val d = ObserveLevelDetail(FakeProgressRepository()).build(2, LevelProgress(2, 0, 0L, 1L, 0, 0))
        assertTrue(d.completed)
        assertEquals(3, d.stars)
        assertNull(d.bestScore)
        assertNull(d.bestTimeMs)
    }

    @Test
    fun difficultyBucketsFollowTargetTechnique() {
        assertEquals(LevelDifficulty.Easy, LevelDifficulty.fromTechnique(1))
        assertEquals(LevelDifficulty.Easy, LevelDifficulty.fromTechnique(2))
        assertEquals(LevelDifficulty.Medium, LevelDifficulty.fromTechnique(3))
        assertEquals(LevelDifficulty.Hard, LevelDifficulty.fromTechnique(4))
        assertEquals(LevelDifficulty.Expert, LevelDifficulty.fromTechnique(5))
    }

    @Test
    fun difficultyOfLevelUsesTheCurve() {
        assertEquals(LevelDifficulty.Easy, LevelDifficulty.of(1))      // 4x4 target 1
        assertEquals(LevelDifficulty.Medium, LevelDifficulty.of(12 + 1)) // 6x6 first target 3
        assertEquals(LevelDifficulty.Expert, LevelDifficulty.of(19))   // 6x6 target 5
        assertEquals(LevelDifficulty.Easy, LevelDifficulty.of(25))     // cycle start target 2
        assertEquals(LevelDifficulty.Expert, LevelDifficulty.of(30))   // cycle end target 5
    }
}
