package com.threetech.catpuzzle.domain.engine

/**
 * SplitMix64. Deterministic across devices/JVMs; no java.util.Random, no kotlin.random.Default.
 */
class SeededRng(seed: Long) {
    private var state: Long = seed

    fun nextLong(): Long {
        state += GOLDEN
        var z = state
        z = (z xor (z ushr 30)) * MUL1
        z = (z xor (z ushr 27)) * MUL2
        return z xor (z ushr 31)
    }

    /** Uniform-ish int in [0, bound). Modulo bias is negligible for small bounds. */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be > 0" }
        return ((nextLong() ushr 1) % bound.toLong()).toInt()
    }

    /** In-place Fisher-Yates. */
    fun shuffle(a: IntArray) {
        for (i in a.size - 1 downTo 1) {
            val j = nextInt(i + 1)
            val t = a[i]
            a[i] = a[j]
            a[j] = t
        }
    }

    fun <T> shuffle(list: MutableList<T>) {
        for (i in list.size - 1 downTo 1) {
            val j = nextInt(i + 1)
            val t = list[i]
            list[i] = list[j]
            list[j] = t
        }
    }

    companion object {
        private val GOLDEN: Long = 0x9E3779B97F4A7C15UL.toLong()
        private val MUL1: Long = 0xBF58476D1CE4E5B9UL.toLong()
        private val MUL2: Long = 0x94D049BB133111EBUL.toLong()

        /** Stateless seed mixer (derive per-level seeds from a base seed). */
        fun mix(a: Long, b: Long): Long {
            var z = a + b * GOLDEN + GOLDEN
            z = (z xor (z ushr 30)) * MUL1
            z = (z xor (z ushr 27)) * MUL2
            return z xor (z ushr 31)
        }
    }
}
