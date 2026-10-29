package com.threetech.catpuzzle.di

import com.threetech.catpuzzle.domain.ads.AdSessionTracker
import com.threetech.catpuzzle.domain.repository.AppFlagsRepository
import com.threetech.catpuzzle.domain.repository.ProgressRepository
import com.threetech.catpuzzle.domain.usecase.DismissRateUs
import com.threetech.catpuzzle.domain.usecase.SubmitRateUs
import com.threetech.catpuzzle.domain.usecase.TryShowRateUs
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
