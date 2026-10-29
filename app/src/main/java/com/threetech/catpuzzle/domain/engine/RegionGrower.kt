package com.threetech.catpuzzle.domain.engine

/**
 * Organic region growth from one seed cell per region.
 * Each step adds one unassigned cell 4-adjacent to the chosen region, so regions stay contiguous.
 * Region choice: undersized regions (below min) first; otherwise with [balancePercent] chance the
 * smallest region, else a uniformly random one. Returns null if the min-size floor was not met.
 */
object RegionGrower {

    fun grow(
        size: Int,
        seedCells: IntArray,
        minRegionSize: Int,
        balancePercent: Int,
        rng: SeededRng,
    ): IntArray? {
        require(seedCells.size == size) { "one seed per region" }
        val total = size * size
        val regions = IntArray(total) { -1 }
        val sizes = IntArray(size)
        for (g in 0 until size) {
            regions[seedCells[g]] = g
            sizes[g] = 1
        }
        val frontier = Array(size) { ArrayList<Int>() }
        var remaining = total - size

        while (remaining > 0) {
            for (f in frontier) f.clear()
            for (cell in 0 until total) {
                val g = regions[cell]
                if (g < 0) continue
                val r = cell / size
                val c = cell % size
                if (r > 0 && regions[cell - size] == -1) frontier[g].add(cell - size)
                if (r < size - 1 && regions[cell + size] == -1) frontier[g].add(cell + size)
                if (c > 0 && regions[cell - 1] == -1) frontier[g].add(cell - 1)
                if (c < size - 1 && regions[cell + 1] == -1) frontier[g].add(cell + 1)
            }
            val open = (0 until size).filter { frontier[it].isNotEmpty() }
            if (open.isEmpty()) return null
            val under = open.filter { sizes[it] < minRegionSize }
            val pool = if (under.isNotEmpty()) under else open

            val g = if (under.isNotEmpty() || rng.nextInt(100) < balancePercent) {
                val smallest = pool.minOf { sizes[it] }
                val ties = pool.filter { sizes[it] == smallest }
                ties[rng.nextInt(ties.size)]
            } else {
                pool[rng.nextInt(pool.size)]
            }
            val f = frontier[g]
            val cell = f[rng.nextInt(f.size)]
            regions[cell] = g
            sizes[g]++
            remaining--
        }

        for (g in 0 until size) if (sizes[g] < minRegionSize) return null
        return regions
    }
}
