package com.tm.meow.sudoku.puzzle.domain.usecase

import com.tm.meow.sudoku.puzzle.domain.ads.AdPolicyConfig
import com.tm.meow.sudoku.puzzle.domain.model.AppFlags
import com.tm.meow.sudoku.puzzle.ui.screens.game.FakeAppFlagsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Remove Ads offers (Home badge, Store card) follow the same ad-free level rule as the ad formats. */
class ObserveAdsExposureTest {

    private suspend fun exposed(level: Int, config: AdPolicyConfig = AdPolicyConfig()): Boolean =
        ObserveAdsExposure(FakeAppFlagsRepository(AppFlags(lastPlayedLevel = level)), config)().first()

    @Test
    fun hiddenThroughLevelNineAndOnFreshInstall() = runTest {
        assertEquals(false, exposed(0))
        assertEquals(false, exposed(1))
        assertEquals(false, exposed(9))
    }

    @Test
    fun shownFromLevelTen() = runTest {
        assertEquals(true, exposed(10))
        assertEquals(true, exposed(42))
    }

    @Test
    fun followsRemoteConfigAdFreeThroughLevel() = runTest {
        val config = AdPolicyConfig(adFreeThroughLevel = 14)
        assertEquals(false, exposed(14, config))
        assertEquals(true, exposed(15, config))
    }

    @Test
    fun flipsWhenTheLevelAdvances() = runTest {
        val flags = FakeAppFlagsRepository(AppFlags(lastPlayedLevel = 9))
        val observe = ObserveAdsExposure(flags, AdPolicyConfig())
        assertEquals(false, observe().first())
        flags.update { it.copy(lastPlayedLevel = 10) }
        assertEquals(true, observe().first())
    }
}
