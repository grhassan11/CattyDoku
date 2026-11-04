package com.tm.meow.sudoku.puzzle

import android.app.Application
import com.tm.meow.sudoku.puzzle.audio.AudioController
import com.tm.meow.sudoku.puzzle.data.ads.AppOpenLifecycleObserver
import com.tm.meow.sudoku.puzzle.data.billing.BillingLifecycleObserver
import com.tm.meow.sudoku.puzzle.data.notification.DailyReminderObserver
import com.tm.meow.sudoku.puzzle.data.notification.StreakReminderObserver
import com.tm.meow.sudoku.puzzle.data.platform.CurrentActivityProvider
import com.tm.meow.sudoku.puzzle.firebase.TelemetryConsentObserver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CatPuzzleApp : Application() {
    @Inject lateinit var currentActivityProvider: CurrentActivityProvider
    @Inject lateinit var appOpenObserver: AppOpenLifecycleObserver
    @Inject lateinit var billingObserver: BillingLifecycleObserver
    @Inject lateinit var telemetryConsentObserver: TelemetryConsentObserver
    @Inject lateinit var audioController: AudioController
    @Inject lateinit var streakReminderObserver: StreakReminderObserver
    @Inject lateinit var dailyReminderObserver: DailyReminderObserver

    override fun onCreate() {
        super.onCreate()
        // Hilt has injected by now (super.onCreate). Gives UMP / ads wrappers a foreground Activity.
        currentActivityProvider.register(this)
        // Resume-only app-open ads (cold start never shows). Needs the ProcessLifecycleOwner (lifecycle-process).
        appOpenObserver.start()
        // Play Billing: listen for purchase updates + sync purchases / entitlement now and on every resume.
        billingObserver.start()
        // Analytics / Crashlytics stay off until UMP consent allows them (follows every consent change).
        telemetryConsentObserver.start()
        // Background music + SFX owner: Music/Sound settings, process foreground, full-screen-ad silencing.
        audioController.start()
        // Evening streak-at-risk reminder: books / cancels the WorkManager check on start, toggle change, and every win.
        streakReminderObserver.start()
        // Daily 20:00 reminder: books on start and toggle change; the worker re-books itself every day.
        dailyReminderObserver.start()
    }
}
