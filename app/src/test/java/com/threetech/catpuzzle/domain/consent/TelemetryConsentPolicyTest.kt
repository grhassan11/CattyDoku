package com.threetech.catpuzzle.domain.consent

import com.threetech.catpuzzle.domain.analytics.AnalyticsEvent
import com.threetech.catpuzzle.domain.analytics.AnalyticsLogger
import com.threetech.catpuzzle.domain.crash.CrashReporter
import com.threetech.catpuzzle.domain.usecase.ApplyTelemetryConsent
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
        assertEquals(TelemetryConsent(analytics = false, ads = false), c)
    }

    @Test
    fun fromStateCopiesBothSignals() {
        val c = TelemetryConsentPolicy.from(ConsentState(canRequestAds = true, analyticsAllowed = false))
        assertEquals(TelemetryConsent(analytics = false, ads = true), c)
    }

    private class FakeAnalytics : AnalyticsLogger {
        var lastAnalytics: Boolean? = null
        var lastAds: Boolean? = null
        override fun log(event: AnalyticsEvent) = Unit
        override fun applyConsent(analyticsAllowed: Boolean, adsAllowed: Boolean) {
            lastAnalytics = analyticsAllowed
            lastAds = adsAllowed
        }
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

        apply(ConsentState(canRequestAds = true, analyticsAllowed = true))
        assertEquals(true, a.lastAnalytics)
        assertEquals(true, a.lastAds)
        assertEquals(true, c.enabled)

        apply(ConsentState(canRequestAds = true, analyticsAllowed = false))
        assertEquals(false, a.lastAnalytics)
        assertEquals(true, a.lastAds)
        assertEquals(false, c.enabled)
    }
}
