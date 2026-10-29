package com.threetech.catpuzzle.di

import com.threetech.catpuzzle.data.repository.AppFlagsRepositoryImpl
import com.threetech.catpuzzle.data.repository.InventoryRepositoryImpl
import com.threetech.catpuzzle.data.repository.ProgressRepositoryImpl
import com.threetech.catpuzzle.data.repository.PuzzleRepositoryImpl
import com.threetech.catpuzzle.data.repository.SavedGameRepositoryImpl
import com.threetech.catpuzzle.data.repository.SettingsRepositoryImpl
import com.threetech.catpuzzle.data.repository.StreakRepositoryImpl
import com.threetech.catpuzzle.data.repository.StreakRewardRepositoryImpl
import com.threetech.catpuzzle.data.platform.AndroidSdkLevelProvider
import com.threetech.catpuzzle.data.notification.WorkDailyReminderScheduler
import com.threetech.catpuzzle.data.notification.WorkStreakReminderScheduler
import com.threetech.catpuzzle.data.time.SystemTimeProvider
import com.threetech.catpuzzle.domain.notification.DailyReminderScheduler
import com.threetech.catpuzzle.domain.notification.StreakReminderScheduler
import com.threetech.catpuzzle.domain.repository.AppFlagsRepository
import com.threetech.catpuzzle.domain.repository.InventoryRepository
import com.threetech.catpuzzle.domain.repository.ProgressRepository
import com.threetech.catpuzzle.domain.repository.PuzzleRepository
import com.threetech.catpuzzle.domain.repository.SavedGameRepository
import com.threetech.catpuzzle.domain.repository.SettingsRepository
import com.threetech.catpuzzle.domain.repository.StreakRepository
import com.threetech.catpuzzle.domain.repository.StreakRewardRepository
import com.threetech.catpuzzle.domain.startup.SdkLevelProvider
import com.threetech.catpuzzle.domain.time.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Interface -> impl bindings. Impls are @Singleton themselves. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindProgress(impl: ProgressRepositoryImpl): ProgressRepository

    @Binds abstract fun bindPuzzle(impl: PuzzleRepositoryImpl): PuzzleRepository

    @Binds abstract fun bindSavedGame(impl: SavedGameRepositoryImpl): SavedGameRepository

    @Binds abstract fun bindStreak(impl: StreakRepositoryImpl): StreakRepository

    @Binds abstract fun bindStreakReward(impl: StreakRewardRepositoryImpl): StreakRewardRepository

    @Binds abstract fun bindInventory(impl: InventoryRepositoryImpl): InventoryRepository

    @Binds abstract fun bindSettings(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds abstract fun bindAppFlags(impl: AppFlagsRepositoryImpl): AppFlagsRepository

    @Binds abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider

    @Binds abstract fun bindSdkLevel(impl: AndroidSdkLevelProvider): SdkLevelProvider

    @Binds abstract fun bindStreakReminderScheduler(impl: WorkStreakReminderScheduler): StreakReminderScheduler

    @Binds abstract fun bindDailyReminderScheduler(impl: WorkDailyReminderScheduler): DailyReminderScheduler
}
