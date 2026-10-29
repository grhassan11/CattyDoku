package com.threetech.catpuzzle.ui.screens.game

import com.threetech.catpuzzle.domain.engine.CatVariants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatBitmapSamplingTest {
    @Test
    fun sampleSizeKeepsDecodedEdgeAtLeastTarget() {
        assertEquals(4, CatBitmapSampling.sampleSize(512, 119))
        assertEquals(4, CatBitmapSampling.sampleSize(512, 128))
        assertEquals(2, CatBitmapSampling.sampleSize(512, 129))
        assertEquals(1, CatBitmapSampling.sampleSize(512, 300))
        assertEquals(1, CatBitmapSampling.sampleSize(512, 512))
        assertEquals(8, CatBitmapSampling.sampleSize(512, 64))
    }

    @Test
    fun sampleSizeDegenerateInputsReturnOne() {
        assertEquals(1, CatBitmapSampling.sampleSize(0, 100))
        assertEquals(1, CatBitmapSampling.sampleSize(512, 0))
        assertEquals(1, CatBitmapSampling.sampleSize(100, 512))
    }

    @Test
    fun variantsOfReturnsDistinctSortedUsedVariantsOnly() {
        val cells = List(4) { i ->
            CellUi(
                row = 0, col = i, region = i, mark = CellMarkUi.EMPTY, conflict = false, locked = false,
                hintRole = HintRole.NONE, catVariant = intArrayOf(7, 2, 7, 12)[i],
            )
        }
        val variants = CatBitmapSampling.variantsOf(BoardUi(size = 2, cells = cells))
        assertEquals(listOf(2, 7), variants)
        assertTrue(variants.all { it in 0 until CatVariants.UNIQUE })
    }
}
