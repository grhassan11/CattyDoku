package com.tm.meow.sudoku.puzzle.domain.analytics

/**
 * Analytics boundary. Impl (firebase/) never throws; callers never check for Firebase. Collection is OFF until
 * [applyConsent] allows it (manifest default is off too).
 */
interface AnalyticsLogger {
    fun log(event: AnalyticsEvent)

    /** [analyticsAllowed] gates collection + analytics_storage; the rest map 1:1 to GA4 ad consent types. */
    fun applyConsent(
        analyticsAllowed: Boolean,
        adStorageAllowed: Boolean,
        adUserDataAllowed: Boolean,
        adPersonalizationAllowed: Boolean,
    )

    /** GA4 user property. [name] <= 24 chars, [value] <= 36 chars (see [AnalyticsUserProps]); null clears it. */
    fun setUserProperty(name: String, value: String?)
}

/** Used when Firebase is not configured (no google-services.json) and as the default in tests. */
object NoopAnalyticsLogger : AnalyticsLogger {
    override fun log(event: AnalyticsEvent) = Unit
    override fun applyConsent(
        analyticsAllowed: Boolean,
        adStorageAllowed: Boolean,
        adUserDataAllowed: Boolean,
        adPersonalizationAllowed: Boolean,
    ) = Unit
    override fun setUserProperty(name: String, value: String?) = Unit
}
