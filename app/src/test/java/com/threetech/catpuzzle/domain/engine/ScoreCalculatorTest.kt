package com.threetech.catpuzzle.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreCalculatorTest {
    private val calc = ScoreCalculator()
    private val cfg = ScoreConfig.DEFAULT

    private fun input(
        placements: Int = 5,
        hearts: Int = 0,
        hints: Int = 0,
        seconds: Int = 30,
        won: Boolean = true,
        size: Int = 5,
    ) = ScoreInput(size, placements, hearts, hints, seconds, won)

    @Test
    fun deterministic() {
        assertEquals(calc.compute(input()), calc.compute(input()))
    }

    @Test
    fun breakdownAddsUp() {
        val s = calc.compute(input(placements = 5, hearts = 1, hints = 2, seconds = 0))
        assertEquals(5 * cfg.pointsPerCorrectPlacement, s.placementPoints)
        assertEquals(cfg.timeBonusMax, s.timeBonus)
        assertEquals(cfg.heartPenalty, s.heartPenalty)
        assertEquals(2 * cfg.hintPenalty, s.hintPenalty)
        assertEquals(s.placementPoints + s.timeBonus - s.heartPenalty - s.hintPenalty, s.total)
    }

    @Test
    fun fasterIsHigherOrEqual() {
        var prev = Int.MAX_VALUE
        for (t in 0..400 step 5) {
            val total = calc.compute(input(seconds = t)).total
            assertTrue("t=$t", total <= prev)
            prev = total
        }
        assertTrue(calc.compute(input(seconds = 10)).total > calc.compute(input(seconds = 60)).total)
    }

    @Test
    fun timeBonusZeroPastBudget() {
        val budget = cfg.timeBudgetSecondsPerCat * 5
        assertEquals(0, calc.compute(input(seconds = budget)).timeBonus)
        assertEquals(0, calc.compute(input(seconds = budget * 10)).timeBonus)
    }

    @Test
    fun moreHintsLowerScore() {
        var prev = Int.MAX_VALUE
        for (h in 0..5) {
            val total = calc.compute(input(hints = h)).total
            assertTrue(total < prev)
            prev = total
        }
    }

    @Test
    fun moreHeartsLostLowerScore() {
        val a = calc.compute(input(hearts = 0)).total
        val b = calc.compute(input(hearts = 2)).total
        assertTrue(b < a)
    }

    @Test
    fun neverNegative() {
        val s = calc.compute(input(placements = 0, hearts = 3, hints = 50, seconds = 9999))
        assertEquals(0, s.total)
        assertTrue(s.heartPenalty > 0)
    }

    @Test
    fun lostGetsNoTimeBonus() {
        val s = calc.compute(input(won = false, seconds = 0))
        assertEquals(0, s.timeBonus)
    }

    @Test
    fun customConfigIsHonoured() {
        val c = ScoreCalculator(ScoreConfig(pointsPerCorrectPlacement = 10, timeBonusMax = 0, heartPenalty = 1, hintPenalty = 1))
        assertEquals(49, c.compute(input(placements = 5, hearts = 1)).total)
    }
}
