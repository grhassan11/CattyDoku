package com.tm.meow.sudoku.puzzle.di

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.tm.meow.sudoku.puzzle.BuildConfig
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.analytics.NoopAnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.billing.AdsRemovedRepository
import com.tm.meow.sudoku.puzzle.domain.config.NoopRemoteConfigRepository
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentRepository
import com.tm.meow.sudoku.puzzle.domain.notification.NotificationStatus
import com.tm.meow.sudoku.puzzle.domain.repository.AppFlagsRepository
import com.tm.meow.sudoku.puzzle.domain.repository.ProgressRepository
import com.tm.meow.sudoku.puzzle.domain.repository.StreakRepository
import com.tm.meow.sudoku.puzzle.domain.sudoku.repository.SudokuRepository
import com.tm.meow.sudoku.puzzle.domain.time.TimeProvider
import com.tm.meow.sudoku.puzzle.domain.usecase.SyncAnalyticsUserProperties
import com.tm.meow.sudoku.puzzle.domain.usecase.UserPropsSync
import com.tm.meow.sudoku.puzzle.domain.config.RemoteConfigRepository
import com.tm.meow.sudoku.puzzle.domain.crash.CrashReporter
import com.tm.meow.sudoku.puzzle.domain.crash.NoopCrashReporter
import com.tm.meow.sudoku.puzzle.domain.usecase.ApplyTelemetryConsent
import com.tm.meow.sudoku.puzzle.domain.usecase.RefreshRemoteConfig
import com.tm.meow.sudoku.puzzle.firebase.FirebaseAnalyticsLogger
import com.tm.meow.sudoku.puzzle.firebase.FirebaseAvailability
import com.tm.meow.sudoku.puzzle.firebase.FirebaseCrashReporter
import com.tm.meow.sudoku.puzzle.firebase.FirebaseRemoteConfigRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Firebase wiring (phase 10). Each seam picks the real adapter only when the default FirebaseApp exists
 * (google-services.json was applied at build time); otherwise the domain no-op. Never throws at injection time.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides @Singleton
    fun provideAnalyticsLogger(@ApplicationContext context: Context): AnalyticsLogger = try {
        if (FirebaseAvailability.isAvailable(context)) {
            FirebaseAnalyticsLogger(FirebaseAnalytics.getInstance(context))
        } else {
            NoopAnalyticsLogger
        }
    } catch (t: Throwable) {
        NoopAnalyticsLogger
    }

    @Provides @Singleton
    fun provideCrashReporter(@ApplicationContext context: Context): CrashReporter = try {
        if (FirebaseAvailability.isAvailable(context)) {
            FirebaseCrashReporter(FirebaseCrashlytics.getInstance())
        } else {
            NoopCrashReporter
        }
    } catch (t: Throwable) {
        NoopCrashReporter
    }

    @Provides @Singleton
    fun provideRemoteConfigRepository(
        @ApplicationContext context: Context,
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ): RemoteConfigRepository = try {
        if (FirebaseAvailability.isAvailable(context)) {
            FirebaseRemoteConfigRepository(
                remoteConfig = FirebaseRemoteConfig.getInstance(),
                scope = CoroutineScope(SupervisorJob() + dispatcher),
                debug = BuildConfig.DEBUG,
            )
        } else {
            NoopRemoteConfigRepository
        }
    } catch (t: Throwable) {
        NoopRemoteConfigRepository
    }

    @Provides
    fun provideApplyTelemetryConsent(analytics: AnalyticsLogger, crash: CrashReporter): ApplyTelemetryConsent =
        ApplyTelemetryConsent(analytics, crash)

    @Provides @Singleton
    fun provideUserPropsSync(
        analytics: AnalyticsLogger,
        consent: ConsentRepository,
        flags: AppFlagsRepository,
        progress: ProgressRepository,
        sudoku: SudokuRepository,
        streak: StreakRepository,
        adsRemoved: AdsRemovedRepository,
        notifications: NotificationStatus,
        time: TimeProvider,
    ): UserPropsSync = SyncAnalyticsUserProperties(
        analytics, consent, flags, progress, sudoku, streak, adsRemoved, notifications, time,
    )

    @Provides
    fun provideRefreshRemoteConfig(
        repository: RemoteConfigRepository,
        consent: ConsentRepository,
    ): RefreshRemoteConfig = RefreshRemoteConfig(repository) { consent.state.value.canRequestAds }
}
