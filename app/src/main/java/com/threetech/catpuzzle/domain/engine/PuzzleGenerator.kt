package com.threetech.catpuzzle.domain.engine

import com.threetech.catpuzzle.domain.model.LevelSpec
import com.threetech.catpuzzle.domain.model.Puzzle
import kotlin.math.abs

/**
 * Budgets for [PuzzleGenerator]. All counts, no wall clock => deterministic.
 * [stageAttempts]: attempts allowed per relaxation stage (last value reused for later stages).
 */
class GeneratorConfig(
    val stageAttempts: List<Int> = listOf(30, 30, 40, 60, 80, 120),
    val maxRepairsPerAttempt: Int = 60,
    val nodeCapPerCount: Int = 100_000,
    val totalNodeBudget: Long = 10_000_000L,
) {
    fun attemptsFor(stage: Int): Int =
        if (stageAttempts.isEmpty()) 0 else stageAttempts[minOf(stage, stageAttempts.lastIndex)]

    companion object {
        val DEFAULT = GeneratorConfig()
    }
}

/** [relaxedAboveTarget]: puzzle needs a harder technique than the spec target (last-resort stages). */
class GenStats(
    val attempts: Int,
    val repairs: Int,
    val solverNodes: Long,
    val stageIndex: Int,
    val openingSingles: Int,
    val relaxedAboveTarget: Boolean,
)

sealed interface GenerationResult {
    class Success(val puzzle: Puzzle, val stats: GenStats) : GenerationResult
    class Failure(val reason: String, val attempts: Int, val solverNodes: Long) : GenerationResult
}

/**
 * Deterministic generator: same (spec, seed, config) => same result on every device.
 *
 * Per attempt: pick a valid cat placement, grow regions around the cats, repair until the
 * solution is unique, relabel regions, then gate on the logic solver. Stages relax the gate
 * (technique window, min region size, finally a harder ceiling). Never returns a board that is
 * not unique and logic-solvable; exhausting budgets yields [GenerationResult.Failure].
 */
object PuzzleGenerator {

    private const val ENUMERATE_UP_TO = 7

    private data class Stage(
        val minTech: Int,
        val maxTech: Int,
        val minRegion: Int,
        val minOpening: Int,
    )

    fun generateForLevel(
        level: Int,
        baseSeed: Long,
        config: GeneratorConfig = GeneratorConfig.DEFAULT,
    ): GenerationResult {
        val spec = DifficultyCurve.specFor(level)
        return generate(spec, SeededRng.mix(baseSeed, spec.levelIndex.toLong()), config)
    }

    fun generate(
        spec: LevelSpec,
        seed: Long,
        config: GeneratorConfig = GeneratorConfig.DEFAULT,
    ): GenerationResult {
        val n = spec.size
        if (n < MIN_BOARD_SIZE || n > MAX_BOARD_SIZE) {
            return GenerationResult.Failure("unsupported size $n", 0, 0L)
        }
        val rng = SeededRng(seed)
        val placements: List<IntArray>? = if (n <= ENUMERATE_UP_TO) enumeratePlacements(n) else null
        if (placements != null && placements.isEmpty()) {
            return GenerationResult.Failure("no valid cat placement for size $n", 0, 0L)
        }

        val stages = buildStages(spec)
        var attempts = 0
        var repairs = 0
        var nodes = 0L

        for (si in stages.indices) {
            val stage = stages[si]
            for (a in 0 until config.attemptsFor(si)) {
                attempts++

                val sol: IntArray = if (placements != null) {
                    placements[rng.nextInt(placements.size)]
                } else {
                    randomPlacement(n, rng) ?: continue
                }
                val seedCells = IntArray(n) { it * n + sol[it] }
                val regions = RegionGrower.grow(n, seedCells, stage.minRegion, spec.balancePercent, rng)
                    ?: continue

                // repair-to-unique (region id == row index of its cat until relabel)
                var unique = false
                var iter = 0
                while (true) {
                    val cr = SolutionCounter.count(n, regions, 2, config.nodeCapPerCount)
                    nodes += cr.nodes
                    if (nodes > config.totalNodeBudget) {
                        return GenerationResult.Failure("node budget exhausted", attempts, nodes)
                    }
                    if (cr.outcome == SolutionCounter.Outcome.UNIQUE) {
                        unique = cr.solutions[0].contentEquals(sol)
                        break
                    }
                    if (cr.outcome != SolutionCounter.Outcome.MULTI) break
                    if (iter >= config.maxRepairsPerAttempt) break
                    iter++
                    repairs++
                    if (!repair(n, regions, sol, cr.solutions, stage.minRegion, rng)) break
                }
                if (!unique) continue

                // relabel regions so colours do not correlate with rows
                val perm = IntArray(n) { it }
                rng.shuffle(perm)
                val finalRegions = IntArray(n * n) { perm[regions[it]] }

                val lr = LogicSolver.solve(n, finalRegions)
                if (!lr.solved) continue
                if (!lr.cats.contentEquals(sol)) continue
                if (lr.maxLevel < stage.minTech || lr.maxLevel > stage.maxTech) continue
                if (lr.openingSingles < stage.minOpening) continue

                val puzzle = Puzzle(
                    size = n,
                    regions = finalRegions,
                    solution = sol.copyOf(),
                    seed = seed,
                    levelIndex = spec.levelIndex,
                    techniqueLevel = lr.maxLevel,
                    generatorVersion = GENERATOR_VERSION,
                )
                val stats = GenStats(
                    attempts = attempts,
                    repairs = repairs,
                    solverNodes = nodes,
                    stageIndex = si,
                    openingSingles = lr.openingSingles,
                    relaxedAboveTarget = lr.maxLevel > spec.targetTechnique,
                )
                return GenerationResult.Success(puzzle, stats)
            }
        }
        return GenerationResult.Failure("attempt budget exhausted", attempts, nodes)
    }

