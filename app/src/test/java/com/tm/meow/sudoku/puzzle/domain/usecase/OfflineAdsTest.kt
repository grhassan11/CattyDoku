package com.tm.meow.sudoku.puzzle.domain.usecase

import com.tm.meow.sudoku.puzzle.domain.ads.AdDecision
import com.tm.meow.sudoku.puzzle.domain.ads.AdPolicyConfig
import com.tm.meow.sudoku.puzzle.domain.ads.AdSessionTracker
import com.tm.meow.sudoku.puzzle.domain.ads.AdSkipReason
import com.tm.meow.sudoku.puzzle.domain.ads.AdTrigger
import com.tm.meow.sudoku.puzzle.domain.ads.AdsSdkStatus
import com.tm.meow.sudoku.puzzle.domain.ads.AppOpenConfig
import com.tm.meow.sudoku.puzzle.domain.ads.AppOpenContext
import com.tm.meow.sudoku.puzzle.domain.ads.AppOpenDecision
import com.tm.meow.sudoku.puzzle.domain.ads.AppOpenPolicy
import com.tm.meow.sudoku.puzzle.domain.ads.AppOpenSkipReason
import com.tm.meow.sudoku.puzzle.domain.ads.InterstitialContext
import com.tm.meow.sudoku.puzzle.domain.ads.InterstitialPolicy
import com.tm.meow.sudoku.puzzle.domain.ads.NativeAdHandle
import com.tm.meow.sudoku.puzzle.domain.ads.NativeAdRepository
import com.tm.meow.sudoku.puzzle.domain.ads.RewardedAdRepository
import com.tm.meow.sudoku.puzzle.domain.ads.RewardedLoadState
import com.tm.meow.sudoku.puzzle.domain.ads.RewardedPlacement
import com.tm.meow.sudoku.puzzle.domain.ads.RewardedResult
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentRepository
import com.tm.meow.sudoku.puzzle.domain.consent.ConsentState
import com.tm.meow.sudoku.puzzle.domain.model.AppFlags
import com.tm.meow.sudoku.puzzle.domain.network.ConnectivityObserver
import com.tm.meow.sudoku.puzzle.ui.screens.game.FakeAppFlagsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Offline ads rule: policy skip reasons, native slot gate, rewarded CTA state, preload/show guards. */
class OfflineAdsTest {

    private class Net(online: Boolean = true) : ConnectivityObserver {
        val flow = MutableStateFlow(online)
        override val isOnline: StateFlow<Boolean> = flow
    }

    private class OffConsent : ConsentRepository {
        override val state: StateFlow<ConsentState> = MutableStateFlow(ConsentState(canRequestAds = true))
        override suspend fun gatherConsent() = Unit
        override suspend fun showPrivacyOptions() = Unit
    }

