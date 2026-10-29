package com.threetech.catpuzzle.di

import com.threetech.catpuzzle.data.billing.PlayBillingGateway
import com.threetech.catpuzzle.data.billing.ShopRepositoryImpl
import com.threetech.catpuzzle.data.repository.AdsRemovedRepositoryImpl
import com.threetech.catpuzzle.data.repository.PurchaseGrantRepositoryImpl
import com.threetech.catpuzzle.domain.analytics.AnalyticsLogger
import com.threetech.catpuzzle.domain.billing.AdsRemovedRepository
import com.threetech.catpuzzle.domain.billing.BillingGateway
import com.threetech.catpuzzle.domain.billing.PurchaseProcessor
import com.threetech.catpuzzle.domain.repository.InventoryRepository
import com.threetech.catpuzzle.domain.repository.PurchaseGrantRepository
import com.threetech.catpuzzle.domain.repository.ShopRepository
import com.threetech.catpuzzle.domain.time.TimeProvider
import com.threetech.catpuzzle.domain.usecase.BuyProduct
import com.threetech.catpuzzle.domain.usecase.LoadShopCatalog
import com.threetech.catpuzzle.domain.usecase.ObserveAdsRemoved
import com.threetech.catpuzzle.domain.usecase.ObserveBoosterBalances
import com.threetech.catpuzzle.domain.usecase.ObserveEntitlement
import com.threetech.catpuzzle.domain.usecase.ObservePurchaseEvents
import com.threetech.catpuzzle.domain.usecase.ObserveShopCatalog
import com.threetech.catpuzzle.domain.usecase.RestorePurchases
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Shop + Play Billing wiring. Impls with `@Inject` are singletons themselves. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BillingBindsModule {
    @Binds abstract fun bindGateway(impl: PlayBillingGateway): BillingGateway

    @Binds abstract fun bindAdsRemoved(impl: AdsRemovedRepositoryImpl): AdsRemovedRepository

    @Binds abstract fun bindPurchaseGrants(impl: PurchaseGrantRepositoryImpl): PurchaseGrantRepository
}

@Module
@InstallIn(SingletonComponent::class)
object BillingModule {

    @Provides
    fun providePurchaseProcessor(
        gateway: BillingGateway,
        grants: PurchaseGrantRepository,
        analytics: AnalyticsLogger,
    ): PurchaseProcessor = PurchaseProcessor(gateway, grants, analytics)

    /** App-lifetime scope: purchase updates must be processed even when no screen is open. */
    @Provides @Singleton
    fun provideShopRepository(
        gateway: BillingGateway,
        processor: PurchaseProcessor,
        adsRemoved: AdsRemovedRepository,
        time: TimeProvider,
    ): ShopRepository = ShopRepositoryImpl(
        gateway = gateway,
        processor = processor,
        adsRemoved = adsRemoved,
        time = time,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    @Provides
    fun provideObserveAdsRemoved(adsRemoved: AdsRemovedRepository): ObserveAdsRemoved = ObserveAdsRemoved(adsRemoved)

    @Provides
    fun provideObserveEntitlement(adsRemoved: AdsRemovedRepository): ObserveEntitlement = ObserveEntitlement(adsRemoved)

    @Provides
    fun provideObserveShopCatalog(shop: ShopRepository): ObserveShopCatalog = ObserveShopCatalog(shop)

    @Provides
    fun provideLoadShopCatalog(shop: ShopRepository): LoadShopCatalog = LoadShopCatalog(shop)

    @Provides
    fun provideBuyProduct(shop: ShopRepository): BuyProduct = BuyProduct(shop)

    @Provides
    fun provideRestorePurchases(shop: ShopRepository): RestorePurchases = RestorePurchases(shop)

    @Provides
    fun provideObservePurchaseEvents(shop: ShopRepository): ObservePurchaseEvents = ObservePurchaseEvents(shop)

    @Provides
    fun provideObserveBoosterBalances(inventory: InventoryRepository): ObserveBoosterBalances =
        ObserveBoosterBalances(inventory)
}
