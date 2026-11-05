package com.tm.meow.sudoku.puzzle.di

import com.tm.meow.sudoku.puzzle.domain.ads.AdSessionTracker
import com.tm.meow.sudoku.puzzle.domain.repository.AppFlagsRepository
import com.tm.meow.sudoku.puzzle.domain.sudoku.repository.SudokuRepository
import com.tm.meow.sudoku.puzzle.domain.sudoku.usecase.CompleteSudoku
import com.tm.meow.sudoku.puzzle.domain.sudoku.usecase.CompleteSudokuTutorial
import com.tm.meow.sudoku.puzzle.domain.sudoku.usecase.GetActiveSudokuElapsed
import com.tm.meow.sudoku.puzzle.domain.sudoku.usecase.IsSudokuTutorialDone
import com.tm.meow.sudoku.puzzle.domain.sudoku.usecase.ObserveSudokuProgress
import com.tm.meow.sudoku.puzzle.domain.sudoku.usecase.ResetActiveSudokuGame
import com.tm.meow.sudoku.puzzle.domain.sudoku.usecase.SaveOrDiscardSudokuGame
import com.tm.meow.sudoku.puzzle.domain.sudoku.usecase.SaveSudokuGame
import com.tm.meow.sudoku.puzzle.domain.sudoku.usecase.ShowSudokuInterstitialIfAllowed
import com.tm.meow.sudoku.puzzle.domain.sudoku.usecase.StartOrResumeSudoku
import com.tm.meow.sudoku.puzzle.domain.sudoku.usecase.UseSudokuHint
import com.tm.meow.sudoku.puzzle.domain.usecase.ConsumeBooster
import com.tm.meow.sudoku.puzzle.domain.usecase.RecordPlayedToday
import com.tm.meow.sudoku.puzzle.domain.usecase.RecordSudokuWon
import com.tm.meow.sudoku.puzzle.domain.usecase.ShowInterstitialIfAllowed
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Sudoku use cases (annotation-free domain classes, built here). The game engine is a plain object
 * (SudokuGameEngine) and needs no binding. Reused as-is: ConsumeBooster, RecordPlayedToday,
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
    fun provideSaveOrDiscardSudokuGame(repo: SudokuRepository): SaveOrDiscardSudokuGame = SaveOrDiscardSudokuGame(repo)

    @Provides
    fun provideResetActiveSudokuGame(repo: SudokuRepository): ResetActiveSudokuGame = ResetActiveSudokuGame(repo)

    @Provides
    fun provideUseSudokuHint(consume: ConsumeBooster): UseSudokuHint = UseSudokuHint(consume)

    @Provides
    fun provideCompleteSudoku(
        repo: SudokuRepository,
        recordPlayedToday: RecordPlayedToday,
        recordWon: RecordSudokuWon,
    ): CompleteSudoku = CompleteSudoku(repo, recordPlayedToday, recordWon)

    @Provides
    fun provideGetActiveSudokuElapsed(repo: SudokuRepository): GetActiveSudokuElapsed = GetActiveSudokuElapsed(repo)

    @Provides
    fun provideObserveSudokuProgress(repo: SudokuRepository): ObserveSudokuProgress = ObserveSudokuProgress(repo)

    @Provides
    fun provideIsSudokuTutorialDone(flags: AppFlagsRepository): IsSudokuTutorialDone = IsSudokuTutorialDone(flags)

    @Provides
    fun provideCompleteSudokuTutorial(flags: AppFlagsRepository): CompleteSudokuTutorial = CompleteSudokuTutorial(flags)

    @Provides
    fun provideRecordSudokuWon(tracker: AdSessionTracker): RecordSudokuWon = RecordSudokuWon(tracker)

    @Provides
    fun provideShowSudokuInterstitialIfAllowed(
        show: ShowInterstitialIfAllowed,
    ): ShowSudokuInterstitialIfAllowed = ShowSudokuInterstitialIfAllowed(show)
}
