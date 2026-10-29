package com.threetech.catpuzzle.domain.usecase

import com.threetech.catpuzzle.domain.ads.AdPolicyConfig
import com.threetech.catpuzzle.domain.ads.AdSessionTracker
import com.threetech.catpuzzle.domain.ads.AdsSdkStatus
import com.threetech.catpuzzle.domain.ads.BannerAdHandle
import com.threetech.catpuzzle.domain.ads.BannerAdRepository
import com.threetech.catpuzzle.domain.ads.RewardedAdRepository
import com.threetech.catpuzzle.domain.ads.RewardedLoadState
import com.threetech.catpuzzle.domain.ads.RewardedResult
import com.threetech.catpuzzle.domain.consent.ConsentRepository
import com.threetech.catpuzzle.domain.consent.ConsentState
import com.threetech.catpuzzle.domain.model.AppFlags
import com.threetech.catpuzzle.domain.model.Booster
import com.threetech.catpuzzle.domain.model.GameState
import com.threetech.catpuzzle.domain.model.GameStatus
import com.threetech.catpuzzle.ui.screens.game.FakeAppFlagsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class GaConsent(allow: Boolean = true) : ConsentRepository {
    private val _state = MutableStateFlow(ConsentState(canRequestAds = allow))
    override val state: StateFlow<ConsentState> = _state

    fun allow(value: Boolean) {
        _state.value = ConsentState(canRequestAds = value)
    }

    override suspend fun gatherConsent() = Unit

    override suspend fun showPrivacyOptions() = Unit
}

private class GaSdk(initialized: Boolean = true) : AdsSdkStatus {
    private val _init = MutableStateFlow(initialized)
    override val isInitialized: StateFlow<Boolean> = _init

    fun set(value: Boolean) {
        _init.value = value
    }
}

private class GaRewarded(
    override val isConfigured: Boolean = true,
    var next: RewardedResult = RewardedResult.EARNED,
    private val onShow: (() -> Unit)? = null,
    private val throwOnShow: Boolean = false,
) : RewardedAdRepository {
    private val _load = MutableStateFlow(RewardedLoadState.IDLE)
    override val loadState: StateFlow<RewardedLoadState> = _load
    var preloadCalls = 0
    var showCalls = 0

    fun setLoad(state: RewardedLoadState) {
        _load.value = state
    }

    override fun preload() {
        preloadCalls++
    }

    override suspend fun show(): RewardedResult {
        showCalls++
        onShow?.invoke()
        if (throwOnShow) error("sdk boom")
        return next
    }
}

private class GaBannerHandle : BannerAdHandle {
    override fun destroy() = Unit
}

private class GaBanner(
    override val isConfigured: Boolean = true,
    private val result: BannerAdHandle? = GaBannerHandle(),
) : BannerAdRepository {
    var loadCalls = 0
    var lastWidth = -1

    override suspend fun load(widthDp: Int): BannerAdHandle? {
        loadCalls++
        lastWidth = widthDp
        return result
    }
}

class GameAdUseCasesTest {

    // ---------------------------------------------------------------- ObserveAdSurfaces

    private fun surfaces(
        flagLevel: Int = 0,
        consent: GaConsent = GaConsent(),
        sdk: GaSdk = GaSdk(),
        rewarded: GaRewarded = GaRewarded(),
        banner: GaBanner = GaBanner(),
        config: AdPolicyConfig = AdPolicyConfig(),
    ) = ObserveAdSurfaces(
        FakeAppFlagsRepository(AppFlags(lastPlayedLevel = flagLevel)), consent, sdk, rewarded, banner, config,
    )

    @Test
    fun surfacesOffThroughLevelNineOnFromTen() = runTest {
        for (l in 0..9) {
            val s = surfaces(flagLevel = l)(MutableStateFlow(l)).first()
            assertFalse("rewarded L$l", s.rewarded)
            assertFalse("banner L$l", s.banner)
        }
        val s = surfaces(flagLevel = 10)(MutableStateFlow(10)).first()
        assertTrue(s.rewarded)
        assertTrue(s.banner)
        assertEquals(10, s.playerLevel)
    }

    @Test
    fun surfaceLevelIsMaxOfScreenAndProgress() = runTest {
        // Daily: screen level 0, progress says 12 -> ads allowed.
        assertTrue(surfaces(flagLevel = 12)(MutableStateFlow(0)).first().banner)
        // Progress flag not yet written for the new level but screen knows it.
        assertTrue(surfaces(flagLevel = 9)(MutableStateFlow(10)).first().banner)
        assertFalse(surfaces(flagLevel = 4)(MutableStateFlow(8)).first().banner)
    }

