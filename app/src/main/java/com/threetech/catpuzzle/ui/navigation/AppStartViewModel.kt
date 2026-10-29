package com.threetech.catpuzzle.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.threetech.catpuzzle.domain.startup.StartInputs
import com.threetech.catpuzzle.domain.startup.StartStep
import com.threetech.catpuzzle.domain.usecase.ReadStartInputs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
) : ViewModel() {

    /** Null until the flags are read. */
    val startInputs: StateFlow<StartInputs?> = flow {
        emit(readStartInputs())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
