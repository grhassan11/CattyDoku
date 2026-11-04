package com.tm.meow.sudoku.puzzle.ui.screens.game

import com.tm.meow.sudoku.puzzle.domain.analytics.RecordingAnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.engine.CatBoosterEngine
import com.tm.meow.sudoku.puzzle.domain.engine.GameEngine
import com.tm.meow.sudoku.puzzle.domain.engine.GameFixtures
import com.tm.meow.sudoku.puzzle.domain.engine.GenStats
import com.tm.meow.sudoku.puzzle.domain.engine.GenerationResult
import com.tm.meow.sudoku.puzzle.domain.engine.HintEngine
import com.tm.meow.sudoku.puzzle.domain.engine.MouseBoosterEngine
import com.tm.meow.sudoku.puzzle.domain.engine.ScoreCalculator
import androidx.lifecycle.SavedStateHandle
import com.tm.meow.sudoku.puzzle.domain.model.AppFlags
import com.tm.meow.sudoku.puzzle.domain.model.LevelSpec
import com.tm.meow.sudoku.puzzle.domain.engine.StarterCatPicker
import com.tm.meow.sudoku.puzzle.domain.model.Pos
import com.tm.meow.sudoku.puzzle.domain.model.Puzzle
import com.tm.meow.sudoku.puzzle.domain.model.Settings
import com.tm.meow.sudoku.puzzle.domain.usecase.ApplyStarterCat
import com.tm.meow.sudoku.puzzle.domain.repository.AppFlagsRepository
import com.tm.meow.sudoku.puzzle.domain.repository.SettingsRepository
import com.tm.meow.sudoku.puzzle.domain.usecase.AnalyseBoard
import com.tm.meow.sudoku.puzzle.domain.usecase.ApplyDragX
import com.tm.meow.sudoku.puzzle.domain.usecase.CompleteLevel
import com.tm.meow.sudoku.puzzle.domain.usecase.ComputeScore
import com.tm.meow.sudoku.puzzle.domain.usecase.ConsumeBooster
import com.tm.meow.sudoku.puzzle.domain.usecase.DailyGenerator
import com.tm.meow.sudoku.puzzle.domain.usecase.ResolveDaily
import com.tm.meow.sudoku.puzzle.domain.usecase.FakeInventoryRepository
import com.tm.meow.sudoku.puzzle.domain.usecase.FakeProgressRepository
import com.tm.meow.sudoku.puzzle.domain.usecase.FakePuzzleRepository
import com.tm.meow.sudoku.puzzle.domain.usecase.FakeSavedGameRepository
import com.tm.meow.sudoku.puzzle.domain.usecase.FakeStreakRepository
import com.tm.meow.sudoku.puzzle.domain.usecase.FakeTimeProvider
import com.tm.meow.sudoku.puzzle.domain.usecase.GetBoosterCharges
import com.tm.meow.sudoku.puzzle.domain.usecase.GetOrCreatePuzzle
import com.tm.meow.sudoku.puzzle.domain.usecase.LevelGenerator
import com.tm.meow.sudoku.puzzle.domain.usecase.MarkLevelPlayed
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveSettings
import com.tm.meow.sudoku.puzzle.domain.usecase.PlaceCat
import com.tm.meow.sudoku.puzzle.domain.usecase.PrefetchLevel
import com.tm.meow.sudoku.puzzle.domain.usecase.RecordPlayedToday
import com.tm.meow.sudoku.puzzle.domain.usecase.ResolveStartLevel
import com.tm.meow.sudoku.puzzle.domain.usecase.RestoreGame
import com.tm.meow.sudoku.puzzle.domain.usecase.ReviveGame
import com.tm.meow.sudoku.puzzle.domain.usecase.SaveGame
import com.tm.meow.sudoku.puzzle.domain.usecase.SeedStarterBoosters
import com.tm.meow.sudoku.puzzle.domain.usecase.StartLevel
import com.tm.meow.sudoku.puzzle.domain.usecase.ToggleX
import com.tm.meow.sudoku.puzzle.domain.usecase.Undo
import com.tm.meow.sudoku.puzzle.domain.usecase.UpdateSettings
import com.tm.meow.sudoku.puzzle.domain.usecase.UseCatBooster
import com.tm.meow.sudoku.puzzle.domain.usecase.UseHint
import com.tm.meow.sudoku.puzzle.domain.usecase.UseMouseBooster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsRepository(initial: Settings = Settings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<Settings> = state

    override suspend fun update(transform: (Settings) -> Settings) {
        state.value = transform(state.value)
    }
}

class FakeAppFlagsRepository(initial: AppFlags = AppFlags()) : AppFlagsRepository {
    private val state = MutableStateFlow(initial)
    override val flags: Flow<AppFlags> = state

    override suspend fun update(transform: (AppFlags) -> AppFlags) {
        state.value = transform(state.value)
    }

