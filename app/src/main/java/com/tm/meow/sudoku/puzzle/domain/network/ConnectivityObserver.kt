package com.tm.meow.sudoku.puzzle.domain.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Live "has a usable internet connection" signal. Online = a default network with INTERNET + VALIDATED capability.
 * Starts as true (until the first platform callback) so ad slots do not flicker away on launch. Impl lives in data/network.
 */
interface ConnectivityObserver {
    val isOnline: StateFlow<Boolean>
}

/** Default for callers/tests that do not care about connectivity: always online. */
object AlwaysOnline : ConnectivityObserver {
    override val isOnline: StateFlow<Boolean> = MutableStateFlow(true)
}
