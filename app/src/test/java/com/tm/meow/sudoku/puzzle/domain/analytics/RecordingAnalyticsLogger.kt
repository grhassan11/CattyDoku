package com.tm.meow.sudoku.puzzle.domain.analytics

/** Test fake: keeps every logged event and user property in order. */
class RecordingAnalyticsLogger : AnalyticsLogger {
    val events = ArrayList<AnalyticsEvent>()
    val userProps = LinkedHashMap<String, String?>()
    var lastConsent: List<Boolean>? = null // analytics, adStorage, adUserData, adPersonalization

    override fun log(event: AnalyticsEvent) {
        events += event
    }

    override fun applyConsent(
        analyticsAllowed: Boolean,
        adStorageAllowed: Boolean,
        adUserDataAllowed: Boolean,
        adPersonalizationAllowed: Boolean,
    ) {
        lastConsent = listOf(analyticsAllowed, adStorageAllowed, adUserDataAllowed, adPersonalizationAllowed)
    }

    override fun setUserProperty(name: String, value: String?) {
        userProps[name] = value
    }

    val names: List<String> get() = events.map { it.name }

    fun count(name: String): Int = events.count { it.name == name }

    fun first(name: String): AnalyticsEvent = events.first { it.name == name }
}
