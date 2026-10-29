package com.threetech.catpuzzle.domain.ads

import com.threetech.catpuzzle.domain.billing.Entitlement
import com.threetech.catpuzzle.domain.billing.FakeAdsRemovedRepository
import com.threetech.catpuzzle.domain.consent.ConsentRepository
import com.threetech.catpuzzle.domain.consent.ConsentState
import com.threetech.catpuzzle.domain.usecase.LoadNativeAd
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home native slot: which states reserve space, and the single "ads allowed" gate that maps to Hidden vs Loading. */
class NativeAdSlotStateTest {

    private class Handle : NativeAdHandle {
        override fun destroy() = Unit
    }

    private class Repo(override val isConfigured: Boolean = true) : NativeAdRepository {
        var loads = 0
        override suspend fun load(): NativeAdHandle? {
            loads++
            return Handle()
        }
    }

    private class Consent(allow: Boolean = true) : ConsentRepository {
        override val state: StateFlow<ConsentState> = MutableStateFlow(ConsentState(canRequestAds = allow))
        override suspend fun gatherConsent() = Unit
        override suspend fun showPrivacyOptions() = Unit
    }

    private fun removed(on: Boolean) =
        FakeAdsRemovedRepository(if (on) Entitlement(foreverOwned = true, verifiedAtMs = 1L) else Entitlement.NONE) { 1L }

    private fun useCase(
        repo: NativeAdRepository = Repo(),
        consent: Boolean = true,
        config: AdPolicyConfig = AdPolicyConfig(),
        adsRemoved: Boolean = false,
    ) = LoadNativeAd(Consent(consent), repo, config, removed(adsRemoved))

    @Test
    fun onlyLoadingAndLoadedReserveTheSlot() {
        assertTrue(NativeAdSlotState.Loading.reservesSlot)
        assertTrue(NativeAdSlotState.Loaded(Handle()).reservesSlot)
        assertFalse(NativeAdSlotState.Hidden.reservesSlot)
        assertFalse(NativeAdSlotState.Failed.reservesSlot)
    }

    @Test
    fun allowedWhenEverythingIsOn() {
        assertTrue(useCase().isAllowed())
    }

    @Test
    fun hiddenWhenConsentMissing() {
        assertFalse(useCase(consent = false).isAllowed())
    }

    @Test
    fun hiddenWhenAdsRemoved() {
        assertFalse(useCase(adsRemoved = true).isAllowed())
    }

    @Test
    fun hiddenWhenKillSwitchOff() {
        assertFalse(useCase(config = AdPolicyConfig(nativeHomeEnabled = false)).isAllowed())
        assertFalse(useCase(config = AdPolicyConfig(enabled = false)).isAllowed())
    }

    @Test
    fun hiddenWhenSdkNotConfigured() = runTest {
        val repo = Repo(isConfigured = false)
        val loader = useCase(repo = repo)
        assertFalse(loader.isAllowed())
        assertNull(loader())
        assertTrue("no request when not allowed", repo.loads == 0)
    }
}
