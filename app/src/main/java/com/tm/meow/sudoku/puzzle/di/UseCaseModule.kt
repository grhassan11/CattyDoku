package com.tm.meow.sudoku.puzzle.di

import com.tm.meow.sudoku.puzzle.domain.engine.CatBoosterEngine
import com.tm.meow.sudoku.puzzle.domain.engine.MouseBoosterEngine
import com.tm.meow.sudoku.puzzle.domain.engine.GameEngine
import com.tm.meow.sudoku.puzzle.domain.engine.HintEngine
import com.tm.meow.sudoku.puzzle.domain.engine.ScoreCalculator
import com.tm.meow.sudoku.puzzle.domain.engine.StarterCatPicker
import com.tm.meow.sudoku.puzzle.domain.tutorial.TutorialEngine
import com.tm.meow.sudoku.puzzle.domain.usecase.ApplyStarterCat
import com.tm.meow.sudoku.puzzle.domain.repository.AppFlagsRepository
import com.tm.meow.sudoku.puzzle.domain.repository.InventoryRepository
import com.tm.meow.sudoku.puzzle.domain.repository.ProgressRepository
import com.tm.meow.sudoku.puzzle.domain.repository.PuzzleRepository
import com.tm.meow.sudoku.puzzle.domain.repository.SavedGameRepository
import com.tm.meow.sudoku.puzzle.domain.repository.SettingsRepository
import com.tm.meow.sudoku.puzzle.domain.config.RemoteConfigRepository
import com.tm.meow.sudoku.puzzle.domain.repository.StreakRepository
import com.tm.meow.sudoku.puzzle.domain.repository.StreakRewardRepository
import com.tm.meow.sudoku.puzzle.domain.time.TimeProvider
import com.tm.meow.sudoku.puzzle.domain.usecase.AnalyseBoard
import com.tm.meow.sudoku.puzzle.domain.usecase.ApplyDragX
import com.tm.meow.sudoku.puzzle.domain.usecase.CompleteLevel
import com.tm.meow.sudoku.puzzle.domain.usecase.CompleteTutorial
import com.tm.meow.sudoku.puzzle.domain.usecase.ComputeScore
import com.tm.meow.sudoku.puzzle.domain.usecase.ConsumeBooster
import com.tm.meow.sudoku.puzzle.domain.usecase.DailyGenerator
import com.tm.meow.sudoku.puzzle.domain.usecase.EngineDailyGenerator
import com.tm.meow.sudoku.puzzle.domain.usecase.EnginePuzzleGenerator
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveCompletedDays
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveDailyStatus
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveStreakCalendar
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveLevelDetail
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveWorldMap
import com.tm.meow.sudoku.puzzle.domain.usecase.ResolveDaily
import com.tm.meow.sudoku.puzzle.domain.usecase.FallbackPuzzleProvider
import com.tm.meow.sudoku.puzzle.domain.usecase.GetBoosterCharges
import com.tm.meow.sudoku.puzzle.domain.usecase.GetOrCreatePuzzle
import com.tm.meow.sudoku.puzzle.domain.usecase.LevelGenerator
import com.tm.meow.sudoku.puzzle.domain.usecase.MarkLevelPlayed
import com.tm.meow.sudoku.puzzle.domain.usecase.ClaimStreakReward
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveStreak
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveStreakRewards
import com.tm.meow.sudoku.puzzle.domain.usecase.AcceptTerms
import com.tm.meow.sudoku.puzzle.domain.usecase.MarkNotificationPromptAsked
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveTermsAccepted
import com.tm.meow.sudoku.puzzle.domain.usecase.ReadStartInputs
import com.tm.meow.sudoku.puzzle.domain.startup.SdkLevelProvider
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveSettings
import com.tm.meow.sudoku.puzzle.domain.usecase.PlaceCat
import com.tm.meow.sudoku.puzzle.domain.usecase.PrefetchLevel
import com.tm.meow.sudoku.puzzle.domain.usecase.RecordPlayedToday
import com.tm.meow.sudoku.puzzle.domain.usecase.ResolveStartLevel
import com.tm.meow.sudoku.puzzle.domain.usecase.RestoreGame
import com.tm.meow.sudoku.puzzle.domain.usecase.SaveGame
import com.tm.meow.sudoku.puzzle.domain.usecase.SeedStarterBoosters
import com.tm.meow.sudoku.puzzle.domain.usecase.StartLevel
import com.tm.meow.sudoku.puzzle.domain.usecase.ToggleX
import com.tm.meow.sudoku.puzzle.domain.usecase.Undo
import com.tm.meow.sudoku.puzzle.domain.usecase.UseCatBooster
import com.tm.meow.sudoku.puzzle.domain.usecase.UpdateSettings
import com.tm.meow.sudoku.puzzle.domain.usecase.UseHint
import com.tm.meow.sudoku.puzzle.domain.usecase.UseMouseBooster
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

