package com.threetech.catpuzzle.domain.engine

import com.threetech.catpuzzle.domain.model.LevelSpec
import java.time.LocalDate

/** What the Home "Daily Challenge" button should offer. */
sealed interface DailyAvailability {
    /** Level [requiredLevel] has not been won yet. */
    data class Locked(val requiredLevel: Int) : DailyAvailability

    data object Available : DailyAvailability

    /** Today's daily is already completed (replay disabled). */
    data object Done : DailyAvailability
}

/**
 * Pure rules of the Daily Challenge. No clock, no Android.
 *
 * Storage namespace: a daily puzzle / autosave / completion reuses the existing level tables under a
 * NEGATIVE key, [keyForDay] = -epochDay. Real levels are 1-based and positive, so keys never collide and
 * no Room schema change is needed. Repositories keep the two spaces apart (see SavedGameRepository.getLatest,
 * ProgressRepository.observeAll / observeDailyCompletions).
 */
object DailyRules {
    /** Daily unlocks once this level has been WON. */
    const val UNLOCK_LEVEL = 5

    /** Fixed daily board size (6x6); difficulty rotates by weekday. */
    const val SIZE = 6

    /** Distinct seed domain ("DAILY") so a daily seed can never be a level seed. Never change. */
    const val SEED_DOMAIN: Long = 0x4441494C59L

    /** Target technique per weekday, Monday first (the 6x6 band of DifficultyCurve uses 3..5). */
    private val TARGET_BY_WEEKDAY = intArrayOf(3, 3, 4, 4, 4, 5, 5)

    private const val MIN_REGION = 2
    private const val BALANCE = 50

    /** Storage key of the daily for [epochDay]. Always negative. */
    fun keyForDay(epochDay: Long): Int = -epochDay.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()

    fun isDailyKey(key: Int): Boolean = key < 0

    /** Inverse of [keyForDay]. */
    fun dayForKey(key: Int): Long = -key.toLong()

    /** Deterministic daily seed: mix(mix(baseSeed, SEED_DOMAIN), epochDay). Same date => same puzzle everywhere. */
    fun seedFor(baseSeed: Long, epochDay: Long): Long =
        SeededRng.mix(SeededRng.mix(baseSeed, SEED_DOMAIN), epochDay)

    /** Seed for retry [attempt] (0 = the plain daily seed), same scheme as StartLevel retries. */
    fun retrySeed(baseSeed: Long, epochDay: Long, attempt: Int): Long {
        val s = seedFor(baseSeed, epochDay)
        return if (attempt == 0) s else SeededRng.mix(s, attempt.toLong())
    }

    fun targetTechniqueFor(epochDay: Long): Int =
        TARGET_BY_WEEKDAY[LocalDate.ofEpochDay(epochDay).dayOfWeek.value - 1]

    fun specFor(epochDay: Long): LevelSpec = LevelSpec(
        levelIndex = keyForDay(epochDay),
        size = SIZE,
        targetTechnique = targetTechniqueFor(epochDay),
        minRegionSize = MIN_REGION,
        balancePercent = BALANCE,
        minOpeningSingles = 0,
    )

    fun isUnlocked(highestLevelWon: Int): Boolean = highestLevelWon >= UNLOCK_LEVEL

    fun availability(highestLevelWon: Int, doneToday: Boolean): DailyAvailability = when {
        !isUnlocked(highestLevelWon) -> DailyAvailability.Locked(UNLOCK_LEVEL)
        doneToday -> DailyAvailability.Done
        else -> DailyAvailability.Available
    }
}
