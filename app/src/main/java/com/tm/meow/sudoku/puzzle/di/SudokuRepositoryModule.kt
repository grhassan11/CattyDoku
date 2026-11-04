package com.tm.meow.sudoku.puzzle.di

import com.tm.meow.sudoku.puzzle.data.repository.SudokuRepositoryImpl
import com.tm.meow.sudoku.puzzle.domain.sudoku.repository.SudokuRepository
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
