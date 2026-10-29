package com.threetech.catpuzzle.domain.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeededRngTest {
    @Test
    fun splitMix64ReferenceVector() {
        // Published SplitMix64 first output for seed 0.
        assertEquals(0xE220A8397B1DCDAFUL.toLong(), SeededRng(0L).nextLong())
    }

    @Test
    fun sameSeedSameSequence() {
        val a = SeededRng(12345L)
        val b = SeededRng(12345L)
        repeat(50) { assertEquals(a.nextLong(), b.nextLong()) }
    }

    @Test
    fun nextIntInRange() {
        val r = SeededRng(7L)
        repeat(1000) {
            val v = r.nextInt(9)
            assertTrue(v in 0..8)
        }
    }

    @Test
    fun shuffleDeterministicPermutation() {
        val a = IntArray(9) { it }
        val b = IntArray(9) { it }
        SeededRng(99L).shuffle(a)
        SeededRng(99L).shuffle(b)
        assertArrayEquals(a, b)
        assertEquals((0..8).toList(), a.sorted())
    }
}
