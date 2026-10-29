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
        assertEquals(7, DifficultyCurve.sizeFor(5000))
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
    fun firstLevelsAreEasy() {
        assertEquals(1, DifficultyCurve.specFor(1).targetTechnique)
        assertEquals(1, DifficultyCurve.specFor(1).minRegionSize)
    }
}
