package com.threetech.catpuzzle.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldRulesTest {

    @Test
    fun freshPlayerSeesSixWorlds() {
        assertEquals(6, WorldRules.visibleWorlds(1))
        assertEquals(60, WorldRules.visibleLevels(1))
    }

    @Test
    fun sixWorldsStayOpenUntilTheLastOpenWorldIsReached() {
        assertEquals(6, WorldRules.visibleWorlds(40))
        assertEquals(6, WorldRules.visibleWorlds(50)) // last level of world 5
    }

    @Test
    fun reachingLevel51OpensThreeMoreWorlds() {
        assertEquals(9, WorldRules.visibleWorlds(51)) // first level of world 6
        assertEquals(90, WorldRules.visibleLevels(51))
        assertEquals(9, WorldRules.visibleWorlds(80))
    }

    @Test
    fun reachingLevel81OpensUpToLevel120() {
        assertEquals(12, WorldRules.visibleWorlds(81)) // first level of world 9
        assertEquals(120, WorldRules.visibleLevels(81))
        assertEquals(12, WorldRules.visibleWorlds(110))
        assertEquals(15, WorldRules.visibleWorlds(111)) // first level of world 12
    }

    @Test
    fun visibleWorldsIsSixPlusThreeKAndStrictlyAboveTheCurrentWorld() {
        for (level in 1..2_000) {
            val visible = WorldRules.visibleWorlds(level)
            assertEquals(0, (visible - 6) % 3)
            assertTrue(visible >= 6)
            assertTrue("level $level", visible > WorldRules.worldNumber(level))
            // smallest such value: three fewer would no longer be above the current world
            assertFalse("level $level", visible - 3 >= 6 && visible - 3 > WorldRules.worldNumber(level))
        }
    }

    @Test
    fun levelAlwaysFitsInsideTheVisibleRange() {
        for (level in 1..2_000) assertTrue(level <= WorldRules.visibleLevels(level))
    }

    @Test
    fun nonPositiveLevelCountsAsLevelOne() {
        assertEquals(6, WorldRules.visibleWorlds(0))
        assertEquals(6, WorldRules.visibleWorlds(-5))
    }

    @Test
    fun themesCycleAfterWorldTen() {
        assertEquals(0, WorldRules.themeIndex(WorldRules.firstLevelOf(11)))
        assertEquals(11, WorldRules.worldNumber(WorldRules.firstLevelOf(11)))
        assertEquals(9, WorldRules.themeIndex(WorldRules.firstLevelOf(10)))
    }

    @Test
    fun bossIsEveryTenthLevel() {
        assertTrue(WorldRules.isBoss(10))
        assertTrue(WorldRules.isBoss(120))
        assertFalse(WorldRules.isBoss(11))
    }

    @Test
    fun starsFollowBestRunSlips() {
        fun p(m: Int, h: Int) = LevelProgress(1, 100, 1_000L, 1L, m, h)
        assertEquals(3, LevelStars.of(p(0, 0)))
        assertEquals(2, LevelStars.of(p(1, 0)))
        assertEquals(2, LevelStars.of(p(1, 1)))
        assertEquals(1, LevelStars.of(p(2, 1)))
    }
}
