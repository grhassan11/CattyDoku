package com.tm.meow.sudoku.puzzle.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterCatPolicyTest {
    @Test
    fun throughLevelIsTwenty() = assertEquals(20, StarterCatPolicy.STARTER_CAT_THROUGH_LEVEL)

    @Test
    fun levelsOneToTwentyApply() {
        for (l in 1..20) assertTrue("level $l", StarterCatPolicy.appliesTo(l))
    }

    @Test
    fun levelTwentyOneAndAboveDoNot() {
        assertFalse(StarterCatPolicy.appliesTo(21))
        assertFalse(StarterCatPolicy.appliesTo(500))
    }

    @Test
    fun dailyNegativeKeyAndZeroDoNot() {
        assertFalse(StarterCatPolicy.appliesTo(0))
        assertFalse(StarterCatPolicy.appliesTo(-20_000))
    }
}
