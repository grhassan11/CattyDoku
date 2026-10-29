package com.threetech.catpuzzle

import android.app.Application
import com.threetech.catpuzzle.audio.AudioController
import com.threetech.catpuzzle.data.ads.AppOpenLifecycleObserver
import com.threetech.catpuzzle.data.billing.BillingLifecycleObserver
import com.threetech.catpuzzle.data.notification.DailyReminderObserver
import com.threetech.catpuzzle.data.notification.StreakReminderObserver
import com.threetech.catpuzzle.data.platform.CurrentActivityProvider
import com.threetech.catpuzzle.firebase.TelemetryConsentObserver
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
