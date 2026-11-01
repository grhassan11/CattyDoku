package com.threetech.catpuzzle.domain.billing

import com.threetech.catpuzzle.domain.model.Booster
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseProcessorTest {
    private val gateway = FakeBillingGateway()
    private val grants = FakePurchaseGrants()
    private val processor = PurchaseProcessor(gateway, grants)

    @Test
    fun consumableIsGrantedThenConsumed() = runTest {
        val out = processor.process(listOf(purchase("cat_pack_20", token = "t1")))
        assertEquals(listOf<PurchaseOutcome>(PurchaseOutcome.PackGranted(ShopProduct.CAT_PACK_20)), out)
        assertEquals(20, grants.count(Booster.CAT))
        assertEquals(listOf("t1"), gateway.consumed)
        assertTrue(gateway.acknowledged.isEmpty())
    }

    @Test
    fun eachPackCreditsItsOwnBoosterAndAmount() = runTest {
        processor.process(
            listOf(
                purchase("cat_pack_5", "a"),
                purchase("hint_pack_50", "b"),
                purchase("mouse_pack_20", "c"),
            ),
        )
        assertEquals(5, grants.count(Booster.CAT))
        assertEquals(50, grants.count(Booster.HINT))
        assertEquals(20, grants.count(Booster.MOUSE))
    }

    @Test
    fun sameTokenIsNeverGrantedTwice() = runTest {
        val p = purchase("cat_pack_5", token = "dup")
        processor.process(listOf(p))
        val second = processor.process(listOf(p))
        assertEquals(5, grants.count(Booster.CAT))
        assertTrue("already granted: nothing to announce", second.isEmpty())
    }

    @Test
    fun consumeFailureAfterGrantDoesNotDoubleGrantOnRetry() = runTest {
        // Crash / failure between grant and consume: the purchase stays unconsumed on Play.
        gateway.consumeResult = StoreResult.Failure(BillingFailure.SERVICE_DISCONNECTED)
        val p = purchase("hint_pack_20", token = "crash")
        val first = processor.process(listOf(p))
        assertEquals(1, first.size) // credit happened and is announced
        assertEquals(20, grants.count(Booster.HINT))

        // Next sync finds the same unconsumed purchase: no second credit, consume is retried and now works.
        gateway.consumeResult = StoreResult.Success(Unit)
        val second = processor.process(listOf(p))
        assertTrue(second.isEmpty())
        assertEquals(20, grants.count(Booster.HINT))
        assertEquals(listOf("crash", "crash"), gateway.consumed)
    }

    @Test
    fun grantHappensBeforeConsume() = runTest {
        var creditedWhenConsumed = -1
        val spy = object : BillingGateway by gateway {
            override suspend fun consume(purchaseToken: String): StoreResult<Unit> {
                creditedWhenConsumed = grants.count(Booster.CAT)
                return StoreResult.Success(Unit)
            }
        }
        PurchaseProcessor(spy, grants).process(listOf(purchase("cat_pack_5", "t")))
        assertEquals(5, creditedWhenConsumed)
    }

    @Test
    fun pendingPurchaseGrantsAndConsumesNothing() = runTest {
        val out = processor.process(listOf(purchase("cat_pack_50", status = PurchaseStatus.PENDING)))
        assertEquals(listOf<PurchaseOutcome>(PurchaseOutcome.Pending(ShopProduct.CAT_PACK_50)), out)
        assertEquals(0, grants.grantCalls)
        assertTrue(gateway.consumed.isEmpty())
        assertTrue(gateway.acknowledged.isEmpty())
    }

    @Test
    fun pendingIsAnnouncedOncePerTokenThenAgainOnlyForANewToken() = runTest {
        val p = purchase("cat_pack_50", token = "pend", status = PurchaseStatus.PENDING)
        assertEquals(1, processor.process(listOf(p)).size)
        assertTrue("repeat sync stays silent", processor.process(listOf(p)).isEmpty())
        val other = purchase("hint_pack_5", token = "pend2", status = PurchaseStatus.PENDING)
        assertEquals(1, processor.process(listOf(p, other)).size)
    }

    @Test
    fun pendingResolvedToPurchasedIsGrantedAndForgotten() = runTest {
        processor.process(listOf(purchase("cat_pack_5", token = "x", status = PurchaseStatus.PENDING)))
        val out = processor.process(listOf(purchase("cat_pack_5", token = "x")))
        assertEquals(listOf<PurchaseOutcome>(PurchaseOutcome.PackGranted(ShopProduct.CAT_PACK_5)), out)
        assertEquals(5, grants.count(Booster.CAT))
    }

    @Test
    fun unspecifiedStateIsIgnored() = runTest {
        val out = processor.process(listOf(purchase("cat_pack_5", status = PurchaseStatus.UNSPECIFIED)))
        assertTrue(out.isEmpty())
        assertEquals(0, grants.grantCalls)
    }

    @Test
    fun unacknowledgedForeverIsAcknowledgedNotConsumed() = runTest {
        val out = processor.process(listOf(purchase("remove_ads_forever", token = "f1", acknowledged = false)))
        assertEquals(listOf<PurchaseOutcome>(PurchaseOutcome.AdsRemovalActivated(ShopProduct.REMOVE_ADS_FOREVER)), out)
        assertEquals(listOf("f1"), gateway.acknowledged)
        assertTrue(gateway.consumed.isEmpty())
        assertEquals(0, grants.grantCalls)
    }

    @Test
    fun unacknowledgedSubscriptionIsAcknowledged() = runTest {
        processor.process(listOf(purchase("remove_ads_monthly", token = "s1")))
        assertEquals(listOf("s1"), gateway.acknowledged)
    }

    @Test
    fun alreadyAcknowledgedEntitlementIsSilent() = runTest {
        val out = processor.process(listOf(purchase("remove_ads_forever", acknowledged = true)))
        assertTrue(out.isEmpty())
        assertTrue(gateway.acknowledged.isEmpty())
    }

    @Test
    fun failedAcknowledgeStillAnnouncesAndIsRetriedByNextSync() = runTest {
        gateway.acknowledgeResult = StoreResult.Failure(BillingFailure.NETWORK)
        val p = purchase("remove_ads_forever", token = "f")
        assertEquals(1, processor.process(listOf(p)).size)
        assertEquals(1, processor.process(listOf(p)).size) // still unacknowledged on Play
        assertEquals(listOf("f", "f"), gateway.acknowledged)
    }

    @Test
    fun unknownProductsAreIgnored() = runTest {
        val out = processor.process(listOf(purchase("gold_pack_9")))
        assertTrue(out.isEmpty())
        assertTrue(gateway.consumed.isEmpty())
        assertTrue(gateway.acknowledged.isEmpty())
    }
}