    @Test
    fun surfacesNeedConsentSdkConfigAndSwitches() = runTest {
        val level = MutableStateFlow(20)
        assertFalse(surfaces(20, consent = GaConsent(false))(level).first().rewarded)
        assertFalse(surfaces(20, sdk = GaSdk(false))(level).first().banner)
        assertFalse(surfaces(20, rewarded = GaRewarded(isConfigured = false))(level).first().rewarded)
        assertTrue(surfaces(20, rewarded = GaRewarded(isConfigured = false))(level).first().banner)
        assertFalse(surfaces(20, banner = GaBanner(isConfigured = false))(level).first().banner)
        assertFalse(surfaces(20, config = AdPolicyConfig(enabled = false))(level).first().rewarded)
        assertFalse(surfaces(20, config = AdPolicyConfig(bannerGameEnabled = false))(level).first().banner)
        assertTrue(surfaces(20, config = AdPolicyConfig(bannerGameEnabled = false))(level).first().rewarded)
        assertFalse(surfaces(20, config = AdPolicyConfig(rewardedEnabled = false))(level).first().rewarded)
    }

    @Test
    fun surfacesReactToConsentRevokeAndLoadState() = runTest {
        val consent = GaConsent(true)
        val rewarded = GaRewarded()
        val uc = surfaces(20, consent = consent, rewarded = rewarded)
        val level = MutableStateFlow(20)
        assertEquals(RewardedLoadState.IDLE, uc(level).first().rewardedLoad)
        rewarded.setLoad(RewardedLoadState.READY)
        assertEquals(RewardedLoadState.READY, uc(level).first().rewardedLoad)
        consent.allow(false)
        assertFalse(uc(level).first().rewarded)
    }

    // ---------------------------------------------------------------- PreloadRewardedAd

    @Test
    fun preloadOnlyWhenPolicyAllows() {
        val repo = GaRewarded()
        val uc = PreloadRewardedAd(GaConsent(), GaSdk(), repo, AdPolicyConfig())
        uc(playerLevel = 9)
        assertEquals(0, repo.preloadCalls)
        uc(playerLevel = 10)
        assertEquals(1, repo.preloadCalls)
    }

    @Test
    fun preloadSkippedWithoutConsentSdkOrSwitch() {
        val repo = GaRewarded()
        PreloadRewardedAd(GaConsent(false), GaSdk(), repo, AdPolicyConfig())(30)
        PreloadRewardedAd(GaConsent(), GaSdk(false), repo, AdPolicyConfig())(30)
        PreloadRewardedAd(GaConsent(), GaSdk(), repo, AdPolicyConfig(rewardedEnabled = false))(30)
        PreloadRewardedAd(GaConsent(), GaSdk(), GaRewarded(isConfigured = false).also { it.preloadCalls = 0 }, AdPolicyConfig())(30)
        assertEquals(0, repo.preloadCalls)
    }

    // ---------------------------------------------------------------- ShowRewardedAd

    private class ShowRig(
        result: RewardedResult = RewardedResult.EARNED,
        consent: GaConsent = GaConsent(),
        sdk: GaSdk = GaSdk(),
        config: AdPolicyConfig = AdPolicyConfig(),
        throwOnShow: Boolean = false,
    ) {
        val time = FakeTimeProvider(now = 1_000_000L)
        val tracker = AdSessionTracker(time)
        var showingDuringShow: Boolean? = null
        val repo = GaRewarded(next = result, onShow = { showingDuringShow = tracker.fullScreenAdShowing }, throwOnShow = throwOnShow)
        val show = ShowRewardedAd(consent, sdk, repo, tracker, config)
    }

    @Test
    fun earnedRecordsSharedFullScreenTimeButNotInterstitialCounters() = runTest {
        val r = ShowRig(RewardedResult.EARNED)
        r.tracker.recordWin()
        r.time.now += 5_000L
        assertEquals(RewardedResult.EARNED, r.show(playerLevel = 15))
        assertEquals(r.time.now, r.tracker.lastAdShownMs)
        assertEquals("wins untouched", 1, r.tracker.winsSinceLastAd)
        assertEquals("interstitial cap untouched", 0, r.tracker.adsShown)
        assertEquals("app-open count untouched", 0, r.tracker.appOpenShown)
    }

    @Test
    fun dismissedWithoutRewardIsReportedAndStillStartsCooldown() = runTest {
        val r = ShowRig(RewardedResult.DISMISSED)
        assertEquals(RewardedResult.DISMISSED, r.show(15))
        assertNotNull(r.tracker.lastAdShownMs)
    }