    /** Strict -> relaxed. Last two stages may exceed the target technique (flagged in stats). */
    private fun buildStages(spec: LevelSpec): List<Stage> {
        val t = spec.targetTechnique.coerceIn(1, Technique.MAX)
        val minR = spec.minRegionSize.coerceAtLeast(1)
        val open = spec.minOpeningSingles.coerceAtLeast(0)
        return listOf(
            Stage(t, t, minR, open),
            Stage((t - 1).coerceAtLeast(1), t, minR, open),
            Stage(1, t, minR, 0),
            Stage(1, t, 1, 0),
            Stage(1, minOf(t + 1, Technique.MAX), 1, 0),
            Stage(1, Technique.MAX, 1, 0),
        ).distinct()
    }

    /**
     * Break the alternate solution by moving one of its cat cells (never a true-solution cat)
     * into an adjacent region. That cell's old region keeps contiguity and min size; the alt then
     * holds two cats in the receiving region, so it is no longer valid.
     */
    private fun repair(
        n: Int,
        regions: IntArray,
        sol: IntArray,
        found: List<IntArray>,
        minRegion: Int,
        rng: SeededRng,
    ): Boolean {
        val alt = found.firstOrNull { !it.contentEquals(sol) } ?: return false
        val moves = ArrayList<IntArray>()
        for (r in 0 until n) {
            if (alt[r] == sol[r]) continue
            val cell = r * n + alt[r]
            val from = regions[cell]
            val row = cell / n
            val col = cell % n
            if (row > 0 && regions[cell - n] != from) moves.add(intArrayOf(cell, regions[cell - n]))
            if (row < n - 1 && regions[cell + n] != from) moves.add(intArrayOf(cell, regions[cell + n]))
            if (col > 0 && regions[cell - 1] != from) moves.add(intArrayOf(cell, regions[cell - 1]))
            if (col < n - 1 && regions[cell + 1] != from) moves.add(intArrayOf(cell, regions[cell + 1]))
        }
        rng.shuffle(moves)
        for (m in moves) {
            val cell = m[0]
            val from = regions[cell]
            var fromSize = 0
            for (g in regions) if (g == from) fromSize++
            if (fromSize - 1 < minRegion) continue
            if (!connectedWithout(n, regions, from, cell, from * n + sol[from])) continue
            regions[cell] = m[1]
            return true
        }
        return false
    }

    /** Is region [id] still connected (4-neighbour) after removing [removed]? BFS from [seedCell]. */
    private fun connectedWithout(n: Int, regions: IntArray, id: Int, removed: Int, seedCell: Int): Boolean {
        var total = 0
        for (i in regions.indices) if (regions[i] == id && i != removed) total++
        val seen = BooleanArray(n * n)
        val queue = IntArray(n * n)
        var head = 0
        var tail = 0
        queue[tail++] = seedCell
        seen[seedCell] = true
        var reached = 1
        while (head < tail) {
            val cell = queue[head++]
            val r = cell / n
            val c = cell % n
            for (d in 0..3) {
                val nb = when (d) {
                    0 -> if (r > 0) cell - n else -1
                    1 -> if (r < n - 1) cell + n else -1
                    2 -> if (c > 0) cell - 1 else -1
                    else -> if (c < n - 1) cell + 1 else -1
                }
                if (nb < 0 || nb == removed || seen[nb] || regions[nb] != id) continue
                seen[nb] = true
                queue[tail++] = nb
                reached++
            }
        }
        return reached == total
    }

    /** All valid placements (one per row/col, no touching) in lexicographic order. */
    private fun enumeratePlacements(n: Int): List<IntArray> {
        val out = ArrayList<IntArray>()
        val cols = IntArray(n)
        fun dfs(row: Int, used: Int) {
            if (row == n) {
                out.add(cols.copyOf())
                return
            }
            for (c in 0 until n) {
                if (((used shr c) and 1) == 1) continue
                if (row > 0 && abs(c - cols[row - 1]) <= 1) continue
                cols[row] = c
                dfs(row + 1, used or (1 shl c))
            }
        }
        dfs(0, 0)
        return out
    }

    /** Randomised DFS placement for larger boards. */
    private fun randomPlacement(n: Int, rng: SeededRng): IntArray? {
        val cols = IntArray(n)
        fun dfs(row: Int, used: Int): Boolean {
            if (row == n) return true
            val order = IntArray(n) { it }
            rng.shuffle(order)
            for (c in order) {
                if (((used shr c) and 1) == 1) continue
                if (row > 0 && abs(c - cols[row - 1]) <= 1) continue
                cols[row] = c
                if (dfs(row + 1, used or (1 shl c))) return true
            }
            return false
        }
        return if (dfs(0, 0)) cols else null
    }
}
