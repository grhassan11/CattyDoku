package com.threetech.catpuzzle.domain.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatVariantsTest {
    @Test fun deterministicForSameInputs() {
        assertArrayEquals(CatVariants.forRegions(42L, 7, 7), CatVariants.forRegions(42L, 7, 7))
        assertArrayEquals(CatVariants.bag(42L, 7), CatVariants.bag(42L, 7))
    }

    @Test fun differentSeedsGiveDifferentOrdersSometimes() {
        val distinct = (1L..30L).map { CatVariants.bag(it, 1).toList() }.toSet()
        assertTrue(distinct.size > 1)
    }

    @Test fun differentLevelsGiveDifferentOrdersSometimes() {
        val distinct = (1..30).map { CatVariants.bag(99L, it).toList() }.toSet()
        assertTrue(distinct.size > 1)
    }

    @Test fun bagIsPermutation() {
        for (s in 0L..50L) {
            assertEquals((0 until CatVariants.UNIQUE).toList(), CatVariants.bag(s, 3).sorted())
        }
    }

    @Test fun firstUniqueRegionsAreDistinct() {
        for (s in 0L..50L) for (n in 4..7) {
            val v = CatVariants.forRegions(s, 5, n)
            val first = v.take(minOf(n, CatVariants.UNIQUE))
            assertEquals(first.size, first.toSet().size)
        }
    }

    @Test fun repeatsOnlyAfterAllUniqueUsed() {
        val v = CatVariants.forRegions(11L, 2, CatVariants.UNIQUE + 7)
        assertEquals(CatVariants.UNIQUE, v.take(CatVariants.UNIQUE).toSet().size)
        for (i in CatVariants.UNIQUE until v.size) assertEquals(v[i - CatVariants.UNIQUE], v[i])
    }

    @Test fun indexAlwaysInRange() {
        for (s in -5L..40L) for (r in 0..12) {
            assertTrue(CatVariants.variantFor(s, 1, r) in 0 until CatVariants.UNIQUE)
        }
    }

    @Test fun variantForMatchesForRegions() {
        val v = CatVariants.forRegions(8L, 4, 7)
        for (r in 0 until 7) assertEquals(v[r], CatVariants.variantFor(8L, 4, r))
    }
}
