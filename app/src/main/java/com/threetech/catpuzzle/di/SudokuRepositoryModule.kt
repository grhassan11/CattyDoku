package com.threetech.catpuzzle.di

import com.threetech.catpuzzle.data.repository.SudokuRepositoryImpl
import com.threetech.catpuzzle.domain.sudoku.repository.SudokuRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Sudoku interface -> impl binding. The impl is @Singleton itself. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SudokuRepositoryModule {
    @Binds abstract fun bindSudokuRepository(impl: SudokuRepositoryImpl): SudokuRepository
}
