package com.threetech.catpuzzle.ui.screens.game

import com.threetech.catpuzzle.domain.engine.CatBoosterEngine
import com.threetech.catpuzzle.domain.engine.GameEngine
import com.threetech.catpuzzle.domain.engine.GameFixtures
import com.threetech.catpuzzle.domain.engine.GenStats
import com.threetech.catpuzzle.domain.engine.GenerationResult
import com.threetech.catpuzzle.domain.engine.HintEngine
import com.threetech.catpuzzle.domain.engine.MouseBoosterEngine
import com.threetech.catpuzzle.domain.engine.ScoreCalculator
import androidx.lifecycle.SavedStateHandle
import com.threetech.catpuzzle.domain.model.AppFlags
import com.threetech.catpuzzle.domain.model.LevelSpec
import com.threetech.catpuzzle.domain.engine.StarterCatPicker
import com.threetech.catpuzzle.domain.model.Pos
import com.threetech.catpuzzle.domain.model.Puzzle
import com.threetech.catpuzzle.domain.model.Settings
import com.threetech.catpuzzle.domain.usecase.ApplyStarterCat
import com.threetech.catpuzzle.domain.repository.AppFlagsRepository
import com.threetech.catpuzzle.domain.repository.SettingsRepository
import com.threetech.catpuzzle.domain.usecase.AnalyseBoard
import com.threetech.catpuzzle.domain.usecase.ApplyDragX
import com.threetech.catpuzzle.domain.usecase.CompleteLevel
import com.threetech.catpuzzle.domain.usecase.ComputeScore
import com.threetech.catpuzzle.domain.usecase.ConsumeBooster
import com.threetech.catpuzzle.domain.usecase.DailyGenerator
import com.threetech.catpuzzle.domain.usecase.ResolveDaily
import com.threetech.catpuzzle.domain.usecase.FakeInventoryRepository
import com.threetech.catpuzzle.domain.usecase.FakeProgressRepository
import com.threetech.catpuzzle.domain.usecase.FakePuzzleRepository
import com.threetech.catpuzzle.domain.usecase.FakeSavedGameRepository
import com.threetech.catpuzzle.domain.usecase.FakeStreakRepository
import com.threetech.catpuzzle.domain.usecase.FakeTimeProvider
import com.threetech.catpuzzle.domain.usecase.GetBoosterCharges
import com.threetech.catpuzzle.domain.usecase.GetOrCreatePuzzle
import com.threetech.catpuzzle.domain.usecase.LevelGenerator
import com.threetech.catpuzzle.domain.usecase.MarkLevelPlayed
import com.threetech.catpuzzle.domain.usecase.ObserveSettings
import com.threetech.catpuzzle.domain.usecase.PlaceCat
import com.threetech.catpuzzle.domain.usecase.PrefetchLevel
import com.threetech.catpuzzle.domain.usecase.RecordPlayedToday
import com.threetech.catpuzzle.domain.usecase.ResolveStartLevel
import com.threetech.catpuzzle.domain.usecase.RestoreGame
import com.threetech.catpuzzle.domain.usecase.ReviveGame
import com.threetech.catpuzzle.domain.usecase.SaveGame
import com.threetech.catpuzzle.domain.usecase.SeedStarterBoosters
import com.threetech.catpuzzle.domain.usecase.StartLevel
import com.threetech.catpuzzle.domain.usecase.ToggleX
import com.threetech.catpuzzle.domain.usecase.Undo
import com.threetech.catpuzzle.domain.usecase.UpdateSettings
import com.threetech.catpuzzle.domain.usecase.UseCatBooster
import com.threetech.catpuzzle.domain.usecase.UseHint
import com.threetech.catpuzzle.domain.usecase.UseMouseBooster
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
        )
    }
}