    @Test
    fun notReadyAndFailedDoNotStartCooldown() = runTest {
        for (res in listOf(RewardedResult.NOT_READY, RewardedResult.FAILED)) {
            val r = ShowRig(res)
            assertEquals(res, r.show(15))
            assertNull("$res", r.tracker.lastAdShownMs)
        }
    }

    @Test
    fun fullScreenFlagIsSetWhileShowingThenCleared() = runTest {
        val r = ShowRig()
        r.show(15)
        assertEquals(true, r.showingDuringShow)
        assertFalse(r.tracker.fullScreenAdShowing)
    }

    @Test
    fun adFreeLevelsNeverTouchTheRepository() = runTest {
        for (l in 0..9) {
            val r = ShowRig()
            assertEquals("L$l", RewardedResult.NOT_READY, r.show(l))
            assertEquals("L$l", 0, r.repo.showCalls)
            assertNull(r.tracker.lastAdShownMs)
        }
    }

    @Test
    fun noConsentSdkOffOrKillSwitchIsNotReady() = runTest {
        assertEquals(RewardedResult.NOT_READY, ShowRig(consent = GaConsent(false)).show(20))
        assertEquals(RewardedResult.NOT_READY, ShowRig(sdk = GaSdk(false)).show(20))
        assertEquals(RewardedResult.NOT_READY, ShowRig(config = AdPolicyConfig(enabled = false)).show(20))
        assertEquals(RewardedResult.NOT_READY, ShowRig(config = AdPolicyConfig(rewardedEnabled = false)).show(20))
    }

    @Test
    fun repositoryThrowingFailsOpenAndClearsFlag() = runTest {
        val r = ShowRig(throwOnShow = true)
        assertEquals(RewardedResult.FAILED, r.show(20))
        assertFalse(r.tracker.fullScreenAdShowing)
    }

    // ---------------------------------------------------------------- GrantBoosterReward / ReviveGame

    @Test
    fun grantAddsOneToInventory() = runTest {
        val inv = FakeInventoryRepository()
        val grant = GrantBoosterReward(inv)
        grant(Booster.CAT)
        grant(Booster.CAT)
        grant(Booster.HINT)
        grant(Booster.MOUSE)
        assertEquals(2, inv.get(Booster.CAT))
        assertEquals(1, inv.get(Booster.HINT))
        assertEquals(1, inv.get(Booster.MOUSE))
        grant(Booster.CAT, amount = 0)
        assertEquals(2, inv.get(Booster.CAT))
    }

    @Test
    fun reviveOnlyFromLostAndGivesOneHeart() {
        val lost = GameState.initial(4).copy(status = GameStatus.LOST, hearts = 0, hintsUsed = 2)
        val revived = ReviveGame()(lost)
        assertNotNull(revived)
        assertEquals(GameStatus.PLAYING, revived!!.status)
        assertEquals(1, revived.hearts)
        assertEquals("rest of the state kept", 2, revived.hintsUsed)
        assertNull(ReviveGame()(GameState.initial(4)))
        assertNull(ReviveGame()(GameState.initial(4).copy(status = GameStatus.SOLVED)))
    }

    // ---------------------------------------------------------------- LoadBannerAd

    @Test
    fun bannerLoadsOnlyPastTheGateWithConsent() = runTest {
        val repo = GaBanner()
        val uc = LoadBannerAd(GaConsent(), GaSdk(), repo, AdPolicyConfig())
        assertNull(uc(playerLevel = 9, widthDp = 360))
        assertEquals(0, repo.loadCalls)
        assertNotNull(uc(playerLevel = 10, widthDp = 360))
        assertEquals(360, repo.lastWidth)
        assertNull(LoadBannerAd(GaConsent(false), GaSdk(), repo, AdPolicyConfig())(30, 360))
        assertNull(LoadBannerAd(GaConsent(), GaSdk(false), repo, AdPolicyConfig())(30, 360))
        assertNull(LoadBannerAd(GaConsent(), GaSdk(), repo, AdPolicyConfig(bannerGameEnabled = false))(30, 360))
        assertNull(uc(30, 0))
        assertEquals(1, repo.loadCalls)
    }

    // ---------------------------------------------------------------- starter pack stays cat + hint only

    @Test
    fun starterPackDoesNotIncludeMouse() = runTest {
        val inv = FakeInventoryRepository()
        SeedStarterBoosters(FakeAppFlagsRepository(), inv, FakeTimeProvider())()
        assertEquals(SeedStarterBoosters.STARTER_AMOUNT, inv.get(Booster.CAT))
        assertEquals(SeedStarterBoosters.STARTER_AMOUNT, inv.get(Booster.HINT))
        assertEquals(SeedStarterBoosters.STARTER_AMOUNT, inv.get(Booster.MOUSE))
    }
}