    override suspend fun setInstallDateIfAbsent(epochDay: Long) {
        if (state.value.installDateEpochDay == null) {
            state.value = state.value.copy(installDateEpochDay = epochDay)
        }
    }
}

/** Generator that always fails and records the levels it was asked for. */
class RecordingGenerator(private val succeedWith: ((Int) -> Puzzle)? = null) : LevelGenerator {
    val levels = ArrayList<Int>()

    override fun generate(level: Int, seed: Long): GenerationResult {
        levels.add(level)
        val make = succeedWith ?: return GenerationResult.Failure("test: no generation", 1, 0L)
        return GenerationResult.Success(make(level), GenStats(1, 0, 0L, 0, 0, false))
    }
}

/** Daily generator that records (spec, seed) calls; serves the 4x4 fixture under the spec's (negative) key. */
class RecordingDailyGenerator(private val succeed: Boolean = true) : DailyGenerator {
    val specs = ArrayList<LevelSpec>()
    val seeds = ArrayList<Long>()

    override fun generate(spec: LevelSpec, seed: Long): GenerationResult {
        specs.add(spec)
        seeds.add(seed)
        if (!succeed) return GenerationResult.Failure("test: no daily generation", 1, 0L)
        return GenerationResult.Success(fixturePuzzle(spec.levelIndex), GenStats(1, 0, 0L, 0, 0, false))
    }
}

/** 4x4 naked-single fixture re-labelled to [level]. Solution cols per row: 1,3,0,2. */
fun fixturePuzzle(level: Int): Puzzle {
    val base = GameFixtures.unique4()
    return GameFixtures.puzzle(4, base.regions, base.solution, level = level)
}

/** Real engines + use cases over in-memory fakes. Build the ViewModel AFTER Dispatchers.setMain. */
class GameRig(
    val generator: RecordingGenerator = RecordingGenerator(),
    val dailyGenerator: RecordingDailyGenerator = RecordingDailyGenerator(),
    seedLevel1: Boolean = true,
    settings: Settings = Settings(),
    /** false (default) = no starter cat, so legacy board tests keep an empty start board. */
    starterCat: Boolean = false,
) {
    private val picker: StarterCatPicker =
        if (starterCat) StarterCatPicker() else object : StarterCatPicker() {
            override fun pick(puzzle: Puzzle): Pos? = null
        }

    val puzzles = FakePuzzleRepository().apply { if (seedLevel1) rows[1] = fixturePuzzle(1) }
    val saved = FakeSavedGameRepository()
    val progress = FakeProgressRepository()
    val streak = FakeStreakRepository()
    val inventory = FakeInventoryRepository()
    val flags = FakeAppFlagsRepository()
    val settingsRepo = FakeSettingsRepository(settings)
    val time = FakeTimeProvider(now = 1_000L, today = 20_000L)
    val analytics = RecordingAnalyticsLogger()

    /** [daily] = launch in Daily Challenge mode (nav arg `daily=true`). */
    fun viewModel(daily: Boolean = false): GameViewModel {
        val engine = GameEngine()
        val catBooster = CatBoosterEngine()
        val getOrCreate = GetOrCreatePuzzle(puzzles, StartLevel(generator), dispatcher = Dispatchers.Unconfined)
        return GameViewModel(
            resolveStartLevel = ResolveStartLevel(saved, flags, progress),
            getOrCreatePuzzle = getOrCreate,
            restoreGame = RestoreGame(saved, puzzles),
            saveGame = SaveGame(saved, time),
            completeLevel = CompleteLevel(progress, streak, saved, time),
            computeScore = ComputeScore(ScoreCalculator()),
            analyseBoard = AnalyseBoard(),
            toggleX = ToggleX(engine),
            placeCat = PlaceCat(engine),
            applyDragX = ApplyDragX(engine),
            undo = Undo(engine),
            useHint = UseHint(HintEngine(catBooster)),
            useCatBooster = UseCatBooster(catBooster, engine),
            useMouseBooster = UseMouseBooster(MouseBoosterEngine()) { 42L },
            applyStarterCat = ApplyStarterCat(picker, engine, Dispatchers.Unconfined),
            recordPlayedToday = RecordPlayedToday(streak, time),
            markLevelPlayed = MarkLevelPlayed(flags),
            observeSettings = ObserveSettings(settingsRepo),
            updateSettings = UpdateSettings(settingsRepo),
            getBoosterCharges = GetBoosterCharges(inventory),
            consumeBooster = ConsumeBooster(inventory),
            seedStarterBoosters = SeedStarterBoosters(flags, inventory, time),
            prefetchLevel = PrefetchLevel(getOrCreate),
            time = time,
            resolveDaily = ResolveDaily(puzzles, dailyGenerator, time, dispatcher = Dispatchers.Unconfined),
            reviveGame = ReviveGame(),
            savedStateHandle = SavedStateHandle(mapOf(GameViewModel.ARG_DAILY to daily)),
            analytics = analytics,
        )
    }
}
