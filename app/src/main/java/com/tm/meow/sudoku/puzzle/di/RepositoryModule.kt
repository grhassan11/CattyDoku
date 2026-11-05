package com.tm.meow.sudoku.puzzle.di

import com.tm.meow.sudoku.puzzle.data.repository.AppFlagsRepositoryImpl
import com.tm.meow.sudoku.puzzle.data.repository.InventoryRepositoryImpl
import com.tm.meow.sudoku.puzzle.data.repository.ProgressRepositoryImpl
import com.tm.meow.sudoku.puzzle.data.repository.PuzzleRepositoryImpl
import com.tm.meow.sudoku.puzzle.data.repository.SavedGameRepositoryImpl
import com.tm.meow.sudoku.puzzle.data.repository.SettingsRepositoryImpl
import com.tm.meow.sudoku.puzzle.data.repository.StreakRepositoryImpl
import com.tm.meow.sudoku.puzzle.data.repository.StreakRewardRepositoryImpl
import com.tm.meow.sudoku.puzzle.data.platform.AndroidSdkLevelProvider
import com.tm.meow.sudoku.puzzle.data.notification.SystemNotificationStatus
import com.tm.meow.sudoku.puzzle.data.notification.WorkDailyReminderScheduler
import com.tm.meow.sudoku.puzzle.data.notification.WorkSudokuReminderScheduler
import com.tm.meow.sudoku.puzzle.data.notification.WorkStreakReminderScheduler
import com.tm.meow.sudoku.puzzle.data.time.SystemTimeProvider
import com.tm.meow.sudoku.puzzle.domain.notification.DailyReminderScheduler
import com.tm.meow.sudoku.puzzle.domain.notification.NotificationStatus
import com.tm.meow.sudoku.puzzle.domain.notification.StreakReminderScheduler
import com.tm.meow.sudoku.puzzle.domain.notification.SudokuReminderScheduler
import com.tm.meow.sudoku.puzzle.domain.repository.AppFlagsRepository
import com.tm.meow.sudoku.puzzle.domain.repository.InventoryRepository
import com.tm.meow.sudoku.puzzle.domain.repository.ProgressRepository
import com.tm.meow.sudoku.puzzle.domain.repository.PuzzleRepository
import com.tm.meow.sudoku.puzzle.domain.repository.SavedGameRepository
import com.tm.meow.sudoku.puzzle.domain.repository.SettingsRepository
import com.tm.meow.sudoku.puzzle.domain.repository.StreakRepository
import com.tm.meow.sudoku.puzzle.domain.repository.StreakRewardRepository
import com.tm.meow.sudoku.puzzle.domain.startup.SdkLevelProvider
import com.tm.meow.sudoku.puzzle.domain.time.TimeProvider
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

    @Binds abstract fun bindNotificationStatus(impl: SystemNotificationStatus): NotificationStatus

    @Binds abstract fun bindDailyReminderScheduler(impl: WorkDailyReminderScheduler): DailyReminderScheduler
    @Binds abstract fun bindSudokuReminderScheduler(impl: WorkSudokuReminderScheduler): SudokuReminderScheduler
}
