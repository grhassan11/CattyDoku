package com.tm.meow.sudoku.puzzle.di

import android.util.Log
import com.tm.meow.sudoku.puzzle.BuildConfig
import com.tm.meow.sudoku.puzzle.domain.ads.AdLogger
import com.tm.meow.sudoku.puzzle.data.ads.AdsRepositoryImpl
import com.tm.meow.sudoku.puzzle.data.ads.AppOpenAdRepositoryImpl
import com.tm.meow.sudoku.puzzle.data.ads.RewardedAdRepositoryImpl
import com.tm.meow.sudoku.puzzle.data.ads.NativeAdRepositoryImpl
import com.tm.meow.sudoku.puzzle.data.consent.ConsentRepositoryImpl
import com.tm.meow.sudoku.puzzle.domain.ads.AdPolicySource
import com.tm.meow.sudoku.puzzle.domain.ads.AdSessionTracker
import com.tm.meow.sudoku.puzzle.domain.ads.AdsRepository
import com.tm.meow.sudoku.puzzle.domain.ads.AdsSdkStatus
import com.tm.meow.sudoku.puzzle.domain.ads.AppOpenAdRepository
import com.tm.meow.sudoku.puzzle.domain.ads.AppOpenConfig
import com.tm.meow.sudoku.puzzle.domain.ads.RewardedAdRepository
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.billing.AdsRemovedRepository
import com.tm.meow.sudoku.puzzle.domain.repository.InventoryRepository
import com.tm.meow.sudoku.puzzle.domain.usecase.GrantBoosterReward
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveAdSurfaces
import com.tm.meow.sudoku.puzzle.domain.usecase.PreloadInterstitialAd
import com.tm.meow.sudoku.puzzle.domain.usecase.PreloadRewardedAd
import com.tm.meow.sudoku.puzzle.domain.usecase.ReviveGame
import com.tm.meow.sudoku.puzzle.domain.usecase.ShowRewardedAd
import com.tm.meow.sudoku.puzzle.domain.ads.NativeAdRepository
import com.tm.meow.sudoku.puzzle.domain.config.RemoteConfigRepository
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentRepository
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveOnline
import com.tm.meow.sudoku.puzzle.domain.network.ConnectivityObserver
import com.tm.meow.sudoku.puzzle.domain.repository.AppFlagsRepository
import com.tm.meow.sudoku.puzzle.domain.time.TimeProvider
import com.tm.meow.sudoku.puzzle.domain.usecase.EnsureConsentAndAdsInit
import com.tm.meow.sudoku.puzzle.domain.usecase.GatherConsentAndInitAds
import com.tm.meow.sudoku.puzzle.domain.usecase.RecoverAfterReconnect
import com.tm.meow.sudoku.puzzle.domain.usecase.RetryAdsInit
import com.tm.meow.sudoku.puzzle.domain.usecase.IsConsentFormShowing
import com.tm.meow.sudoku.puzzle.domain.usecase.LoadNativeAd
import com.tm.meow.sudoku.puzzle.domain.usecase.MarkStartFlowComplete
import com.tm.meow.sudoku.puzzle.domain.usecase.SetAppOpenScreenSuppressed
import com.tm.meow.sudoku.puzzle.domain.usecase.PreloadAppOpenAd
import com.tm.meow.sudoku.puzzle.domain.startup.SplashTiming
import com.tm.meow.sudoku.puzzle.domain.usecase.ShowAppOpenIfAllowed
import com.tm.meow.sudoku.puzzle.domain.usecase.ShowAppOpenOnSplash
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveConsentState
import com.tm.meow.sudoku.puzzle.domain.usecase.RecordLevelWon
import com.tm.meow.sudoku.puzzle.domain.usecase.RefreshRemoteConfig
import com.tm.meow.sudoku.puzzle.domain.usecase.ShowInterstitialIfAllowed
import com.tm.meow.sudoku.puzzle.domain.usecase.ShowPrivacyOptions
import com.tm.meow.sudoku.puzzle.domain.ads.GameNativeAdCache
import com.tm.meow.sudoku.puzzle.domain.usecase.PreloadGameNativeAd
import com.tm.meow.sudoku.puzzle.domain.usecase.TakeGameNativeAd
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Ads + consent wiring (phase 9). Impls are @Singleton themselves. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AdsBindsModule {
    @Binds abstract fun bindAds(impl: AdsRepositoryImpl): AdsRepository

    /** Same singleton as [bindAds]: one SDK-init state shared by interstitial, native and app-open. */
    @Binds abstract fun bindSdkStatus(impl: AdsRepositoryImpl): AdsSdkStatus

    @Binds abstract fun bindNative(impl: NativeAdRepositoryImpl): NativeAdRepository

    @Binds abstract fun bindAppOpen(impl: AppOpenAdRepositoryImpl): AppOpenAdRepository

    @Binds abstract fun bindRewarded(impl: RewardedAdRepositoryImpl): RewardedAdRepository

    @Binds abstract fun bindConsent(impl: ConsentRepositoryImpl): ConsentRepository
}

