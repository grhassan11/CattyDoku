package com.tm.meow.sudoku.puzzle.di

import com.tm.meow.sudoku.puzzle.domain.ads.AdSessionTracker
import com.tm.meow.sudoku.puzzle.domain.repository.AppFlagsRepository
import com.tm.meow.sudoku.puzzle.domain.repository.ProgressRepository
import com.tm.meow.sudoku.puzzle.domain.usecase.DismissRateUs
import com.tm.meow.sudoku.puzzle.domain.usecase.SubmitRateUs
import com.tm.meow.sudoku.puzzle.domain.usecase.TryShowRateUs
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Rate us wiring. */
@Module
@InstallIn(SingletonComponent::class)
object RatingModule {

    @Provides
    fun provideTryShowRateUs(
        progress: ProgressRepository,
        flags: AppFlagsRepository,
        tracker: AdSessionTracker,
    ): TryShowRateUs = TryShowRateUs(progress, flags, tracker)

    @Provides
    fun provideDismissRateUs(flags: AppFlagsRepository): DismissRateUs = DismissRateUs(flags)

    @Provides
    fun provideSubmitRateUs(flags: AppFlagsRepository): SubmitRateUs = SubmitRateUs(flags)
}
