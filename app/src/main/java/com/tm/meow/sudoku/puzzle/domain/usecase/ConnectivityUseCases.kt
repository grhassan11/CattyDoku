package com.tm.meow.sudoku.puzzle.domain.usecase

import com.tm.meow.sudoku.puzzle.domain.network.ConnectivityObserver
import kotlinx.coroutines.flow.StateFlow

/** Live online/offline state for ViewModels (true until the first platform callback, so nothing flickers on launch). */
class ObserveOnline(private val connectivity: ConnectivityObserver) {
    operator fun invoke(): StateFlow<Boolean> = connectivity.isOnline
}
