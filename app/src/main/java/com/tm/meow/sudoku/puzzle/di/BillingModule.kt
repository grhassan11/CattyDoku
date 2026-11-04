package com.tm.meow.sudoku.puzzle.di

import com.tm.meow.sudoku.puzzle.data.billing.PlayBillingGateway
import com.tm.meow.sudoku.puzzle.data.billing.ShopRepositoryImpl
import com.tm.meow.sudoku.puzzle.data.repository.AdsRemovedRepositoryImpl
import com.tm.meow.sudoku.puzzle.data.repository.PurchaseGrantRepositoryImpl
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.billing.AdsRemovedRepository
import com.tm.meow.sudoku.puzzle.domain.billing.BillingGateway
import com.tm.meow.sudoku.puzzle.domain.billing.PurchaseProcessor
import com.tm.meow.sudoku.puzzle.domain.repository.AppFlagsRepository
import com.tm.meow.sudoku.puzzle.domain.repository.InventoryRepository
import com.tm.meow.sudoku.puzzle.domain.repository.PurchaseGrantRepository
import com.tm.meow.sudoku.puzzle.domain.repository.ShopRepository
import com.tm.meow.sudoku.puzzle.domain.time.TimeProvider
import com.tm.meow.sudoku.puzzle.domain.usecase.BuyProduct
import com.tm.meow.sudoku.puzzle.domain.usecase.LoadShopCatalog
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveAdsRemoved
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveBoosterBalances
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveEntitlement
import com.tm.meow.sudoku.puzzle.domain.usecase.ObservePurchaseEvents
import com.tm.meow.sudoku.puzzle.domain.usecase.ObserveShopCatalog
import com.tm.meow.sudoku.puzzle.domain.usecase.RestorePurchases
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

    /** Singleton: holds the announced-pending token set (pending message shows once per token per process). */
    @Provides @Singleton
    fun providePurchaseProcessor(
        gateway: BillingGateway,
        grants: PurchaseGrantRepository,
        analytics: AnalyticsLogger,
        flags: AppFlagsRepository,
    ): PurchaseProcessor = PurchaseProcessor(gateway, grants, analytics, flags)

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
