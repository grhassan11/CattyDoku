package com.threetech.catpuzzle.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterCatPolicyTest {
    @Test
    fun throughLevelIsTen() = assertEquals(10, StarterCatPolicy.STARTER_CAT_THROUGH_LEVEL)

    @Test
    fun levelsOneToTenApply() {
        for (l in 1..10) assertTrue("level $l", StarterCatPolicy.appliesTo(l))
    }

    @Test
    fun levelElevenAndAboveDoNot() {
        assertFalse(StarterCatPolicy.appliesTo(11))
        assertFalse(StarterCatPolicy.appliesTo(500))
    }

    @Test
    fun dailyNegativeKeyAndZeroDoNot() {
        assertFalse(StarterCatPolicy.appliesTo(0))
        assertFalse(StarterCatPolicy.appliesTo(-20_000))
    }
}
