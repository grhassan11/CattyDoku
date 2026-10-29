package com.threetech.catpuzzle.domain.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionGrowerTest {
    private val n = 5
    private val sol = intArrayOf(1, 3, 0, 2, 4)
    private val seeds = IntArray(n) { it * n + sol[it] }

    @Test
    fun contiguousAndMinSizeRespected() {
        for (s in 1L..40L) {
            val g = RegionGrower.grow(n, seeds, 2, 60, SeededRng(s)) ?: continue
            assertTrue(Fixtures.regionsContiguous(n, g))
            for (id in 0 until n) {
                assertTrue(g.count { it == id } >= 2)
                assertTrue(g[seeds[id]] == id)
            }
        }
    }

    @Test
    fun someSeedsSucceed() {
        var ok = 0
        for (s in 1L..40L) if (RegionGrower.grow(n, seeds, 2, 60, SeededRng(s)) != null) ok++
        assertTrue("growth success count $ok", ok >= 20)
    }

    @Test
    fun deterministic() {
        val a = RegionGrower.grow(n, seeds, 1, 50, SeededRng(5L))
        val b = RegionGrower.grow(n, seeds, 1, 50, SeededRng(5L))
        assertNotNull(a)
        assertArrayEquals(a, b)
    }
}