/**
 * Binds the annotation-free domain layer (engines + use cases) into Hilt.
 * Engines are stateless, so one @Singleton instance of each is shared.
 */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    // ---- engines ----

    @Provides @Singleton
    fun provideGameEngine(): GameEngine = GameEngine()

    @Provides @Singleton
    fun provideCatBoosterEngine(): CatBoosterEngine = CatBoosterEngine()

    @Provides @Singleton
    fun provideMouseBoosterEngine(): MouseBoosterEngine = MouseBoosterEngine()

    @Provides @Singleton
    fun provideHintEngine(catBooster: CatBoosterEngine): HintEngine = HintEngine(catBooster)

    @Provides @Singleton
    fun provideScoreCalculator(): ScoreCalculator = ScoreCalculator()

    // ---- puzzle source ----

    @Provides @Singleton
    fun provideLevelGenerator(): LevelGenerator = EnginePuzzleGenerator()

    /** Bundled fallback puzzles are a pending decision: NONE until then. */
    @Provides @Singleton
    fun provideFallbackPuzzleProvider(): FallbackPuzzleProvider = FallbackPuzzleProvider.NONE

    @Provides @Singleton
    fun provideStartLevel(generator: LevelGenerator, fallback: FallbackPuzzleProvider): StartLevel =
        StartLevel(generator, fallback)

    @Provides @Singleton
    fun provideGetOrCreatePuzzle(
        cache: PuzzleRepository,
        startLevel: StartLevel,
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ): GetOrCreatePuzzle = GetOrCreatePuzzle(
        cache = cache,
        startLevel = startLevel,
        baseSeed = GetOrCreatePuzzle.DEFAULT_BASE_SEED,
        dispatcher = dispatcher,
    )

    @Provides @Singleton
    fun providePrefetchLevel(getOrCreate: GetOrCreatePuzzle): PrefetchLevel = PrefetchLevel(getOrCreate)

    // ---- board / booster use cases ----

    @Provides fun provideToggleX(engine: GameEngine): ToggleX = ToggleX(engine)

    @Provides fun providePlaceCat(engine: GameEngine): PlaceCat = PlaceCat(engine)

    @Provides fun provideApplyDragX(engine: GameEngine): ApplyDragX = ApplyDragX(engine)

    @Provides fun provideUndo(engine: GameEngine): Undo = Undo(engine)

    @Provides fun provideUseHint(hintEngine: HintEngine): UseHint = UseHint(hintEngine)

    @Provides
    fun provideUseCatBooster(catBooster: CatBoosterEngine, engine: GameEngine): UseCatBooster =
        UseCatBooster(catBooster, engine)

    /** Fresh seed per use (random X spots differ every tap); tests inject a fixed one. */
    @Provides
    fun provideUseMouseBooster(mouseBooster: MouseBoosterEngine): UseMouseBooster =
        UseMouseBooster(mouseBooster, seedSource = { System.nanoTime() })

    @Provides @Singleton
    fun provideStarterCatPicker(): StarterCatPicker = StarterCatPicker()

    @Provides
    fun provideApplyStarterCat(
        picker: StarterCatPicker,
        engine: GameEngine,
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ): ApplyStarterCat = ApplyStarterCat(picker, engine, dispatcher)

    @Provides fun provideComputeScore(calculator: ScoreCalculator): ComputeScore = ComputeScore(calculator)

    @Provides fun provideAnalyseBoard(): AnalyseBoard = AnalyseBoard()

    // ---- persistence use cases ----

    @Provides
    fun provideSaveGame(repo: SavedGameRepository, time: TimeProvider): SaveGame = SaveGame(repo, time)

    @Provides
    fun provideRestoreGame(saved: SavedGameRepository, puzzles: PuzzleRepository): RestoreGame =
        RestoreGame(saved, puzzles)

    @Provides
    fun provideCompleteLevel(
        progress: ProgressRepository,
        streak: StreakRepository,
        savedGames: SavedGameRepository,
        time: TimeProvider,
    ): CompleteLevel = CompleteLevel(progress, streak, savedGames, time)

    @Provides
    fun provideRecordPlayedToday(repo: StreakRepository, time: TimeProvider): RecordPlayedToday =
        RecordPlayedToday(repo, time)

    @Provides
    fun provideObserveStreak(repo: StreakRepository, time: TimeProvider): ObserveStreak =
        ObserveStreak(repo, time)

    // ---- streak milestone rewards ----

    @Provides
    fun provideObserveStreakRewards(
        streak: StreakRepository,
        rewards: StreakRewardRepository,
        flags: AppFlagsRepository,
        time: TimeProvider,
        config: RemoteConfigRepository,
    ): ObserveStreakRewards = ObserveStreakRewards(streak, rewards, flags, time, config)

    @Provides
    fun provideClaimStreakReward(
        streak: StreakRepository,
        rewards: StreakRewardRepository,
        flags: AppFlagsRepository,
        time: TimeProvider,
        config: RemoteConfigRepository,
    ): ClaimStreakReward = ClaimStreakReward(streak, rewards, flags, time, config)

    // ---- session use cases ----

    @Provides
    fun provideResolveStartLevel(
        savedGames: SavedGameRepository,
        flags: AppFlagsRepository,
        progress: ProgressRepository,
    ): ResolveStartLevel = ResolveStartLevel(savedGames, flags, progress)

    @Provides fun provideMarkLevelPlayed(flags: AppFlagsRepository): MarkLevelPlayed = MarkLevelPlayed(flags)

    @Provides fun provideObserveSettings(repo: SettingsRepository): ObserveSettings = ObserveSettings(repo)

    @Provides fun provideUpdateSettings(repo: SettingsRepository): UpdateSettings = UpdateSettings(repo)

    @Provides
    fun provideGetBoosterCharges(inventory: InventoryRepository): GetBoosterCharges =
        GetBoosterCharges(inventory)

    @Provides
    fun provideConsumeBooster(inventory: InventoryRepository): ConsumeBooster = ConsumeBooster(inventory)

    @Provides
    fun provideSeedStarterBoosters(
        flags: AppFlagsRepository,
        inventory: InventoryRepository,
        time: TimeProvider,
    ): SeedStarterBoosters = SeedStarterBoosters(flags, inventory, time)

    // ---- daily challenge + streak calendar (phase 7) ----

    @Provides @Singleton
    fun provideDailyGenerator(): DailyGenerator = EngineDailyGenerator()

    @Provides
    fun provideResolveDaily(
        cache: PuzzleRepository,
        generator: DailyGenerator,
        time: TimeProvider,
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ): ResolveDaily = ResolveDaily(
        cache = cache,
        generator = generator,
        time = time,
        baseSeed = GetOrCreatePuzzle.DEFAULT_BASE_SEED,
        dispatcher = dispatcher,
    )

    @Provides
    fun provideObserveCompletedDays(progress: ProgressRepository): ObserveCompletedDays =
        ObserveCompletedDays(progress)

    @Provides
    fun provideObserveWorldMap(progress: ProgressRepository): ObserveWorldMap = ObserveWorldMap(progress)

    @Provides
    fun provideObserveLevelDetail(progress: ProgressRepository): ObserveLevelDetail = ObserveLevelDetail(progress)

    @Provides
    fun provideObserveDailyStatus(
        progress: ProgressRepository,
        completed: ObserveCompletedDays,
        time: TimeProvider,
    ): ObserveDailyStatus = ObserveDailyStatus(progress, completed, time)

    @Provides
    fun provideObserveStreakCalendar(
        streak: StreakRepository,
        completed: ObserveCompletedDays,
        time: TimeProvider,
    ): ObserveStreakCalendar = ObserveStreakCalendar(streak, completed, time)

    // ---- tutorial (phase 6) ----

    @Provides @Singleton
    fun provideTutorialEngine(engine: GameEngine): TutorialEngine = TutorialEngine(engine)

    @Provides
    fun provideCompleteTutorial(flags: AppFlagsRepository): CompleteTutorial = CompleteTutorial(flags)

    // ---- startup flow (phase 8) ----

    @Provides
    fun provideReadStartInputs(flags: AppFlagsRepository, sdk: SdkLevelProvider): ReadStartInputs =
        ReadStartInputs(flags, sdk)

    @Provides
    fun provideObserveTermsAccepted(flags: AppFlagsRepository): ObserveTermsAccepted = ObserveTermsAccepted(flags)

    @Provides
    fun provideAcceptTerms(flags: AppFlagsRepository): AcceptTerms = AcceptTerms(flags)

    @Provides
    fun provideMarkNotificationPromptAsked(flags: AppFlagsRepository): MarkNotificationPromptAsked =
        MarkNotificationPromptAsked(flags)
}
