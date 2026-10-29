package com.threetech.catpuzzle.firebase

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.threetech.catpuzzle.domain.analytics.AnalyticsEvent
import com.threetech.catpuzzle.domain.analytics.AnalyticsLogger

/** Firebase Analytics adapter. Every call is wrapped: analytics must never crash or block the game. */
class FirebaseAnalyticsLogger(private val analytics: FirebaseAnalytics) : AnalyticsLogger {

    override fun log(event: AnalyticsEvent) {
        try {
            analytics.logEvent(event.name, toBundle(event.params))
        } catch (t: Throwable) {
            // swallow
        }
    }

    override fun applyConsent(analyticsAllowed: Boolean, adsAllowed: Boolean) {
        try {
            analytics.setAnalyticsCollectionEnabled(analyticsAllowed)
            analytics.setConsent(
                mapOf(
                    FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to status(analyticsAllowed),
                    FirebaseAnalytics.ConsentType.AD_STORAGE to status(adsAllowed),
                    FirebaseAnalytics.ConsentType.AD_USER_DATA to status(adsAllowed),
                    FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to status(adsAllowed),
                ),
            )
        } catch (t: Throwable) {
            // swallow
        }
    }

    private fun status(granted: Boolean) =
        if (granted) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED

    private fun toBundle(params: Map<String, Any>): Bundle? {
        if (params.isEmpty()) return null
        val b = Bundle(params.size)
        for ((k, v) in params) {
            when (v) {
                is Long -> b.putLong(k, v)
                is Int -> b.putLong(k, v.toLong())
                is Double -> b.putDouble(k, v)
                else -> b.putString(k, v.toString())
            }
        }
        return b
    }
}
