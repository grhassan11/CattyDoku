package com.threetech.catpuzzle.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BossNodeShapesTest {

    @Test
    fun stableAndInRange() {
        for (level in 10..2000 step 10) {
            val a = BossNodeShapes.indexFor(level)
            assertEquals(a, BossNodeShapes.indexFor(level))
            assertTrue(a in 0 until BossNodeShapes.COUNT)
        }
    }

    @Test
    fun allShapesAppearAcrossFirstBosses() {
        val seen = (10..200 step 10).map { BossNodeShapes.indexFor(it) }.toSet()
        assertEquals((0 until BossNodeShapes.COUNT).toSet(), seen)
        val firstBlock = (10..50 step 10).map { BossNodeShapes.indexFor(it) }.toSet()
        assertEquals(BossNodeShapes.COUNT, firstBlock.size)
    }

    @Test
    fun poolIsFiveWithFishTinInside() {
        assertEquals(5, BossNodeShapes.COUNT)
        assertTrue(BossNodeShapes.FISH_TIN in 0 until BossNodeShapes.COUNT)
    }

    @Test
    fun adjacentBossesDiffer() {
        for (level in 10..2000 step 10) {
            assertNotEquals(BossNodeShapes.indexFor(level), BossNodeShapes.indexFor(level + 10))
        }
    }
}
