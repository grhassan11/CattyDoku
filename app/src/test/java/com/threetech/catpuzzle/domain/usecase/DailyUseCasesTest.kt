package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.engine.DailyAvailability
import com.threetech.catpuzzle.domain.engine.DailyRules
import com.threetech.catpuzzle.domain.engine.SeededRng
import com.threetech.catpuzzle.domain.model.GameResult
import com.threetech.catpuzzle.domain.model.GameState
import com.threetech.catpuzzle.domain.model.LevelProgress
import com.threetech.catpuzzle.domain.model.SavedGame
import com.threetech.catpuzzle.domain.model.ScoreBreakdown
import com.threetech.catpuzzle.ui.screens.game.FakeAppFlagsRepository
import com.threetech.catpuzzle.ui.screens.game.RecordingDailyGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyUseCasesTest {
    private val puzzles = FakePuzzleRepository()
    private val progress = FakeProgressRepository()
    private val streak = FakeStreakRepository()
    private val saved = FakeSavedGameRepository()
    private val time = FakeTimeProvider(now = 5_000L, today = 20_752L)
    private val gen = RecordingDailyGenerator()

    private fun resolver(generator: DailyGenerator = gen, attempts: Int = 5) =
        ResolveDaily(puzzles, generator, time, dispatcher = Dispatchers.Unconfined, maxAttempts = attempts)

    private fun won(key: Int) = LevelProgress(key, 500, 1_000L, 1L, 0, 0)

    // ---- ResolveDaily ----

    @Test
    fun generatesWithDailySpecAndSeedAndCachesUnderDailyKey() = runTest {
        val r = resolver()()
        assertTrue(r is DailyLoadResult.Ready)
        r as DailyLoadResult.Ready
        val key = DailyRules.keyForDay(20_752L)
        assertEquals(20_752L, r.epochDay)
        assertEquals(key, r.puzzle.levelIndex)
        assertEquals(key, gen.specs.single().levelIndex)
        assertEquals(6, gen.specs.single().size)
        assertEquals(DailyRules.seedFor(DEFAULT_SEED, 20_752L), gen.seeds.single())
        assertEquals(listOf(key), puzzles.rows.keys.toList()) // nothing under a level key
    }

    @Test
    fun cachedDailyWinsAndGeneratorIsNotCalledAgain() = runTest {
        val first = resolver()() as DailyLoadResult.Ready
        val second = resolver()() as DailyLoadResult.Ready
        assertEquals(1, gen.specs.size)
        assertEquals(first.puzzle, second.puzzle)
    }

    @Test
    fun dateRolloverGivesNewPuzzleAndKeepsOldOne() = runTest {
        val r = resolver()
        val d1 = r() as DailyLoadResult.Ready
        time.today = 20_753L
        val d2 = r() as DailyLoadResult.Ready
        assertEquals(20_753L, d2.epochDay)
        assertNotEquals(d1.puzzle.levelIndex, d2.puzzle.levelIndex)
        assertEquals(2, gen.specs.size)
        assertEquals(2, puzzles.rows.size)
        // going back (clock change) serves the cached day again
        time.today = 20_752L
        assertEquals(d1.puzzle, (r() as DailyLoadResult.Ready).puzzle)
        assertEquals(2, gen.specs.size)
    }

    @Test
    fun retriesWithDerivedSeedsThenFailsWithoutCaching() = runTest {
        val failing = RecordingDailyGenerator(succeed = false)
        val r = resolver(failing, attempts = 3)()
        assertTrue(r is DailyLoadResult.Failed)
        assertEquals(3, failing.seeds.size)
        assertEquals(3, failing.seeds.toSet().size)
        assertEquals(DailyRules.seedFor(DEFAULT_SEED, 20_752L), failing.seeds[0])
        assertEquals(SeededRng.mix(DailyRules.seedFor(DEFAULT_SEED, 20_752L), 1L), failing.seeds[1])
        assertTrue(puzzles.rows.isEmpty())
    }

    @Test
    fun realEngineBuildsValidDeterministicSixBySix() = runTest {
        val engine = ResolveDaily(puzzles, EngineDailyGenerator(), time, dispatcher = Dispatchers.Unconfined)
        val a = engine() as DailyLoadResult.Ready
        assertEquals(6, a.puzzle.size)
        assertEquals(DailyRules.keyForDay(20_752L), a.puzzle.levelIndex)
        // same date on a second device (empty cache) => identical puzzle
        val other = ResolveDaily(FakePuzzleRepository(), EngineDailyGenerator(), time, dispatcher = Dispatchers.Unconfined)
        assertEquals(a.puzzle, (other() as DailyLoadResult.Ready).puzzle)
    }

    // ---- completion recording (CompleteLevel under the daily key) ----

    private fun dailyResult(key: Int) =
        GameResult(key, true, ScoreBreakdown(400, 0, 0, 0, 400), heartsLeft = 3, hintsUsed = 0, elapsedSeconds = 40)

    @Test
    fun completingDailyRecordsCompletionStreakAndClearsSaveButNotLevelProgress() = runTest {
        val key = DailyRules.keyForDay(20_752L)
        saved.rows[key] = SavedGame(key, GameState.initial(6), 1_000L, 1L)
        val out = CompleteLevel(progress, streak, saved, time)(dailyResult(key))

        assertNotNull(out.progress)
        assertTrue(out.streakDayAdded)
        assertNull(saved.rows[key])
        assertEquals(setOf(20_752L), ObserveCompletedDays(progress)().first())
        assertTrue(progress.observeAll().first().isEmpty()) // level view untouched
        assertTrue(20_752L in streak.observePlayedDays().first())
    }

    @Test
    fun completedDaysAreDatesAndIgnoreLevels() = runTest {
        progress.save(won(3))
        progress.save(won(DailyRules.keyForDay(20_750L)))
        progress.save(won(DailyRules.keyForDay(20_752L)))
        assertEquals(setOf(20_750L, 20_752L), ObserveCompletedDays(progress)().first())
    }

    // ---- ObserveDailyStatus ----

    private fun status() = ObserveDailyStatus(progress, ObserveCompletedDays(progress), time)

    @Test
    fun lockedUntilLevelFiveWon() = runTest {
        assertEquals(DailyAvailability.Locked(5), status()().first().availability)
        for (l in 1..4) progress.save(won(l))
        assertEquals(DailyAvailability.Locked(5), status()().first().availability)
        progress.save(won(5))
        assertEquals(DailyAvailability.Available, status()().first().availability)
    }

    @Test
    fun doneTodayOnlyWhenTodaysDateCompleted() = runTest {
        for (l in 1..5) progress.save(won(l))
        progress.save(won(DailyRules.keyForDay(20_751L))) // yesterday
        assertEquals(DailyAvailability.Available, status()().first().availability)

        progress.save(won(DailyRules.keyForDay(20_752L)))
        val s = status()().first()
        assertEquals(DailyAvailability.Done, s.availability)
        assertTrue(s.doneToday)

        time.today = 20_753L // midnight passed, re-collected
        assertEquals(DailyAvailability.Available, status()().first().availability)
    }

    @Test
    fun dailyCompletionAloneNeverUnlocks() = runTest {
        progress.save(won(DailyRules.keyForDay(20_752L)))
        assertEquals(DailyAvailability.Locked(5), status()().first().availability)
        assertEquals(0, status()().first().highestLevelWon)
    }

    // ---- ObserveStreakCalendar + autosave isolation ----

    @Test
    fun streakCalendarCombinesPlayedAndDailyDays() = runTest {
        streak.recordPlayed(20_751L)
        streak.recordPlayed(20_752L)
        progress.save(won(DailyRules.keyForDay(20_752L)))
        val c = ObserveStreakCalendar(streak, ObserveCompletedDays(progress), time)().first()
        assertEquals(2, c.info.current)
        assertEquals(1, c.dailyCompletedTotal)
        assertTrue(c.recent.last().dailyDone)
    }

    @Test
    fun dailyAutosaveNeverHijacksLevelStart() = runTest {
        val key = DailyRules.keyForDay(20_752L)
        saved.rows[3] = SavedGame(3, GameState.initial(4), 0L, 10L)
        saved.rows[key] = SavedGame(key, GameState.initial(6), 0L, 99L) // newer
        val start = ResolveStartLevel(saved, FakeAppFlagsRepository(), progress)()
        assertEquals(3, start)
    }

    private companion object {
        const val DEFAULT_SEED = GetOrCreatePuzzle.DEFAULT_BASE_SEED
    }
}
