package com.tm.meow.sudoku.puzzle

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.view.Choreographer
import com.tm.meow.sudoku.puzzle.audio.AudioController
import com.tm.meow.sudoku.puzzle.data.ads.AppOpenLifecycleObserver
import com.tm.meow.sudoku.puzzle.data.billing.BillingLifecycleObserver
import com.tm.meow.sudoku.puzzle.data.notification.DailyReminderObserver
import com.tm.meow.sudoku.puzzle.data.notification.NotificationPermissionObserver
import com.tm.meow.sudoku.puzzle.data.notification.StreakReminderObserver
import com.tm.meow.sudoku.puzzle.data.notification.SudokuReminderObserver
import com.tm.meow.sudoku.puzzle.data.platform.CurrentActivityProvider
import com.tm.meow.sudoku.puzzle.di.IoDispatcher
import com.tm.meow.sudoku.puzzle.domain.crash.CrashReporter
import com.tm.meow.sudoku.puzzle.firebase.TelemetryConsentObserver
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class CatPuzzleApp : Application() {
    @Inject lateinit var currentActivityProvider: CurrentActivityProvider
        @Inject lateinit var telemetryConsentObserver: TelemetryConsentObserver
    @Inject lateinit var audioController: AudioController
    @Inject lateinit var crashReporter: CrashReporter
    @Inject @field:IoDispatcher lateinit var ioDispatcher: CoroutineDispatcher

    // App-open + billing observers also start after the first frame (see runAfterFirstFrame): ProcessLifecycleOwner replays ON_START
    // to a late observer, which each observer's own coldStartPending flag treats as the cold start (no ad, no duplicate sync).
    @Inject lateinit var appOpenObserver: Lazy<AppOpenLifecycleObserver>
    @Inject lateinit var billingObserver: Lazy<BillingLifecycleObserver>

    // Reminder schedulers are not needed for the first frame: built and started after it (see runAfterFirstFrame).
    @Inject lateinit var streakReminderObserver: Lazy<StreakReminderObserver>
    @Inject lateinit var dailyReminderObserver: Lazy<DailyReminderObserver>
    @Inject lateinit var sudokuReminderObserver: Lazy<SudokuReminderObserver>
    @Inject lateinit var notificationPermissionObserver: Lazy<NotificationPermissionObserver>

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) enableStrictMode()
        // Hilt has injected by now (super.onCreate). Gives UMP / ads wrappers a foreground Activity.
        currentActivityProvider.register(this)
        // Analytics / Crashlytics stay off until UMP consent allows them (follows every consent change).
        telemetryConsentObserver.start()
        // Background music + SFX owner: Music/Sound settings, process foreground, full-screen-ad silencing.
        audioController.start()
        // Ordering above is deliberate: ActivityProvider, telemetry (must be collecting before the splash runs gatherConsent,
        // its drop(1) skips only the fail-closed default), audio.
        runAfterFirstFrame {
            // Resume-only app-open ads (cold start never shows). Needs the ProcessLifecycleOwner (lifecycle-process).
            appOpenObserver.get().start()
            // Play Billing: listen for purchase updates + sync purchases / entitlement now and on every resume.
            billingObserver.get().start()
            // Evening streak-at-risk reminder: books / cancels the WorkManager check on start, toggle change, and every win.
            streakReminderObserver.get().start()
            // Daily 20:00 reminder: books on start and toggle change; the worker re-books itself every day.
            dailyReminderObserver.get().start()
            // Unfinished-Sudoku one-shot reminder: books ~4 h after the app goes to background with a saved game; cancels on resume / finish / toggle off.
            sudokuReminderObserver.get().start()
            // Permission granted outside the app (system settings) -> all reminder toggles ON once, on every foreground.
            notificationPermissionObserver.get().start()
            logLastExitReason()
        }
    }

    /** Why the previous process ended (API 30+), as Crashlytics keys. Read on IO, never on main. */
    private fun logLastExitReason() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        CoroutineScope(SupervisorJob() + ioDispatcher).launch {
            try {
                val manager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return@launch
                val last = manager.getHistoricalProcessExitReasons(packageName, 0, 1).firstOrNull() ?: return@launch
                crashReporter.setCustomKey("last_exit_reason", exitReasonName(last.reason))
                crashReporter.setCustomKey("last_exit_importance", last.importance)
                crashReporter.setCustomKey("last_exit_at_ms", last.timestamp.toString())
            } catch (t: Throwable) {
                // observability only; never affect the app
            }
        }
    }

    private fun exitReasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INIT_FAILURE"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        else -> "OTHER_$reason"
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Key only (cheap, last value wins): shows the last memory pressure level seen before a kill / crash.
        if (::crashReporter.isInitialized) crashReporter.setCustomKey("last_trim_memory_level", level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (::crashReporter.isInitialized) {
            crashReporter.setCustomKey("low_memory_seen", true)
            crashReporter.log("onLowMemory")
        }
    }

    /**
     * Runs [block] once on the main thread after the first Activity has drawn its first frame. Fallback: [DEFERRED_FALLBACK_MS]
     * after process start, so a headless process (WorkManager wake-up, no Activity) still gets its observers.
     */
    private fun runAfterFirstFrame(block: () -> Unit) {
        val done = AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())
        val once = Runnable { if (done.compareAndSet(false, true)) block() }
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                unregisterActivityLifecycleCallbacks(this)
                // Resume -> next vsync is the first drawn frame; the block then runs on the following main-thread turn.
                Choreographer.getInstance().postFrameCallback { handler.post(once) }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        handler.postDelayed(once, DEFERRED_FALLBACK_MS)
    }

    private companion object {
        const val DEFERRED_FALLBACK_MS = 5_000L
    }

    /** Debug builds only: log main-thread I/O and leaked closeables / activities to Logcat (tag StrictMode). Never kills the app. */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectActivityLeaks()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .detectLeakedSqlLiteObjects()
                .detectFileUriExposure()
                .penaltyLog()
                .build(),
        )
    }
}
