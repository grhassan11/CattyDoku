package com.tm.meow.sudoku.puzzle.di

import com.tm.meow.sudoku.puzzle.data.network.NetworkConnectivityObserver
import com.tm.meow.sudoku.puzzle.domain.network.ConnectivityObserver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {
    @Binds abstract fun bindConnectivity(impl: NetworkConnectivityObserver): ConnectivityObserver
}
