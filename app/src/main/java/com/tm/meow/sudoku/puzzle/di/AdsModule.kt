package com.tm.meow.sudoku.puzzle.di

import android.util.Log
import com.tm.meow.sudoku.puzzle.BuildConfig
import com.tm.meow.sudoku.puzzle.domain.ads.AdLogger
import com.tm.meow.sudoku.puzzle.data.ads.AdsRepositoryImpl
import com.tm.meow.sudoku.puzzle.data.ads.AppOpenAdRepositoryImpl
import com.tm.meow.sudoku.puzzle.data.ads.BannerAdRepositoryImpl
import com.tm.meow.sudoku.puzzle.data.ads.RewardedAdRepositoryImpl
import com.tm.meow.sudoku.puzzle.data.ads.NativeAdRepositoryImpl
import com.tm.meow.sudoku.puzzle.data.consent.ConsentRepositoryImpl
import com.tm.meow.sudoku.puzzle.domain.ads.AdPolicyConfig
import com.tm.meow.sudoku.puzzle.domain.ads.AdSessionTracker
import com.tm.meow.sudoku.puzzle.domain.ads.AdsRepository
import com.tm.meow.sudoku.puzzle.domain.ads.AdsSdkStatus
import com.tm.meow.sudoku.puzzle.domain.ads.AppOpenAdRepository
import com.tm.meow.sudoku.puzzle.domain.ads.AppOpenConfig
import com.tm.meow.sudoku.puzzle.domain.ads.BannerAdRepository
import com.tm.meow.sudoku.puzzle.domain.ads.RewardedAdRepository
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.billing.AdsRemovedRepository
import com.tm.meow.sudoku.puzzle.domain.repository.InventoryRepository
import com.tm.meow.sudoku.puzzle.domain.usecase.GrantBoosterReward
import com.tm.meow.sudoku.puzzle.domain.usecase.LoadBannerAd
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveAdSurfaces
import com.tm.meow.sudoku.puzzle.domain.usecase.PreloadRewardedAd
import com.tm.meow.sudoku.puzzle.domain.usecase.ReviveGame
import com.tm.meow.sudoku.puzzle.domain.usecase.ShowRewardedAd
import com.tm.meow.sudoku.puzzle.domain.ads.NativeAdRepository
import com.tm.meow.sudoku.puzzle.domain.config.RemoteConfigRepository
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentRepository
import com.tm.meow.sudoku.puzzle.domain.repository.AppFlagsRepository
import com.tm.meow.sudoku.puzzle.domain.time.TimeProvider
import com.tm.meow.sudoku.puzzle.domain.usecase.GatherConsentAndInitAds
import com.tm.meow.sudoku.puzzle.domain.usecase.IsConsentFormShowing
import com.tm.meow.sudoku.puzzle.domain.usecase.LoadNativeAd
import com.tm.meow.sudoku.puzzle.domain.usecase.MarkStartFlowComplete
import com.tm.meow.sudoku.puzzle.domain.usecase.PreloadAppOpenAd
import com.tm.meow.sudoku.puzzle.domain.usecase.ShowAppOpenIfAllowed
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveConsentState
import com.tm.meow.sudoku.puzzle.domain.usecase.RecordLevelWon
import com.tm.meow.sudoku.puzzle.domain.usecase.ShowInterstitialIfAllowed
import com.tm.meow.sudoku.puzzle.domain.usecase.ShowPrivacyOptions
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

    @Binds abstract fun bindBanner(impl: BannerAdRepositoryImpl): BannerAdRepository

    @Binds abstract fun bindConsent(impl: ConsentRepositoryImpl): ConsentRepository
}

@Module
@InstallIn(SingletonComponent::class)
object AdsModule {

    /** Remote Config backed (frozen per process, see RemoteConfigRepository). Defaults = [AdPolicyConfig] defaults. */
    @Provides @Singleton
    fun provideAdPolicyConfig(remoteConfig: RemoteConfigRepository): AdPolicyConfig =
        remoteConfig.current().toAdPolicyConfig()

