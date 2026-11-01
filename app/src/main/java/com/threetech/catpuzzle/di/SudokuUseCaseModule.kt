package com.threetech.catpuzzle.di

import com.threetech.catpuzzle.domain.ads.AdPolicyConfig
import com.threetech.catpuzzle.domain.ads.AdSessionTracker
import com.threetech.catpuzzle.domain.sudoku.repository.SudokuRepository
import com.threetech.catpuzzle.domain.sudoku.usecase.CompleteSudoku
import com.threetech.catpuzzle.domain.sudoku.usecase.GetActiveSudokuElapsed
import com.threetech.catpuzzle.domain.sudoku.usecase.ObserveSudokuProgress
import com.threetech.catpuzzle.domain.sudoku.usecase.ResetActiveSudokuGame
import com.threetech.catpuzzle.domain.sudoku.usecase.SaveSudokuGame
import com.threetech.catpuzzle.domain.sudoku.usecase.ShowSudokuInterstitialIfAllowed
import com.threetech.catpuzzle.domain.sudoku.usecase.StartOrResumeSudoku
import com.threetech.catpuzzle.domain.sudoku.usecase.UseSudokuHint
import com.threetech.catpuzzle.domain.usecase.ConsumeBooster
import com.threetech.catpuzzle.domain.usecase.RecordLevelWon
import com.threetech.catpuzzle.domain.usecase.RecordPlayedToday
import com.threetech.catpuzzle.domain.usecase.ShowInterstitialIfAllowed
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Sudoku use cases (annotation-free domain classes, built here). The game engine is a plain object
 * (SudokuGameEngine) and needs no binding. Reused as-is: ConsumeBooster, RecordPlayedToday, RecordLevelWon,
 * ShowInterstitialIfAllowed (all provided by the Queens modules).
 */
@Module
@InstallIn(SingletonComponent::class)
object SudokuUseCaseModule {
    @Provides
    fun provideStartOrResumeSudoku(
        repo: SudokuRepository,
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ): StartOrResumeSudoku = StartOrResumeSudoku(repo = repo, dispatcher = dispatcher)

    @Provides
    fun provideSaveSudokuGame(repo: SudokuRepository): SaveSudokuGame = SaveSudokuGame(repo)

    @Provides
    fun provideResetActiveSudokuGame(repo: SudokuRepository): ResetActiveSudokuGame = ResetActiveSudokuGame(repo)

    @Provides
    fun provideUseSudokuHint(consume: ConsumeBooster): UseSudokuHint = UseSudokuHint(consume)

    @Provides
    fun provideCompleteSudoku(
        repo: SudokuRepository,
        recordPlayedToday: RecordPlayedToday,
        recordWon: RecordLevelWon,
    ): CompleteSudoku = CompleteSudoku(repo, recordPlayedToday, recordWon)

    @Provides
    fun provideGetActiveSudokuElapsed(repo: SudokuRepository): GetActiveSudokuElapsed = GetActiveSudokuElapsed(repo)

    @Provides
    fun provideObserveSudokuProgress(repo: SudokuRepository): ObserveSudokuProgress = ObserveSudokuProgress(repo)

    @Provides
    fun provideShowSudokuInterstitialIfAllowed(
        show: ShowInterstitialIfAllowed,
        tracker: AdSessionTracker,
        config: AdPolicyConfig,
    ): ShowSudokuInterstitialIfAllowed = ShowSudokuInterstitialIfAllowed(show, tracker, config)
}
