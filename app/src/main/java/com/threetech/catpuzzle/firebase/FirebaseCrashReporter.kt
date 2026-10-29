package com.threetech.catpuzzle.firebase

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.threetech.catpuzzle.domain.crash.CrashReporter

/** Crashlytics adapter. Every call is wrapped: reporting must never crash the app. */
class FirebaseCrashReporter(private val crashlytics: FirebaseCrashlytics) : CrashReporter {

    override fun setCollectionEnabled(enabled: Boolean) = safe { crashlytics.setCrashlyticsCollectionEnabled(enabled) }

    override fun log(message: String) = safe { crashlytics.log(message) }

    override fun setCustomKey(key: String, value: String) = safe { crashlytics.setCustomKey(key, value) }

    override fun setCustomKey(key: String, value: Int) = safe { crashlytics.setCustomKey(key, value) }

    override fun setCustomKey(key: String, value: Boolean) = safe { crashlytics.setCustomKey(key, value) }

    override fun recordNonFatal(throwable: Throwable, context: String?) = safe {
        if (!context.isNullOrBlank()) crashlytics.log(context)
        crashlytics.recordException(throwable)
    }

    private inline fun safe(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            // swallow
        }
    }
}
