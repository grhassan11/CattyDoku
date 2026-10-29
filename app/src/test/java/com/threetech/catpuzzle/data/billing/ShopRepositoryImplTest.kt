package com.threetech.catpuzzle.data.billing

import app.cash.turbine.test
import com.threetech.catpuzzle.domain.billing.BillingFailure
import com.threetech.catpuzzle.domain.billing.CatalogState
import com.threetech.catpuzzle.domain.billing.Entitlement
import com.threetech.catpuzzle.domain.billing.FakeAdsRemovedRepository
import com.threetech.catpuzzle.domain.billing.FakeBillingGateway
import com.threetech.catpuzzle.domain.billing.FakePurchaseGrants
import com.threetech.catpuzzle.domain.billing.ProductPrice
import com.threetech.catpuzzle.domain.billing.PurchaseEvent
import com.threetech.catpuzzle.domain.billing.PurchaseProcessor
import com.threetech.catpuzzle.domain.billing.PurchaseStatus
import com.threetech.catpuzzle.domain.billing.ShopProduct
import com.threetech.catpuzzle.domain.billing.StoreResult
import com.threetech.catpuzzle.domain.billing.purchase
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.domain.usecase.FakeTimeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShopRepositoryImplTest {
    private val gateway = FakeBillingGateway()
    private val grants = FakePurchaseGrants()
    private val time = FakeTimeProvider(now = 5_000L)

    private fun newAds(initial: Entitlement = Entitlement.NONE) = FakeAdsRemovedRepository(initial) { time.now }

    private fun TestScope.repo(ads: FakeAdsRemovedRepository = newAds()) = ShopRepositoryImpl(
        gateway = gateway,
        processor = PurchaseProcessor(gateway, grants),
        adsRemoved = ads,
        time = time,
        // Runs eagerly on the test scheduler; cancelled with the test (child of backgroundScope).
        scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
    )

    // ---------------------------------------------------------------- catalog

    @Test
    fun catalogReadyCarriesPlayPricesVerbatim() = runTest {
        gateway.productsResult = StoreResult.Success(
            mapOf("cat_pack_5" to ProductPrice("cat_pack_5", "Rs 270.00")),
        )
        val r = repo()
        assertEquals(CatalogState.Loading, r.catalog.value)
        r.loadCatalog()
        val ready = r.catalog.value as CatalogState.Ready
        assertEquals("Rs 270.00", ready.prices.getValue("cat_pack_5").formattedPrice)
    }

    @Test
    fun catalogUnavailableWhenBillingFailsOrNoProducts() = runTest {
        val r = repo()
        gateway.productsResult = StoreResult.Failure(BillingFailure.BILLING_UNAVAILABLE)
        r.loadCatalog()
        assertEquals(CatalogState.Unavailable(BillingFailure.BILLING_UNAVAILABLE), r.catalog.value)

        gateway.productsResult = StoreResult.Success(emptyMap())
        r.loadCatalog()
        assertEquals(CatalogState.Unavailable(BillingFailure.ITEM_UNAVAILABLE), r.catalog.value)
    }

    @Test
    fun failedRefreshKeepsEarlierPrices() = runTest {
        val r = repo()
        gateway.productsResult = StoreResult.Success(mapOf("cat_pack_5" to ProductPrice("cat_pack_5", "$1.99")))
        r.loadCatalog()
        gateway.productsResult = StoreResult.Failure(BillingFailure.NETWORK)
        r.loadCatalog()
        assertTrue(r.catalog.value is CatalogState.Ready)
    }

    // ---------------------------------------------------------------- sync / entitlement

    @Test
    fun startSyncAppliesEntitlementFromPlayAndAcknowledges() = runTest {
        val ads = newAds()
        gateway.purchasesResult = StoreResult.Success(listOf(purchase("remove_ads_forever", token = "f")))
        val r = repo(ads)
        r.start()
        assertTrue(ads.entitlement.value.foreverOwned)
        assertTrue(ads.adsRemoved.value)
        assertEquals(listOf("f"), gateway.acknowledged)
    }

    @Test
    fun startIsIdempotent() = runTest {
        val r = repo()
        r.start()
        r.start()
        assertEquals(1, gateway.queryPurchasesCalls)
    }

    @Test
    fun failedQueryNeverTouchesTheCachedEntitlement() = runTest {
        val ads = newAds(Entitlement(foreverOwned = true, verifiedAtMs = 1_000L))
        gateway.purchasesResult = StoreResult.Failure(BillingFailure.NETWORK)
        repo(ads).refresh()
        assertEquals(0, ads.applyCalls)
        assertTrue(ads.adsRemoved.value)
    }

    @Test
    fun successfulEmptyQueryClearsEntitlement_refundOrExpiry() = runTest {
        val ads = newAds(Entitlement(foreverOwned = true, monthlyActive = true, verifiedAtMs = 1_000L))
        gateway.purchasesResult = StoreResult.Success(emptyList())
        repo(ads).refresh()
        assertFalse(ads.adsRemoved.value)
        assertEquals(Entitlement(verifiedAtMs = time.now), ads.entitlement.value)
    }

    // ---------------------------------------------------------------- purchase updates

    @Test
    fun userCanceledUpdateEmitsFailedEvent() = runTest {
        val r = repo()
        r.events.test {
            r.start()
            gateway.updates.emit(StoreResult.Failure(BillingFailure.USER_CANCELED))
            assertEquals(PurchaseEvent.Failed(BillingFailure.USER_CANCELED), awaitItem())
        }
    }

    @Test
    fun purchasedConsumableUpdateCreditsInventoryConsumesAndAnnounces() = runTest {
        val r = repo()
        r.events.test {
            r.start()
            gateway.updates.emit(StoreResult.Success(listOf(purchase("cat_pack_20", token = "u1"))))
            assertEquals(PurchaseEvent.PackGranted(ShopProduct.CAT_PACK_20), awaitItem())
        }
        assertEquals(20, grants.count(Booster.CAT))
        assertEquals(listOf("u1"), gateway.consumed)
    }

    @Test
    fun pendingUpdateGrantsNothing() = runTest {
        val r = repo()
        r.events.test {
            r.start()
            gateway.updates.emit(
                StoreResult.Success(listOf(purchase("hint_pack_5", status = PurchaseStatus.PENDING))),
            )
            assertEquals(PurchaseEvent.Pending(ShopProduct.HINT_PACK_5), awaitItem())
        }
        assertEquals(0, grants.grantCalls)
        assertTrue(gateway.consumed.isEmpty())
    }

    @Test
    fun foreverUpdateTurnsAdsOffImmediately() = runTest {
        val ads = newAds()
        val r = repo(ads)
        r.events.test {
            r.start()
            gateway.updates.emit(StoreResult.Success(listOf(purchase("remove_ads_forever", token = "f"))))
            assertEquals(PurchaseEvent.AdsRemovalActive(ShopProduct.REMOVE_ADS_FOREVER), awaitItem())
        }
        assertTrue(ads.adsRemoved.value)
    }

    @Test
    fun alreadyOwnedUpdateTriggersAResync() = runTest {
        val r = repo()
        r.start()
        val before = gateway.queryPurchasesCalls
        gateway.updates.emit(StoreResult.Failure(BillingFailure.ALREADY_OWNED))
        assertEquals(before + 1, gateway.queryPurchasesCalls)
    }

    // ---------------------------------------------------------------- buy

    @Test
    fun buyLaunchesTheSheet() = runTest {
        val r = repo()
        assertTrue(r.buy("cat_pack_5") is StoreResult.Success)
        assertEquals(listOf("cat_pack_5"), gateway.launched)
    }

    @Test
    fun buyRetriesOnceWhenServiceDisconnected() = runTest {
        gateway.launchResults.add(StoreResult.Failure(BillingFailure.SERVICE_DISCONNECTED))
        val r = repo()
        r.events.test {
            assertTrue(r.buy("cat_pack_5") is StoreResult.Success)
            expectNoEvents()
        }
        assertEquals(2, gateway.launched.size)
    }

    @Test
    fun buyFailureIsReportedOnEvents() = runTest {
        gateway.launchResults.add(StoreResult.Failure(BillingFailure.DEVELOPER_ERROR))
        val r = repo()
        r.events.test {
            val result = r.buy("hint_pack_5")
            assertEquals(StoreResult.Failure(BillingFailure.DEVELOPER_ERROR), result)
            assertEquals(PurchaseEvent.Failed(BillingFailure.DEVELOPER_ERROR), awaitItem())
        }
    }

    @Test
    fun persistentServiceDisconnectedSurfacesAfterOneRetry() = runTest {
        gateway.launchResults.add(StoreResult.Failure(BillingFailure.SERVICE_DISCONNECTED))
        gateway.launchResults.add(StoreResult.Failure(BillingFailure.SERVICE_DISCONNECTED))
        val r = repo()
        r.events.test {
            r.buy("cat_pack_5")
            assertEquals(PurchaseEvent.Failed(BillingFailure.SERVICE_DISCONNECTED), awaitItem())
        }
        assertEquals(2, gateway.launched.size)
    }

    @Test
    fun itemAlreadyOwnedFromPlayReportsAndResyncs() = runTest {
        gateway.launchResults.add(StoreResult.Failure(BillingFailure.ALREADY_OWNED))
        val r = repo()
        r.events.test {
            r.buy("remove_ads_forever")
            assertEquals(PurchaseEvent.Failed(BillingFailure.ALREADY_OWNED), awaitItem())
        }
        assertEquals(1, gateway.queryPurchasesCalls)
    }

    @Test
    fun buyingWhatIsAlreadyOwnedNeverOpensTheSheet() = runTest {
        val r = repo(newAds(Entitlement(foreverOwned = true, verifiedAtMs = 1_000L)))
        r.events.test {
            assertTrue(r.buy("remove_ads_forever") is StoreResult.Failure)
            assertTrue(r.buy("remove_ads_monthly") is StoreResult.Failure) // forever covers the monthly too
            assertEquals(PurchaseEvent.Failed(BillingFailure.ALREADY_OWNED), awaitItem())
            assertEquals(PurchaseEvent.Failed(BillingFailure.ALREADY_OWNED), awaitItem())
        }
        assertTrue(gateway.launched.isEmpty())
    }

    @Test
    fun unknownProductIsADeveloperError() = runTest {
        val r = repo()
        val result = r.buy("gold_pack_1")
        assertEquals(StoreResult.Failure(BillingFailure.DEVELOPER_ERROR), result)
        assertTrue(gateway.launched.isEmpty())
    }

    @Test
    fun consumablesCanBeBoughtRepeatedly() = runTest {
        val r = repo(newAds(Entitlement(foreverOwned = true, verifiedAtMs = 1_000L)))
        assertTrue(r.buy("cat_pack_5") is StoreResult.Success)
        assertTrue(r.buy("cat_pack_5") is StoreResult.Success)
        assertEquals(2, gateway.launched.size)
    }

    // ---------------------------------------------------------------- restore

    @Test
    fun restoreWithNothingSaysSo() = runTest {
        val r = repo()
        r.events.test {
            r.restore()
            assertEquals(PurchaseEvent.NothingToRestore, awaitItem())
        }
    }

    @Test
    fun restoreOfAnAlreadyAcknowledgedPurchaseReappliesEntitlement() = runTest {
        val ads = newAds()
        gateway.purchasesResult = StoreResult.Success(
            listOf(purchase("remove_ads_monthly", token = "m", acknowledged = true)),
        )
        val r = repo(ads)
        r.events.test {
            r.restore()
            assertEquals(PurchaseEvent.Restored, awaitItem())
        }
        assertTrue(ads.entitlement.value.monthlyActive)
        assertTrue(gateway.acknowledged.isEmpty())
    }

    @Test
    fun restoreAcknowledgesAnythingUnacknowledged() = runTest {
        gateway.purchasesResult = StoreResult.Success(listOf(purchase("remove_ads_forever", token = "f")))
        val r = repo()
        r.events.test {
            r.restore()
            assertEquals(PurchaseEvent.AdsRemovalActive(ShopProduct.REMOVE_ADS_FOREVER), awaitItem())
        }
        assertEquals(listOf("f"), gateway.acknowledged)
    }

    @Test
    fun restoreRecoversALeftoverConsumableOnce() = runTest {
        gateway.purchasesResult = StoreResult.Success(listOf(purchase("mouse_pack_5", token = "left")))
        val r = repo()
        r.restore()
        r.restore()
        assertEquals(5, grants.count(Booster.MOUSE))
        assertEquals(listOf("left", "left"), gateway.consumed)
    }

    @Test
    fun restoreFailureIsReported() = runTest {
        gateway.purchasesResult = StoreResult.Failure(BillingFailure.NETWORK)
        val r = repo()
        r.events.test {
            r.restore()
            assertEquals(PurchaseEvent.Failed(BillingFailure.NETWORK), awaitItem())
        }
    }
}
