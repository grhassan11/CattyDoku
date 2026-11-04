package com.tm.meow.sudoku.puzzle.domain.consent

import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsEvent
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsLogger
import com.tm.meow.sudoku.puzzle.domain.crash.CrashReporter
import com.tm.meow.sudoku.puzzle.domain.usecase.ApplyTelemetryConsent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryConsentPolicyTest {

    @Test
    fun noAdConsentMeansNoAnalytics() {
        assertFalse(TelemetryConsentPolicy.analyticsAllowed(false, null))
        assertFalse(TelemetryConsentPolicy.analyticsAllowed(false, "1111111111"))
    }

    @Test
    fun noTcfStringFollowsCanRequestAds() {
        assertTrue(TelemetryConsentPolicy.analyticsAllowed(true, null))
        assertTrue(TelemetryConsentPolicy.analyticsAllowed(true, ""))
        assertTrue(TelemetryConsentPolicy.analyticsAllowed(true, "   "))
    }

    @Test
    fun tcfNeedsPurposeOneAndSeven() {
        assertTrue(TelemetryConsentPolicy.analyticsAllowed(true, "1000001000"))
        assertFalse(TelemetryConsentPolicy.analyticsAllowed(true, "0000001000")) // purpose 1 missing
        assertFalse(TelemetryConsentPolicy.analyticsAllowed(true, "1000000000")) // purpose 7 missing
        assertFalse(TelemetryConsentPolicy.analyticsAllowed(true, "100000")) // too short for purpose 7
    }

    @Test
    fun defaultConsentStateFailsClosed() {
        val c = TelemetryConsentPolicy.from(ConsentState())
        assertEquals(TelemetryConsent(false, false, false, false), c)
    }

    @Test
    fun fromStateCopiesAllSignals() {
        val c = TelemetryConsentPolicy.from(
            ConsentState(canRequestAds = true, analyticsAllowed = false, adStorage = true, adUserData = false, adPersonalization = true),
        )
        assertEquals(TelemetryConsent(analytics = false, adStorage = true, adUserData = false, adPersonalization = true), c)
    }

    @Test
    fun adSignalsArePurposeAware() {
        // purposes 1,3,4,7 granted
        val all = "1011001000"
        assertTrue(TelemetryConsentPolicy.adStorageAllowed(true, all))
        assertTrue(TelemetryConsentPolicy.adUserDataAllowed(true, all))
        assertTrue(TelemetryConsentPolicy.adPersonalizationAllowed(true, all))
        // only 1 + 7: no personalization
        val p17 = "1000001000"
        assertTrue(TelemetryConsentPolicy.adStorageAllowed(true, p17))
        assertTrue(TelemetryConsentPolicy.adUserDataAllowed(true, p17))
        assertFalse(TelemetryConsentPolicy.adPersonalizationAllowed(true, p17))
        // only 1: storage only
        val p1 = "1000000000"
        assertTrue(TelemetryConsentPolicy.adStorageAllowed(true, p1))
        assertFalse(TelemetryConsentPolicy.adUserDataAllowed(true, p1))
        // none
        assertFalse(TelemetryConsentPolicy.adStorageAllowed(true, "0000000000"))
        // no TCF string follows canRequestAds
        assertTrue(TelemetryConsentPolicy.adPersonalizationAllowed(true, null))
        assertFalse(TelemetryConsentPolicy.adStorageAllowed(false, all))
    }

    private class FakeAnalytics : AnalyticsLogger {
        var lastAnalytics: Boolean? = null
        var lastAdStorage: Boolean? = null
        override fun log(event: AnalyticsEvent) = Unit
        override fun applyConsent(
            analyticsAllowed: Boolean,
            adStorageAllowed: Boolean,
            adUserDataAllowed: Boolean,
            adPersonalizationAllowed: Boolean,
        ) {
            lastAnalytics = analyticsAllowed
            lastAdStorage = adStorageAllowed
        }
        override fun setUserProperty(name: String, value: String?) = Unit
    }

    private class FakeCrash : CrashReporter {
        var enabled: Boolean? = null
        override fun setCollectionEnabled(enabled: Boolean) { this.enabled = enabled }
        override fun log(message: String) = Unit
        override fun setCustomKey(key: String, value: String) = Unit
        override fun setCustomKey(key: String, value: Int) = Unit
        override fun setCustomKey(key: String, value: Boolean) = Unit
        override fun recordNonFatal(throwable: Throwable, context: String?) = Unit
    }

    @Test
    fun applyPushesGateToAnalyticsAndCrash() {
        val a = FakeAnalytics()
        val c = FakeCrash()
        val apply = ApplyTelemetryConsent(a, c)

        apply(ConsentState())
        assertEquals(false, a.lastAnalytics)
        assertEquals(false, c.enabled)

        apply(ConsentState(canRequestAds = true, analyticsAllowed = true, adStorage = true))
        assertEquals(true, a.lastAnalytics)
        assertEquals(true, a.lastAdStorage)
        assertEquals(true, c.enabled)

        apply(ConsentState(canRequestAds = true, analyticsAllowed = false, adStorage = true))
        assertEquals(false, a.lastAnalytics)
        assertEquals(true, a.lastAdStorage)
        assertEquals(false, c.enabled)
    }

    @Test
    fun usRegulatedWithoutTcfDeniesAdSignalsButKeepsAnalytics() {
        assertTrue(TelemetryConsentPolicy.usRegulatedNoTcf(true, null))
        assertTrue(TelemetryConsentPolicy.usRegulatedNoTcf(true, "  "))
        assertFalse(TelemetryConsentPolicy.adStorageAllowed(true, null, usRegulatedNoTcf = true))
        assertFalse(TelemetryConsentPolicy.adUserDataAllowed(true, null, usRegulatedNoTcf = true))
        assertFalse(TelemetryConsentPolicy.adPersonalizationAllowed(true, null, usRegulatedNoTcf = true))
        assertTrue(TelemetryConsentPolicy.analyticsAllowed(true, null))
    }

    @Test
    fun usRegulatedFlagOnlyWhenOptionsRequiredAndNoTcf() {
        assertFalse(TelemetryConsentPolicy.usRegulatedNoTcf(false, null)) // non-regulated: stays granted
        assertFalse(TelemetryConsentPolicy.usRegulatedNoTcf(true, "1111111111")) // EEA/UK: TCF path decides
        assertTrue(TelemetryConsentPolicy.adStorageAllowed(true, null, usRegulatedNoTcf = false))
        assertTrue(TelemetryConsentPolicy.adUserDataAllowed(true, null, usRegulatedNoTcf = false))
        assertTrue(TelemetryConsentPolicy.adPersonalizationAllowed(true, null, usRegulatedNoTcf = false))
    }

    @Test
    fun refreshRemoteConfigIsGated() {
        val repo = object : com.tm.meow.sudoku.puzzle.domain.config.RemoteConfigRepository {
            var n = 0
            override fun current() = com.tm.meow.sudoku.puzzle.domain.config.AppRemoteConfig()
            override fun refresh() { n++ }
        }
        com.tm.meow.sudoku.puzzle.domain.usecase.RefreshRemoteConfig(repo) { false }()
        assertEquals(0, repo.n)
        com.tm.meow.sudoku.puzzle.domain.usecase.RefreshRemoteConfig(repo) { true }()
        assertEquals(1, repo.n)
        com.tm.meow.sudoku.puzzle.domain.usecase.RefreshRemoteConfig(repo)()
        assertEquals(2, repo.n)
    }
}
