package com.tm.meow.sudoku.puzzle.firebase

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsEvent
import com.tm.meow.sudoku.puzzle.domain.analytics.AnalyticsLogger

/** Firebase Analytics adapter. Every call is wrapped: analytics must never crash or block the game. */
class FirebaseAnalyticsLogger(private val analytics: FirebaseAnalytics) : AnalyticsLogger {

    override fun log(event: AnalyticsEvent) {
        try {
            analytics.logEvent(event.name, toBundle(event.params))
        } catch (t: Throwable) {
            // swallow
        }
    }

    override fun applyConsent(
        analyticsAllowed: Boolean,
        adStorageAllowed: Boolean,
        adUserDataAllowed: Boolean,
        adPersonalizationAllowed: Boolean,
    ) {
        try {
            analytics.setAnalyticsCollectionEnabled(analyticsAllowed)
            analytics.setConsent(
                mapOf(
                    FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to status(analyticsAllowed),
                    FirebaseAnalytics.ConsentType.AD_STORAGE to status(adStorageAllowed),
                    FirebaseAnalytics.ConsentType.AD_USER_DATA to status(adUserDataAllowed),
                    FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to status(adPersonalizationAllowed),
                ),
            )
        } catch (t: Throwable) {
            // swallow
        }
    }

    override fun setUserProperty(name: String, value: String?) {
        try {
            analytics.setUserProperty(name, value)
        } catch (t: Throwable) {
            // swallow
        }
    }

    private fun status(granted: Boolean) =
        if (granted) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED

    private fun toItemBundle(item: Map<*, *>): Bundle {
        val ib = Bundle(item.size)
        for ((ik, iv) in item) {
            val key = ik as? String ?: continue
            when (iv) {
                is Long -> ib.putLong(key, iv)
                is Int -> ib.putLong(key, iv.toLong())
                is Double -> ib.putDouble(key, iv)
                else -> ib.putString(key, iv.toString())
            }
        }
        return ib
    }

    private fun toBundle(params: Map<String, Any>): Bundle? {
        if (params.isEmpty()) return null
        val b = Bundle(params.size)
        for ((k, v) in params) {
            when (v) {
                is Long -> b.putLong(k, v)
                is Int -> b.putLong(k, v.toLong())
                is Double -> b.putDouble(k, v)
                is List<*> -> b.putParcelableArray(k, v.filterIsInstance<Map<*, *>>().map { toItemBundle(it) }.toTypedArray())
                else -> b.putString(k, v.toString())
            }
        }
        return b
    }
}
