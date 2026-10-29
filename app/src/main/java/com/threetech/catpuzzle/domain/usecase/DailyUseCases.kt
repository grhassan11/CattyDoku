package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.engine.DailyRules
import com.threetech.catpuzzle.domain.engine.GenerationResult
import com.threetech.catpuzzle.domain.engine.GeneratorConfig
import com.threetech.catpuzzle.domain.engine.PuzzleGenerator
import com.threetech.catpuzzle.domain.engine.StreakCalendarBuilder
import com.threetech.catpuzzle.domain.model.DailyStatus
import com.threetech.catpuzzle.domain.model.LevelSpec
import com.threetech.catpuzzle.domain.model.Puzzle
import com.threetech.catpuzzle.domain.model.StreakCalendar
import com.threetech.catpuzzle.domain.repository.ProgressRepository
import com.threetech.catpuzzle.domain.repository.PuzzleRepository
import com.threetech.catpuzzle.domain.repository.StreakRepository
import com.threetech.catpuzzle.domain.time.TimeProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Seam over [PuzzleGenerator] for the daily (explicit spec + seed, no level curve). Tests inject failures. */
fun interface DailyGenerator {
    fun generate(spec: LevelSpec, seed: Long): GenerationResult
}

class EngineDailyGenerator(private val config: GeneratorConfig = GeneratorConfig.DEFAULT) : DailyGenerator {
    override fun generate(spec: LevelSpec, seed: Long): GenerationResult =
        PuzzleGenerator.generate(spec, seed, config)
}

sealed interface DailyLoadResult {
    /** [epochDay] = the date this puzzle belongs to (fixed for the whole play session, even past midnight). */
    data class Ready(val epochDay: Long, val puzzle: Puzzle) : DailyLoadResult

    data class Failed(val reason: String) : DailyLoadResult
}

/**
 * Today's daily puzzle, cache-first (cache key = [DailyRules.keyForDay]). A cached daily always wins, so a
 * half-played or finished daily never changes. Deterministic: same date + base seed => same puzzle.
 * The date is read once at call time (local calendar day via [TimeProvider]).
 */
class ResolveDaily(
    private val cache: PuzzleRepository,
    private val generator: DailyGenerator,
    private val time: TimeProvider,
    private val baseSeed: Long = GetOrCreatePuzzle.DEFAULT_BASE_SEED,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val maxAttempts: Int = StartLevel.DEFAULT_MAX_ATTEMPTS,
) {
    suspend operator fun invoke(): DailyLoadResult {
        val day = time.todayEpochDay()
        val key = DailyRules.keyForDay(day)
        cache.get(key)?.let { return DailyLoadResult.Ready(day, it) }

        val spec = DailyRules.specFor(day)
        var reason = "no attempt"
        var created: Puzzle? = null
        for (attempt in 0 until maxAttempts) {
            val seed = DailyRules.retrySeed(baseSeed, day, attempt)
            when (val r = withContext(dispatcher) { generator.generate(spec, seed) }) {
                is GenerationResult.Success -> created = r.puzzle
                is GenerationResult.Failure -> reason = r.reason
            }
            if (created != null) break
        }
        val puzzle = created ?: return DailyLoadResult.Failed(reason)
        if (!cache.putIfAbsent(puzzle)) {
            // Lost a race with another writer: the stored one wins.
            cache.get(key)?.let { return DailyLoadResult.Ready(day, it) }
        }
        return DailyLoadResult.Ready(day, puzzle)
    }
}

/** Local dates whose daily was completed (live). */
class ObserveCompletedDays(private val progress: ProgressRepository) {
    operator fun invoke(): Flow<Set<Long>> =
        progress.observeDailyCompletions().map { rows -> rows.map { DailyRules.dayForKey(it.levelIndex) }.toSet() }
}

/**
 * Live Home status: unlock state (level [DailyRules.UNLOCK_LEVEL] won) + done-today. "Today" is read per
 * emission; the UI re-collects on resume to cross midnight.
 */
class ObserveDailyStatus(
    private val progress: ProgressRepository,
    private val completedDays: ObserveCompletedDays,
    private val time: TimeProvider,
) {
    operator fun invoke(): Flow<DailyStatus> =
        combine(progress.observeAll(), completedDays()) { levels, done ->
            val today = time.todayEpochDay()
            val highest = levels.maxOfOrNull { it.levelIndex } ?: 0
            DailyStatus(
                today = today,
                highestLevelWon = highest,
                completedDays = done,
                availability = DailyRules.availability(highest, today in done),
            )
        }
}

/** Streak dialog data: streak summary + last 30 days incl. daily completions. */
class ObserveStreakCalendar(
    private val streak: StreakRepository,
    private val completedDays: ObserveCompletedDays,
    private val time: TimeProvider,
) {
    operator fun invoke(): Flow<StreakCalendar> =
        combine(streak.observePlayedDays(), completedDays()) { played, done ->
            StreakCalendarBuilder.build(played, done, time.todayEpochDay())
        }
}
