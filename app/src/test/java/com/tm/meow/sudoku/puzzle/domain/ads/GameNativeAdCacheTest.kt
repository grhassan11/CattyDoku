package com.tm.meow.sudoku.puzzle.domain.ads

import com.tm.meow.sudoku.puzzle.domain.billing.AdsRemovedRepository
import com.tm.meow.sudoku.puzzle.domain.billing.Entitlement
import com.tm.meow.sudoku.puzzle.domain.usecase.AdPlacement
import com.tm.meow.sudoku.puzzle.domain.usecase.FakeTimeProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private class CacheHandle : NativeAdHandle {
    var destroyed = false
    override fun destroy() {
        destroyed = true
    }
}

private class ToggleAdsRemoved : AdsRemovedRepository {
    override val adsRemoved = MutableStateFlow(false)
    override val entitlement: StateFlow<Entitlement> = MutableStateFlow(Entitlement.NONE)
    override suspend fun apply(entitlement: Entitlement) = Unit
}

class GameNativeAdCacheTest {
    private val time = FakeTimeProvider(now = 1_000_000L)
    private val adsRemoved = ToggleAdsRemoved()
    private val cache = GameNativeAdCache(time, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), adsRemoved)
    private val levels = AdPlacement.LEVELS_GAME
    private val sudoku = AdPlacement.SUDOKU_GAME

    private var loads = 0
    private val made = mutableListOf<CacheHandle>()
    private suspend fun okLoad(): NativeAdHandle? {
        loads++
        return CacheHandle().also { made += it }
    }

    @Test
    fun takeHandsTheAdOverExactlyOnce() = runTest {
        cache.preload(levels) { okLoad() }
        val first = cache.take(levels)
        assertSame(made[0], first)
        assertNull("take-once", cache.take(levels))
        assertFalse("ownership moved to the caller, cache must not destroy it", made[0].destroyed)
    }

    @Test
    fun placementsAreIndependent() = runTest {
        cache.preload(levels) { okLoad() }
        assertNull(cache.take(sudoku))
        assertNotNull(cache.take(levels))
    }

    @Test
    fun freshEntryIsNotReloaded() = runTest {
        cache.preload(levels) { okLoad() }
        cache.preload(levels) { okLoad() }
        assertEquals(1, loads)
    }

    @Test
    fun expiredEntryIsDestroyedOnTake() = runTest {
        cache.preload(levels) { okLoad() }
        time.now += GameNativeAdCache.MAX_AD_AGE_MS
        assertNull(cache.take(levels))
        assertTrue(made[0].destroyed)
    }

    @Test
    fun expiredEntryIsDestroyedAndReplacedOnPreload() = runTest {
        cache.preload(levels) { okLoad() }
        time.now += GameNativeAdCache.MAX_AD_AGE_MS
        cache.preload(levels) { okLoad() }
        assertTrue(made[0].destroyed)
        assertEquals(2, loads)
        assertSame(made[1], cache.take(levels))
    }

    @Test
    fun entryJustUnderTheLimitIsStillServed() = runTest {
        cache.preload(levels) { okLoad() }
        time.now += GameNativeAdCache.MAX_AD_AGE_MS - 1
        assertSame(made[0], cache.take(levels))
    }

    @Test
    fun failedPreloadIsThrottledForAMinute() = runTest {
        var calls = 0
        cache.preload(levels) { calls++; null }
        cache.preload(levels) { calls++; null }
        assertEquals(1, calls)
        time.now += 61_000L
        cache.preload(levels) { calls++; null }
        assertEquals(2, calls)
    }

    @Test
    fun clearDestroysEveryHeldAd() = runTest {
        cache.preload(levels) { okLoad() }
        cache.preload(sudoku) { okLoad() }
        cache.clear()
        assertTrue(made.all { it.destroyed })
        assertNull(cache.take(levels))
        assertNull(cache.take(sudoku))
    }

    @Test
    fun removeAdsPurchaseDestroysHeldAds() = runTest {
        cache.preload(levels) { okLoad() }
        adsRemoved.adsRemoved.value = true
        assertTrue(made[0].destroyed)
        assertNull(cache.take(levels))
    }

    @Test
    fun adArrivingAfterClearIsDestroyed() = runTest {
        val gate = CompletableDeferred<Unit>()
        // NonCancellable: the SDK still delivers an ad after the preload job was cancelled.
        cache.preload(levels) { withContext(NonCancellable) { gate.await(); okLoad() } }
        cache.clear()
        gate.complete(Unit)
        assertEquals(1, made.size)
        assertTrue("late ad destroyed, never cached", made[0].destroyed)
        assertNull(cache.take(levels))
    }

    @Test
    fun preloadsAreSerialized() = runTest {
        val gate = CompletableDeferred<Unit>()
        var sudokuStarted = false
        cache.preload(levels) { gate.await(); okLoad() }
        cache.preload(sudoku) { sudokuStarted = true; okLoad() }
        assertFalse("second load waits for the first (shared native unit)", sudokuStarted)
        gate.complete(Unit)
        assertTrue(sudokuStarted)
        assertNotNull(cache.take(levels))
        assertNotNull(cache.take(sudoku))
    }

    @Test
    fun awaitPreloadReturnsOnceTheInFlightLoadSettles() = runTest {
        val gate = CompletableDeferred<Unit>()
        cache.preload(levels) { gate.await(); okLoad() }
        val waiter = launch { cache.awaitPreload(levels) }
        assertTrue(waiter.isActive)
        gate.complete(Unit)
        waiter.join()
        assertNotNull(cache.take(levels))
    }
}
