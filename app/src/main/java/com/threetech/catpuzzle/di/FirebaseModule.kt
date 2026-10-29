package com.threetech.catpuzzle.di

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.threetech.catpuzzle.BuildConfig
import com.threetech.catpuzzle.domain.analytics.AnalyticsLogger
import com.threetech.catpuzzle.domain.analytics.NoopAnalyticsLogger
import com.threetech.catpuzzle.domain.config.NoopRemoteConfigRepository
import com.threetech.catpuzzle.domain.config.RemoteConfigRepository
import com.threetech.catpuzzle.domain.crash.CrashReporter
import com.threetech.catpuzzle.domain.crash.NoopCrashReporter
import com.threetech.catpuzzle.domain.usecase.ApplyTelemetryConsent
import com.threetech.catpuzzle.domain.usecase.RefreshRemoteConfig
import com.threetech.catpuzzle.firebase.FirebaseAnalyticsLogger
import com.threetech.catpuzzle.firebase.FirebaseAvailability
import com.threetech.catpuzzle.firebase.FirebaseCrashReporter
import com.threetech.catpuzzle.firebase.FirebaseRemoteConfigRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    fun provideRemoteConfigRepository(@ApplicationContext context: Context): RemoteConfigRepository = try {
        if (FirebaseAvailability.isAvailable(context)) {
            FirebaseRemoteConfigRepository(
                remoteConfig = FirebaseRemoteConfig.getInstance(),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
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

    @Provides
    fun provideRefreshRemoteConfig(repository: RemoteConfigRepository): RefreshRemoteConfig =
        RefreshRemoteConfig(repository)
}