@Module
@InstallIn(SingletonComponent::class)
object AdsModule {

    /**
     * LIVE Remote Config backed policy (B+C: a fetch that activates mid-session applies to the next decision; consumers read
     * AdPolicySource.current() once per decision). Defaults = AdPolicyConfig defaults.
     */
    @Provides @Singleton
    fun provideAdPolicySource(remoteConfig: RemoteConfigRepository): AdPolicySource =
        AdPolicySource { remoteConfig.current().toAdPolicyConfig() }

    /** App-open policy constants (no remote switch, no level gate). */
    @Provides @Singleton
    fun provideAppOpenConfig(): AppOpenConfig = AppOpenConfig()

    @Provides @Singleton
    fun provideAdSessionTracker(time: TimeProvider): AdSessionTracker = AdSessionTracker(time)

    @Provides
    fun provideGatherConsentAndInitAds(
        consent: ConsentRepository,
        ads: AdsRepository,
        flags: AppFlagsRepository,
        tracker: AdSessionTracker,
    ): GatherConsentAndInitAds = GatherConsentAndInitAds(consent, ads, flags, tracker)

    @Provides
    fun provideRetryAdsInit(gather: GatherConsentAndInitAds): RetryAdsInit = RetryAdsInit(gather)

    @Provides
    fun provideEnsureConsentAndAdsInit(
        gather: GatherConsentAndInitAds,
        refreshRemoteConfig: RefreshRemoteConfig,
        tracker: AdSessionTracker,
    ): EnsureConsentAndAdsInit = EnsureConsentAndAdsInit(gather, refreshRemoteConfig, tracker)

    /** Singleton: its Mutex is the single-flight guard shared by the connectivity observer and the resume fallback. */
    @Provides @Singleton
    fun provideRecoverAfterReconnect(
        consent: ConsentRepository,
        ads: AdsRepository,
        tracker: AdSessionTracker,
        refreshRemoteConfig: RefreshRemoteConfig,
        preloadInterstitial: PreloadInterstitialAd,
        preloadAppOpen: PreloadAppOpenAd,
        connectivity: ConnectivityObserver,
        logger: AdLogger,
    ): RecoverAfterReconnect = RecoverAfterReconnect(
        consent, ads, tracker, refreshRemoteConfig,
        preloadAds = {
            preloadInterstitial(null) // null = no level gate (shared unit); policy switches / consent / Remove Ads gated inside
            preloadAppOpen()
        },
        connectivity = connectivity,
        logger = logger,
    )

    @Provides
    fun provideIsConsentFormShowing(tracker: AdSessionTracker): IsConsentFormShowing = IsConsentFormShowing(tracker)

    @Provides
    fun provideObserveConsentState(consent: ConsentRepository): ObserveConsentState = ObserveConsentState(consent)

    @Provides
    fun provideShowPrivacyOptions(consent: ConsentRepository, ads: AdsRepository): ShowPrivacyOptions =
        ShowPrivacyOptions(consent, ads)

    @Provides
    fun provideRecordLevelWon(tracker: AdSessionTracker): RecordLevelWon = RecordLevelWon(tracker)

    /** Debug-only decision trace (tag "MeowAds"); release stays silent. */
    @Provides @Singleton
    fun provideAdLogger(): AdLogger = AdLogger { message ->
        if (BuildConfig.DEBUG) Log.d("MeowAds", message)
    }

    @Provides
    fun provideShowInterstitialIfAllowed(
        consent: ConsentRepository,
        ads: AdsRepository,
        tracker: AdSessionTracker,
        flags: AppFlagsRepository,
        config: AdPolicySource,
        adsRemoved: AdsRemovedRepository,
        logger: AdLogger,
        analytics: AnalyticsLogger,
        connectivity: ConnectivityObserver,
    ): ShowInterstitialIfAllowed =
        ShowInterstitialIfAllowed(consent, ads, tracker, flags, config, adsRemoved, logger, analytics, connectivity)

    @Provides
    fun providePreloadInterstitialAd(
        consent: ConsentRepository,
        ads: AdsRepository,
        config: AdPolicySource,
        adsRemoved: AdsRemovedRepository,
        logger: AdLogger,
        connectivity: ConnectivityObserver,
    ): PreloadInterstitialAd = PreloadInterstitialAd(consent, ads, config, adsRemoved, logger, connectivity)

