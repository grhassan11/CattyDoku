package com.threetech.catpuzzle.di

import com.threetech.catpuzzle.domain.engine.CatBoosterEngine
import com.threetech.catpuzzle.domain.engine.MouseBoosterEngine
import com.threetech.catpuzzle.domain.engine.GameEngine
import com.threetech.catpuzzle.domain.engine.HintEngine
import com.threetech.catpuzzle.domain.engine.ScoreCalculator
import com.threetech.catpuzzle.domain.engine.StarterCatPicker
import com.threetech.catpuzzle.domain.tutorial.TutorialEngine
import com.threetech.catpuzzle.domain.usecase.ApplyStarterCat
import com.threetech.catpuzzle.domain.repository.AppFlagsRepository
import com.threetech.catpuzzle.domain.repository.InventoryRepository
import com.threetech.catpuzzle.domain.repository.ProgressRepository
import com.threetech.catpuzzle.domain.repository.PuzzleRepository
import com.threetech.catpuzzle.domain.repository.SavedGameRepository
import com.threetech.catpuzzle.domain.repository.SettingsRepository
import com.threetech.catpuzzle.domain.config.RemoteConfigRepository
import com.threetech.catpuzzle.domain.repository.StreakRepository
import com.threetech.catpuzzle.domain.repository.StreakRewardRepository
import com.threetech.catpuzzle.domain.time.TimeProvider
import com.threetech.catpuzzle.domain.usecase.AnalyseBoard
import com.threetech.catpuzzle.domain.usecase.ApplyDragX
import com.threetech.catpuzzle.domain.usecase.CompleteLevel
import com.threetech.catpuzzle.domain.usecase.CompleteTutorial
import com.threetech.catpuzzle.domain.usecase.ComputeScore
import com.threetech.catpuzzle.domain.usecase.ConsumeBooster
import com.threetech.catpuzzle.domain.usecase.DailyGenerator
import com.threetech.catpuzzle.domain.usecase.EngineDailyGenerator
import com.threetech.catpuzzle.domain.usecase.EnginePuzzleGenerator
import com.threetech.catpuzzle.domain.usecase.ObserveCompletedDays
import com.threetech.catpuzzle.domain.usecase.ObserveDailyStatus
import com.threetech.catpuzzle.domain.usecase.ObserveStreakCalendar
import com.threetech.catpuzzle.domain.usecase.ObserveLevelDetail
import com.threetech.catpuzzle.domain.usecase.ObserveWorldMap
import com.threetech.catpuzzle.domain.usecase.ResolveDaily
import com.threetech.catpuzzle.domain.usecase.FallbackPuzzleProvider
import com.threetech.catpuzzle.domain.usecase.GetBoosterCharges
import com.threetech.catpuzzle.domain.usecase.GetOrCreatePuzzle
import com.threetech.catpuzzle.domain.usecase.LevelGenerator
import com.threetech.catpuzzle.domain.usecase.MarkLevelPlayed
import com.threetech.catpuzzle.domain.usecase.ClaimStreakReward
import com.threetech.catpuzzle.domain.usecase.ObserveStreak
import com.threetech.catpuzzle.domain.usecase.ObserveStreakRewards
import com.threetech.catpuzzle.domain.usecase.ObserveTutorialDone
import com.threetech.catpuzzle.domain.usecase.AcceptTerms
import com.threetech.catpuzzle.domain.usecase.MarkNotificationPromptAsked
import com.threetech.catpuzzle.domain.usecase.ObserveTermsAccepted
import com.threetech.catpuzzle.domain.usecase.ReadStartInputs
import com.threetech.catpuzzle.domain.startup.SdkLevelProvider
import com.threetech.catpuzzle.domain.usecase.ObserveSettings
import com.threetech.catpuzzle.domain.usecase.PlaceCat
import com.threetech.catpuzzle.domain.usecase.PrefetchLevel
import com.threetech.catpuzzle.domain.usecase.RecordPlayedToday
import com.threetech.catpuzzle.domain.usecase.ResolveStartLevel
import com.threetech.catpuzzle.domain.usecase.RestoreGame
import com.threetech.catpuzzle.domain.usecase.SaveGame
import com.threetech.catpuzzle.domain.usecase.SeedStarterBoosters
import com.threetech.catpuzzle.domain.usecase.StartLevel
import com.threetech.catpuzzle.domain.usecase.ToggleX
import com.threetech.catpuzzle.domain.usecase.Undo
import com.threetech.catpuzzle.domain.usecase.UseCatBooster
import com.threetech.catpuzzle.domain.usecase.UpdateSettings
import com.threetech.catpuzzle.domain.usecase.UseHint
import com.threetech.catpuzzle.domain.usecase.UseMouseBooster
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
    fun provideObserveTutorialDone(flags: AppFlagsRepository): ObserveTutorialDone = ObserveTutorialDone(flags)

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
