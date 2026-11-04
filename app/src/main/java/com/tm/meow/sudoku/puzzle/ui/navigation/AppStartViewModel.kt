package com.tm.meow.sudoku.puzzle.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsEvent
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.analytics.NoopAnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.crash.CrashReporter
import com.tm.meow.sudoku.puzzle.domain.crash.NoopCrashReporter
import com.tm.meow.sudoku.puzzle.domain.startup.StartInputs
import com.tm.meow.sudoku.puzzle.domain.startup.StartStep
import com.tm.meow.sudoku.puzzle.domain.usecase.ReadStartInputs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** Route for a start step. Pure mapping, unit-tested. */
internal fun routeFor(step: StartStep): String = when (step) {
    StartStep.WELCOME -> Routes.WELCOME
    StartStep.NOTIFICATION -> Routes.NOTIFICATION
    StartStep.TUTORIAL -> Routes.TUTORIAL
    StartStep.HOME -> Routes.HOME
}

/**
 * Reads the start-flow inputs once per process (terms / notification asked / tutorial done / API level).
 * The nav graph always starts at the in-app splash; this snapshot decides where the splash hands over
 * (see StartFlow). One-shot on purpose: flags flipping later must not rebuild the nav graph.
 * MainActivity holds the system splash until [startInputs] is non-null (a few ms): no blank frame.
 */
@HiltViewModel
class AppStartViewModel @Inject constructor(
    readStartInputs: ReadStartInputs,
    private val analytics: AnalyticsLogger = NoopAnalyticsLogger,
    crash: CrashReporter = NoopCrashReporter,
) : ViewModel() {

    /** Last screen logged. Survives rotation (ViewModel), so a re-attached nav listener does not re-log the same screen. */
    private var lastScreen: String? = null

    /** Log `screen_view` for a destination change. [routePattern] = NavDestination.route. Consent gating lives in the logger. */
    fun onDestinationChanged(routePattern: String?) {
        val name = screenNameFor(routePattern) ?: return
        if (name == lastScreen) return
        lastScreen = name
        analytics.log(AnalyticsEvent.ScreenView(name))
    }

    /** Null until the flags are read. */
    val startInputs: StateFlow<StartInputs?> = flow {
        emit(readStartInputs())
    }.catch { e ->
        // DataStore read failure must not crash launch: fall back to first-launch defaults.
        if (e is CancellationException) throw e
        runCatching { crash.recordNonFatal(e, "AppStartViewModel.startInputs") }
        emit(readStartInputs.fallback())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