    /** Remote Config backed (frozen per process). Defaults = [AppOpenConfig] defaults. */
    @Provides @Singleton
    fun provideAppOpenConfig(remoteConfig: RemoteConfigRepository): AppOpenConfig =
        remoteConfig.current().toAppOpenConfig()

    @Provides @Singleton
    fun provideAdSessionTracker(time: TimeProvider): AdSessionTracker = AdSessionTracker(time)

    @Provides
    fun provideGatherConsentAndInitAds(
        consent: ConsentRepository,
        ads: AdsRepository,
        flags: AppFlagsRepository,
    ): GatherConsentAndInitAds = GatherConsentAndInitAds(consent, ads, flags)

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
        config: AdPolicyConfig,
        adsRemoved: AdsRemovedRepository,
        logger: AdLogger,
        analytics: AnalyticsLogger,
    ): ShowInterstitialIfAllowed =
        ShowInterstitialIfAllowed(consent, ads, tracker, flags, config, adsRemoved, logger, analytics)

    @Provides
    fun provideLoadNativeAd(
        consent: ConsentRepository,
        repository: NativeAdRepository,
        config: AdPolicyConfig,
        adsRemoved: AdsRemovedRepository,
    ): LoadNativeAd = LoadNativeAd(consent, repository, config, adsRemoved)

    @Provides
    fun provideMarkStartFlowComplete(tracker: AdSessionTracker): MarkStartFlowComplete = MarkStartFlowComplete(tracker)

    @Provides
    fun providePreloadAppOpenAd(
        consent: ConsentRepository,
        sdk: AdsSdkStatus,
        config: AppOpenConfig,
        repository: AppOpenAdRepository,
        adsRemoved: AdsRemovedRepository,
    ): PreloadAppOpenAd = PreloadAppOpenAd(consent, sdk, config, repository, adsRemoved)

    @Provides
    fun provideShowAppOpenIfAllowed(
        consent: ConsentRepository,
        sdk: AdsSdkStatus,
        repository: AppOpenAdRepository,
        tracker: AdSessionTracker,
        config: AppOpenConfig,
        preload: PreloadAppOpenAd,
        flags: AppFlagsRepository,
        adsRemoved: AdsRemovedRepository,
    ): ShowAppOpenIfAllowed = ShowAppOpenIfAllowed(consent, sdk, repository, tracker, config, preload, flags, adsRemoved)

    // ---- Game screen: rewarded + sticky banner (shared ad-free early-level rule in AdPolicyConfig / AdFreePolicy) ----

    @Provides
    fun provideObserveAdSurfaces(
        flags: AppFlagsRepository,
        consent: ConsentRepository,
        sdk: AdsSdkStatus,
        rewarded: RewardedAdRepository,
        banner: BannerAdRepository,
        config: AdPolicyConfig,
        adsRemoved: AdsRemovedRepository,
    ): ObserveAdSurfaces = ObserveAdSurfaces(flags, consent, sdk, rewarded, banner, config, adsRemoved)

    @Provides
    fun providePreloadRewardedAd(
        consent: ConsentRepository,
        sdk: AdsSdkStatus,
        repository: RewardedAdRepository,
        config: AdPolicyConfig,
    ): PreloadRewardedAd = PreloadRewardedAd(consent, sdk, repository, config)

    @Provides
    fun provideShowRewardedAd(
        consent: ConsentRepository,
        sdk: AdsSdkStatus,
        repository: RewardedAdRepository,
        tracker: AdSessionTracker,
        config: AdPolicyConfig,
    ): ShowRewardedAd = ShowRewardedAd(consent, sdk, repository, tracker, config)

    @Provides
    fun provideGrantBoosterReward(inventory: InventoryRepository): GrantBoosterReward = GrantBoosterReward(inventory)

    @Provides
    fun provideReviveGame(): ReviveGame = ReviveGame()

    @Provides
    fun provideLoadBannerAd(
        consent: ConsentRepository,
        sdk: AdsSdkStatus,
        repository: BannerAdRepository,
        config: AdPolicyConfig,
        adsRemoved: AdsRemovedRepository,
    ): LoadBannerAd = LoadBannerAd(consent, sdk, repository, config, adsRemoved)
}
