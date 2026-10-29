package com.threetech.catpuzzle.data.ads

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.threetech.catpuzzle.domain.ads.AdSessionTracker
import com.threetech.catpuzzle.domain.ads.AdsSdkStatus
import com.threetech.catpuzzle.domain.usecase.PreloadAppOpenAd
import com.threetech.catpuzzle.domain.usecase.ShowAppOpenIfAllowed
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Process-foreground adapter for resume-only app-open ads. Registered once from the Application.
 *
 * ProcessLifecycleOwner is process-wide: hopping between our Activity and the Ads SDK / UMP / permission screens in the
 * same process never produces a stop/start pair, so only a real background -> foreground return reaches [onStart]. The
 * first ON_START of the process is the cold start and never shows. A tap on an ad can leave the app (browser / store):
 * [AdSessionTracker.consumeAdClicked] at stop makes the matching foreground a "return from ad", also skipped.
 * All show rules live in the pure domain policy ([ShowAppOpenIfAllowed]).
 */
@Singleton
class AppOpenLifecycleObserver @Inject constructor(
    private val showAppOpen: ShowAppOpenIfAllowed,
    private val preloadAppOpen: PreloadAppOpenAd,
    private val sdk: AdsSdkStatus,
    private val tracker: AdSessionTracker,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var coldStartPending = true
    private var backgroundedAtMs: Long? = null
    private var leftViaAdClick = false

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
        val fromAd = leftViaAdClick
        leftViaAdClick = false
        val backgroundedAt = backgroundedAtMs
        scope.launch {
            showAppOpen(coldStart = cold, backgroundedAtMs = backgroundedAt, returnedFromAd = fromAd)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAtMs = tracker.now()
        leftViaAdClick = tracker.consumeAdClicked()
    }
}