    private class OffSdk : AdsSdkStatus {
        override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true)
    }

    private class OffRewarded : RewardedAdRepository {
        override val isConfigured = true
        override val loadState: StateFlow<RewardedLoadState> = MutableStateFlow(RewardedLoadState.READY)
        var preloads = 0
        var shows = 0
        override fun preload() {
            preloads++
        }

        override suspend fun show(): RewardedResult {
            shows++
            return RewardedResult.EARNED
        }
    }

    private class OffNative : NativeAdRepository {
        override suspend fun load(): NativeAdHandle? = null
    }

    // ------------------------------------------------------------------ pure policies

    private fun interstitialCtx(online: Boolean) = InterstitialContext(
        trigger = AdTrigger.LEVEL_WON_CONTINUE, canRequestAds = true, level = 20, lastInterstitialLevel = 0, online = online,
    )

    @Test
    fun interstitialSkipsOfflineAndShowsOnline() {
        assertEquals(AdDecision.Skip(AdSkipReason.OFFLINE), InterstitialPolicy.decide(interstitialCtx(false), AdPolicyConfig()))
        assertEquals(AdDecision.Show, InterstitialPolicy.decide(interstitialCtx(true), AdPolicyConfig()))
    }

    @Test
    fun interstitialOfflineAppliesToSudokuAndRetryToo() {
        for (t in listOf(AdTrigger.SUDOKU_WON_CONTINUE, AdTrigger.SUDOKU_RESTART, AdTrigger.LOSE_RETRY)) {
            val ctx = interstitialCtx(false).copy(trigger = t)
            assertEquals("$t", AdDecision.Skip(AdSkipReason.OFFLINE), InterstitialPolicy.decide(ctx, AdPolicyConfig()))
        }
    }

    private fun appOpenCtx(online: Boolean) = AppOpenContext(
        coldStart = false, canRequestAds = true, sdkInitialized = true,
        fullScreenAdShowing = false, consentFormShowing = false, adAgeMs = 0L,
        online = online,
    )

    @Test
    fun appOpenSkipsOffline() {
        val cfg = AppOpenConfig()
        assertEquals(AppOpenDecision.Skip(AppOpenSkipReason.OFFLINE), AppOpenPolicy.decide(appOpenCtx(false), cfg))
        assertTrue(AppOpenPolicy.decide(appOpenCtx(true), cfg) != AppOpenDecision.Skip(AppOpenSkipReason.OFFLINE))
    }

    // ------------------------------------------------------------------ use cases

    @Test
    fun nativeSlotNotAllowedOfflineAndComesBack() = runTest {
        val net = Net(false)
        val uc = LoadNativeAd(OffConsent(), OffNative(), AdPolicyConfig(), connectivity = net)
        assertFalse(uc.isAllowed(AdPlacement.LEVELS_HOME))
        net.flow.value = true
        assertTrue(uc.isAllowed(AdPlacement.LEVELS_HOME))
    }

    @Test
    fun nativeAllowedChangesFollowsConnectivity() = runTest {
        val net = Net(true)
        val uc = LoadNativeAd(OffConsent(), OffNative(), AdPolicyConfig(), connectivity = net)
        assertTrue(uc.allowedChanges(AdPlacement.LEVELS_HOME).first())
        net.flow.value = false
        assertFalse(uc.allowedChanges(AdPlacement.LEVELS_HOME).first())
    }

    @Test
    fun surfacesOfflineKeepRewardedVisibleButMarkOfflineAndDropGameNative() = runTest {
        val net = Net(true)
        val surfaces = ObserveAdSurfaces(
            FakeAppFlagsRepository(AppFlags(lastPlayedLevel = 20)), OffConsent(), OffSdk(), OffRewarded(), OffNative(),
            AdPolicyConfig(), connectivity = net,
        )
        val on = surfaces(MutableStateFlow(20)).first()
        assertTrue(on.online && on.levelsGameNative && on.levelsRevive && on.levelsBoosters)
        assertEquals(RewardedLoadState.READY, on.rewardedLoad)

        net.flow.value = false
        val off = surfaces(MutableStateFlow(20)).first()
        assertFalse(off.online)
        assertFalse(off.levelsGameNative)
        assertFalse(off.sudokuGameNative)
        // Offline: no request, but the slot (skeleton) stays reserved.
        assertTrue(off.levelsGameNativeSlot && off.sudokuGameNativeSlot)
        // CTAs stay visible (surface flags unchanged); load state OFFLINE = UI shows the helper text and ignores the click.
        assertTrue(off.levelsRevive && off.levelsBoosters && off.sudokuRevive && off.sudokuBoosters)
        assertEquals(RewardedLoadState.OFFLINE, off.rewardedLoad)
    }

    @Test
    fun rewardedShowAndPreloadAreNeverAttemptedOffline() = runTest {
        val net = Net(false)
        val repo = OffRewarded()
        val tracker = AdSessionTracker(FakeTimeProvider())
        val show = ShowRewardedAd(OffConsent(), OffSdk(), repo, tracker, AdPolicyConfig(), connectivity = net)
        assertEquals(RewardedResult.NOT_READY, show(20, RewardedPlacement.LEVELS_REVIVE))
        PreloadRewardedAd(OffConsent(), OffSdk(), repo, AdPolicyConfig(), connectivity = net)(20)
        assertEquals(0, repo.shows)
        assertEquals(0, repo.preloads)

        net.flow.value = true
        PreloadRewardedAd(OffConsent(), OffSdk(), repo, AdPolicyConfig(), connectivity = net)(20)
        assertEquals(1, repo.preloads)
    }
}
