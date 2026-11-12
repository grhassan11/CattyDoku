package com.tm.meow.sudoku.puzzle.data.ads

import com.tm.meow.sudoku.puzzle.di.MainDispatcher
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.tm.meow.sudoku.puzzle.domain.ads.AdsSdkStatus
import com.tm.meow.sudoku.puzzle.domain.usecase.PreloadAppOpenAd
import com.tm.meow.sudoku.puzzle.domain.usecase.ShowAppOpenIfAllowed
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Process-foreground adapter for resume-only app-open ads. Registered once from the Application.
 *
 * ProcessLifecycleOwner is process-wide: hopping between our Activity and the Ads SDK / UMP / permission screens in the
 * same process never produces a stop/start pair, so only a real background -> foreground return reaches [onStart]. The
 * first ON_START of the process is the cold start and never shows.
 * All show rules live in the pure domain policy ([ShowAppOpenIfAllowed]).
 */
@Singleton
class AppOpenLifecycleObserver @Inject constructor(
    private val showAppOpen: ShowAppOpenIfAllowed,
    private val preloadAppOpen: PreloadAppOpenAd,
    private val sdk: AdsSdkStatus,
    @MainDispatcher main: CoroutineDispatcher,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + main)

    private var coldStartPending = true

    /** Call once from `Application.onCreate` (main thread). */
    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        // First preload as soon as consent + SDK init are done (start flow), never before.
        scope.launch {
            sdk.isInitialized.first { it }
            preloadAppOpen()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        val cold = coldStartPending
        coldStartPending = false
        scope.launch { showAppOpen(coldStart = cold) }
    }
}
