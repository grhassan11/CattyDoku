package com.threetech.catpuzzle.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DifficultyCurveTest {
    @Test
    fun sizeBoundaries() {
        assertEquals(4, DifficultyCurve.sizeFor(1))
        assertEquals(4, DifficultyCurve.sizeFor(4))
        assertEquals(5, DifficultyCurve.sizeFor(5))
        assertEquals(5, DifficultyCurve.sizeFor(12))
        assertEquals(6, DifficultyCurve.sizeFor(13))
        assertEquals(6, DifficultyCurve.sizeFor(24))
        assertEquals(7, DifficultyCurve.sizeFor(25))
    }

    @Test
    fun mixedCycleLevels24To40() {
        // level -> (size, target)
        val expected = mapOf(
            24 to (6 to 5),
            25 to (7 to 2), 26 to (7 to 3), 27 to (7 to 3), 28 to (7 to 4), 29 to (7 to 4), 30 to (7 to 4),
            31 to (7 to 2), 32 to (7 to 3), 33 to (8 to 3), 34 to (8 to 4), 35 to (8 to 4), 36 to (8 to 5),
            37 to (7 to 2), 38 to (7 to 3), 39 to (8 to 3), 40 to (8 to 4),
            41 to (9 to 4), 42 to (9 to 5),
        )
        for ((lvl, st) in expected) {
            val s = DifficultyCurve.specFor(lvl)
            assertEquals("size L$lvl", st.first, s.size)
            assertEquals("size L$lvl", st.first, DifficultyCurve.sizeFor(lvl))
            assertEquals("target L$lvl", st.second, s.targetTechnique)
        }
        assertEquals(40, DifficultyCurve.specFor(25).balancePercent)
        assertEquals(35, DifficultyCurve.specFor(33).balancePercent)
        assertEquals(30, DifficultyCurve.specFor(41).balancePercent)
    }

    @Test
    fun noBigGridsBeforeGate() {
        for (lvl in 1..30) assertTrue("L$lvl", DifficultyCurve.sizeFor(lvl) <= 7)
        for (lvl in 1..40) assertTrue("L$lvl", DifficultyCurve.sizeFor(lvl) <= 8)
        assertEquals(8, (31..40).maxOf { DifficultyCurve.sizeFor(it) })
        assertEquals(9, DifficultyCurve.sizeFor(41))
        for (lvl in 1..300) {
            val fb = DifficultyCurve.fallbackSpecsFor(lvl)
            val cap = DifficultyCurve.sizeFor(lvl)
            assertTrue("L$lvl", fb.all { it.size < cap || (it.size == 7 && cap > 7) })
            assertEquals("L$lvl dup", fb.size, fb.distinct().size)
        }
    }

    @Test
    fun neverAboveMaxBoardAndSizeMatchesSpec() {
        for (lvl in (1..3000) + listOf(99_999, Int.MAX_VALUE)) {
            val size = DifficultyCurve.sizeFor(lvl)
            assertTrue("L$lvl size $size", size in 4..MAX_BOARD_SIZE)
            assertEquals(size, DifficultyCurve.specFor(lvl).size)
        }
        // high level sample: (5000 - 25) % 6 = 1 -> 7x7 target 3; (5002 - 25) % 6 = 3 -> 8x8 target 4
        assertEquals(7, DifficultyCurve.sizeFor(5000))
        assertEquals(8, DifficultyCurve.sizeFor(5002))
        assertEquals(4, DifficultyCurve.specFor(5002).targetTechnique)
    }

    @Test
    fun fallbackSpecsOnlyForBigBoards() {
        for (lvl in 1..60) {
            val fb = DifficultyCurve.fallbackSpecsFor(lvl)
            val base = DifficultyCurve.specFor(lvl)
            if (base.size <= 7) {
                assertTrue("L$lvl", fb.isEmpty())
            } else {
                assertTrue("L$lvl", fb.isNotEmpty())
                for (f in fb) {
                    assertEquals(lvl, f.levelIndex)
                    assertTrue(f.size in 7 until base.size || (f.size == 7 && f.targetTechnique <= base.targetTechnique))
                    assertTrue(f.targetTechnique in 1..base.targetTechnique)
                }
                assertEquals(7, fb.last().size)
            }
        }
    }

    @Test
    fun levelClampedAtOne() {
        assertEquals(DifficultyCurve.specFor(1), DifficultyCurve.specFor(0))
        assertEquals(DifficultyCurve.specFor(1), DifficultyCurve.specFor(-9))
    }

    @Test
    fun specsSane() {
        for (lvl in 1..200) {
            val s = DifficultyCurve.specFor(lvl)
            assertEquals(lvl, s.levelIndex)
            assertEquals(DifficultyCurve.sizeFor(lvl), s.size)
            assertTrue(s.targetTechnique in 1..Technique.MAX)
            assertTrue(s.minRegionSize in 1..s.size)
            assertTrue(s.balancePercent in 0..100)
            assertTrue(s.minOpeningSingles >= 0)
        }
    }

    @Test
    fun sevenBySevenIsSawtooth() {
        val targets = (25..60).map { DifficultyCurve.specFor(it).targetTechnique }
        var drops = 0
        for (i in 1 until targets.size) if (targets[i] < targets[i - 1]) drops++
        assertTrue("expected repeated drops, got $drops", drops >= 4)
        assertTrue(targets.min() < targets.max())
        // cycle repeats
        assertEquals(DifficultyCurve.specFor(25).targetTechnique, DifficultyCurve.specFor(31).targetTechnique)
    }

    @Test
    fun noExpertInFirst30Levels() {
        for (lvl in 1..30) assertTrue("L$lvl", DifficultyCurve.specFor(lvl).targetTechnique <= 4)
        assertEquals(5, DifficultyCurve.specFor(36).targetTechnique)
    }

    @Test
    fun firstLevelsAreEasy() {
        assertEquals(1, DifficultyCurve.specFor(1).targetTechnique)
        assertEquals(1, DifficultyCurve.specFor(1).minRegionSize)
    }
}
