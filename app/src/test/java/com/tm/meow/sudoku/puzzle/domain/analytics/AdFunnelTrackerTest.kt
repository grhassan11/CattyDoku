package com.tm.meow.sudoku.puzzle.domain.analytics

import com.tm.meow.sudoku.puzzle.domain.crash.NoopCrashReporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AdFunnelTrackerTest {

    private class Recorder : AnalyticsLogger {
        val events = mutableListOf<AnalyticsEvent>()
        override fun log(event: AnalyticsEvent) { events += event }
        override fun applyConsent(a: Boolean, b: Boolean, c: Boolean, d: Boolean) = Unit
        override fun setUserProperty(name: String, value: String?) = Unit
    }

    private class KeyRecorder : com.tm.meow.sudoku.puzzle.domain.crash.CrashReporter {
        val keys = mutableMapOf<String, Any>()
        override fun setCollectionEnabled(enabled: Boolean) = Unit
        override fun log(message: String) = Unit
        override fun setCustomKey(key: String, value: String) { keys[key] = value }
        override fun setCustomKey(key: String, value: Int) { keys[key] = value }
        override fun setCustomKey(key: String, value: Boolean) { keys[key] = value }
        override fun recordNonFatal(throwable: Throwable, context: String?) = Unit
    }

    @Test
    fun nativeShownEmitsEventAndLastKeysButNotShowingKeys() {
        val r = Recorder()
        val k = KeyRecorder()
        AdFunnelTracker(r, k).shown(AdFormat.NATIVE, "unit")
        assertEquals("ad_shown", r.events.single().name)
        assertEquals("native", k.keys["last_ad_format"])
        assertEquals("shown", k.keys["last_ad_state"])
        assertFalse(k.keys.containsKey("ad_showing_format"))
        assertFalse(k.keys.containsKey("ad_showing_unit"))
        assertFalse(k.keys.containsKey("ad_showing_since"))
    }

    @Test
    fun fullScreenShownSetsShowingKeys() {
        val k = KeyRecorder()
        AdFunnelTracker(Recorder(), k).shown(AdFormat.INTERSTITIAL, "unit")
        assertEquals("interstitial", k.keys["ad_showing_format"])
        assertEquals("unit", k.keys["ad_showing_unit"])
    }

    @Test
    fun loadedCarriesLatencyAndFormat() {
        val r = Recorder()
        AdFunnelTracker(r, NoopCrashReporter).loaded(AdFormat.REWARDED, 420)
        val e = r.events.single()
        assertEquals("ad_loaded", e.name)
        assertEquals("rewarded", e.params["ad_format"])
        assertEquals(420L, e.params["ad_load_latency_ms"])
    }

    @Test
    fun failedCarriesErrorCodeOnlyAndNegativeLatencyClamped() {
        val r = Recorder()
        AdFunnelTracker(r).failed(AdFormat.NATIVE, "NO_FILL", -5)
        val e = r.events.single()
        assertEquals("ad_failed", e.name)
        assertEquals("NO_FILL", e.params["error_code"])
        assertEquals(0L, e.params["ad_load_latency_ms"])
        assertFalse(e.params.containsKey("message"))
    }

    @Test
    fun eventNamesAndParamKeysWithinFirebaseLimits() {
        val r = Recorder()
        val t = AdFunnelTracker(r)
        t.requested("app_open"); t.shown("app_open"); t.dismissed("app_open"); t.clicked("app_open")
        r.events.forEach { e ->
            assert(e.name.length <= 40)
            assert(e.params.size <= 25)
        }
        assertEquals(listOf("ad_requested", "ad_shown", "ad_dismissed", "ad_clicked"), r.events.map { it.name })
    }
}
