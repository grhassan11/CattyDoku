package com.tm.meow.sudoku.puzzle.domain.engine

import com.tm.meow.sudoku.puzzle.domain.model.HintRule
import org.junit.Assert.assertEquals
import org.junit.Test

class HintRuleTest {
    @Test
    fun familyMapping() {
        assertEquals(HintRule.REGION, HintRule.fromFamily(0))
        assertEquals(HintRule.ROW, HintRule.fromFamily(1))
        assertEquals(HintRule.COLUMN, HintRule.fromFamily(2))
    }
}
