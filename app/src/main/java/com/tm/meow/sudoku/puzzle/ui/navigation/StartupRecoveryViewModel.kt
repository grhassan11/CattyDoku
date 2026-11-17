package com.tm.meow.sudoku.puzzle.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tm.meow.sudoku.puzzle.domain.usecase.EnsureConsentAndAdsInit
import com.tm.meow.sudoku.puzzle.domain.usecase.RecoverAfterReconnect
import com.tm.meow.sudoku.puzzle.domain.usecase.SetAppOpenScreenSuppressed
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    private val setAppOpenScreenSuppressed: SetAppOpenScreenSuppressed,
    private val recoverAfterReconnect: RecoverAfterReconnect,
) : ViewModel() {

    private var job: Job? = null

    /** Settings, About and Shop (Play Billing sheet runs over it) block app-open. [route] null / other = allowed. Pass null on dispose to clear. */
    fun onDestinationChanged(route: String?) {
        setAppOpenScreenSuppressed(isAppOpenSuppressedRoute(route))
    }

    companion object {
        /** Route patterns (as in NavDestination.route) where App Open must not show on foreground. */
        fun isAppOpenSuppressedRoute(route: String?): Boolean =
            route == Routes.SETTINGS || route == Routes.ABOUT || route == Routes.SHOP_PATTERN || route == Routes.SHOP
    }

    /**
     * [currentRoute] = route pattern of the top back stack entry when the Activity resumed (null = not known yet, ignored).
     * Every resume off the splash re-enters the idempotent use case: first time runs startup, later times only retry a
     * failed ads init (no UMP form). Overlapping calls are dropped.
     */
    fun onResumed(currentRoute: String?) {
        if (currentRoute == null || currentRoute == Routes.SPLASH || job?.isActive == true) return
        job = viewModelScope.launch {
            try {
                ensureConsentAndAdsInit()
                // Online again but a pass was missed (no foreground Activity for UMP while backgrounded, retries used up): redo it now.
                if (recoverAfterReconnect.needsRecovery()) recoverAfterReconnect.run()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // fail open: ads stay off
            }
        }
    }
}
