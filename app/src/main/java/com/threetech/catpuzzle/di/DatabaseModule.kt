package com.threetech.catpuzzle.di

import android.content.Context
import androidx.room.Room
import com.threetech.catpuzzle.data.local.MeowdokuDatabase
import com.threetech.catpuzzle.data.local.dao.DailyStreakDao
import com.threetech.catpuzzle.data.local.dao.InventoryDao
import com.threetech.catpuzzle.data.local.dao.LevelProgressDao
import com.threetech.catpuzzle.data.local.dao.PuzzleCacheDao
import com.threetech.catpuzzle.data.local.dao.PurchaseGrantDao
import com.threetech.catpuzzle.data.local.dao.SavedGameDao
import com.threetech.catpuzzle.data.local.dao.StreakRewardDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Room database + DAOs. Deliberately NO fallbackToDestructiveMigration: see the migration policy on
 * [MeowdokuDatabase].
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MeowdokuDatabase =
        Room.databaseBuilder(context, MeowdokuDatabase::class.java, MeowdokuDatabase.NAME)
            .addMigrations(*MeowdokuDatabase.ALL_MIGRATIONS)
            .build()

    @Provides
    fun provideLevelProgressDao(db: MeowdokuDatabase): LevelProgressDao = db.levelProgressDao()

    @Provides
    fun providePuzzleCacheDao(db: MeowdokuDatabase): PuzzleCacheDao = db.puzzleCacheDao()

    @Provides
    fun provideSavedGameDao(db: MeowdokuDatabase): SavedGameDao = db.savedGameDao()

    @Provides
    fun provideDailyStreakDao(db: MeowdokuDatabase): DailyStreakDao = db.dailyStreakDao()

    @Provides
    fun provideInventoryDao(db: MeowdokuDatabase): InventoryDao = db.inventoryDao()

    @Provides
    fun providePurchaseGrantDao(db: MeowdokuDatabase): PurchaseGrantDao = db.purchaseGrantDao()

    @Provides
    fun provideStreakRewardDao(db: MeowdokuDatabase): StreakRewardDao = db.streakRewardDao()
}