    @Provides
    fun provideLoadNativeAd(
        consent: ConsentRepository,
        repository: NativeAdRepository,
        config: AdPolicySource,
        adsRemoved: AdsRemovedRepository,
        connectivity: ConnectivityObserver,
    ): LoadNativeAd = LoadNativeAd(consent, repository, config, adsRemoved, connectivity)

    /** One preloaded game-screen native per mode, process-wide (survives navigation); destroyed on expiry / Remove Ads. */
    @Provides @Singleton
    fun provideGameNativeAdCache(
        time: TimeProvider,
        adsRemoved: AdsRemovedRepository,
        @MainDispatcher main: CoroutineDispatcher,
    ): GameNativeAdCache = GameNativeAdCache(time, CoroutineScope(SupervisorJob() + main), adsRemoved)

    @Provides
    fun providePreloadGameNativeAd(
        loadNative: LoadNativeAd,
        cache: GameNativeAdCache,
        flags: AppFlagsRepository,
    ): PreloadGameNativeAd = PreloadGameNativeAd(loadNative, cache, flags)

    @Provides
    fun provideTakeGameNativeAd(loadNative: LoadNativeAd, cache: GameNativeAdCache): TakeGameNativeAd =
        TakeGameNativeAd(loadNative, cache)

    @Provides
    fun provideObserveOnline(connectivity: ConnectivityObserver): ObserveOnline = ObserveOnline(connectivity)

    @Provides
    fun provideMarkStartFlowComplete(tracker: AdSessionTracker): MarkStartFlowComplete = MarkStartFlowComplete(tracker)

    @Provides
    fun provideSetAppOpenScreenSuppressed(tracker: AdSessionTracker): SetAppOpenScreenSuppressed =
        SetAppOpenScreenSuppressed(tracker)

    @Provides
    fun providePreloadAppOpenAd(
        consent: ConsentRepository,
        sdk: AdsSdkStatus,
        repository: AppOpenAdRepository,
        adsRemoved: AdsRemovedRepository,
        connectivity: ConnectivityObserver,
        logger: AdLogger,
    ): PreloadAppOpenAd = PreloadAppOpenAd(consent, sdk, repository, adsRemoved, connectivity, logger)

    @Provides
    fun provideShowAppOpenIfAllowed(
        consent: ConsentRepository,
        sdk: AdsSdkStatus,
        repository: AppOpenAdRepository,
        tracker: AdSessionTracker,
        config: AppOpenConfig,
        preload: PreloadAppOpenAd,
        adsRemoved: AdsRemovedRepository,
        connectivity: ConnectivityObserver,
        logger: AdLogger,
    ): ShowAppOpenIfAllowed = ShowAppOpenIfAllowed(consent, sdk, repository, tracker, config, preload, adsRemoved, connectivity, logger)

    @Provides
    fun provideShowAppOpenOnSplash(
        repository: AppOpenAdRepository,
        preload: PreloadAppOpenAd,
        show: ShowAppOpenIfAllowed,
        logger: AdLogger,
        remoteConfig: RemoteConfigRepository,
    ): ShowAppOpenOnSplash = ShowAppOpenOnSplash(repository, preload, show, SplashTiming.APP_OPEN_WAIT_MS, logger, remoteConfig)

    // ---- Game screen: rewarded + compact native slot (shared ad-free early-level rule in AdPolicyConfig / AdFreePolicy) ----

    @Provides
    fun provideObserveAdSurfaces(
        flags: AppFlagsRepository,
        consent: ConsentRepository,
        sdk: AdsSdkStatus,
        rewarded: RewardedAdRepository,
        nativeAds: NativeAdRepository,
        config: AdPolicySource,
        adsRemoved: AdsRemovedRepository,
        logger: AdLogger,
        connectivity: ConnectivityObserver,
    ): ObserveAdSurfaces = ObserveAdSurfaces(flags, consent, sdk, rewarded, nativeAds, config, adsRemoved, logger, connectivity)

    @Provides
    fun providePreloadRewardedAd(
        consent: ConsentRepository,
        sdk: AdsSdkStatus,
        repository: RewardedAdRepository,
        config: AdPolicySource,
        connectivity: ConnectivityObserver,
    ): PreloadRewardedAd = PreloadRewardedAd(consent, sdk, repository, config, connectivity)

    @Provides
    fun provideShowRewardedAd(
        consent: ConsentRepository,
        sdk: AdsSdkStatus,
        repository: RewardedAdRepository,
        tracker: AdSessionTracker,
        config: AdPolicySource,
        connectivity: ConnectivityObserver,
    ): ShowRewardedAd = ShowRewardedAd(consent, sdk, repository, tracker, config, connectivity)

    @Provides
    fun provideGrantBoosterReward(inventory: InventoryRepository): GrantBoosterReward = GrantBoosterReward(inventory)

    @Provides
    fun provideReviveGame(): ReviveGame = ReviveGame()
}
