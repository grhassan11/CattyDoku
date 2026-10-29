package com.threetech.catpuzzle.domain.analytics

/**
 * Analytics boundary. Impl (firebase/) never throws; callers never check for Firebase. Collection is OFF until
 * [applyConsent] allows it (manifest default is off too).
 */
interface AnalyticsLogger {
    fun log(event: AnalyticsEvent)

    /** [analyticsAllowed] gates collection + analytics storage; [adsAllowed] sets the ad-related consent signals. */
    fun applyConsent(analyticsAllowed: Boolean, adsAllowed: Boolean)
}

/** Used when Firebase is not configured (no google-services.json) and as the default in tests. */
object NoopAnalyticsLogger : AnalyticsLogger {
    override fun log(event: AnalyticsEvent) = Unit
    override fun applyConsent(analyticsAllowed: Boolean, adsAllowed: Boolean) = Unit
}
