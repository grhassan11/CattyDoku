package com.tm.meow.sudoku.puzzle.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tm.meow.sudoku.puzzle.domain.usecase.EnsureConsentAndAdsInit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Safety net for launches that skip the in-app splash. Process death restores the nav back stack (Home, Game, ...), the
 * splash is not on it, and the splash is the only place that runs UMP consent + ads init. When the app resumes on any
 * route other than the splash and that startup step has not run this process, run it now (see [EnsureConsentAndAdsInit],
 * idempotent). A normal cold start lands on the splash, which does the work itself. Activity-scoped: survives rotation.
 */
@HiltViewModel
class StartupRecoveryViewModel @Inject constructor(
    private val ensureConsentAndAdsInit: EnsureConsentAndAdsInit,
) : ViewModel() {

    private var requested = false

    /** [currentRoute] = route pattern of the top back stack entry when the Activity resumed (null = not known yet, ignored). */
    fun onResumed(currentRoute: String?) {
        if (requested || currentRoute == null || currentRoute == Routes.SPLASH) return
        requested = true
        viewModelScope.launch {
            try {
                ensureConsentAndAdsInit()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // fail open: ads stay off
            }
        }
    }
}
