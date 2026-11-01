package com.threetech.catpuzzle.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BossAnimalSetTest {
    private val bosses = (10..300 step 10).toList()

    @Test
    fun setIsStablePerLevel() {
        for (level in bosses) assertEquals(BossAnimalSet.setFor(level), BossAnimalSet.setFor(level))
    }

    @Test
    fun fishTinAlwaysOcean() {
        val fishTin = bosses.filter { BossNodeShapes.indexFor(it) == BossNodeShapes.FISH_TIN }
        assertTrue(fishTin.isNotEmpty())
        for (level in fishTin) assertEquals(AnimalSet.OCEAN, BossAnimalSet.setFor(level))
    }

    @Test
    fun allThreeSetsAppearAcrossBosses() {
        assertEquals(AnimalSet.entries.toSet(), bosses.map { BossAnimalSet.setFor(it) }.toSet())
    }

    @Test
    fun regionIndexStableInRangeAndDistinct() {
        for (set in AnimalSet.entries) for (level in bosses) {
            val all = (0 until 9).map { BossAnimalSet.regionIndex(level, set, it) }
            assertEquals(all, (0 until 9).map { BossAnimalSet.regionIndex(level, set, it) })
            assertTrue(all.all { it in 0 until set.count })
            assertEquals(9, all.toSet().size)
        }
    }

    @Test
    fun boosterIndexStableAndInRange() {
        for (set in AnimalSet.entries) for (level in bosses) {
            val a = BossAnimalSet.boosterIndex(level, set)
            assertEquals(a, BossAnimalSet.boosterIndex(level, set))
            assertTrue(a in 0 until set.count)
        }
    }

    @Test
    fun pieceSkinOnlyAnimalOnBossLevels() {
        assertFalse(PieceSkin.forLevel(1).isAnimal)
        assertFalse(PieceSkin.forLevel(9).isAnimal)
        assertFalse(PieceSkin.forLevel(11).isAnimal)
        assertFalse(PieceSkin.cats().isAnimal)
        for (level in bosses) {
            val skin = PieceSkin.forLevel(level)
            assertTrue(skin.isAnimal)
            assertEquals(BossAnimalSet.setFor(level), skin.set)
        }
    }
}
