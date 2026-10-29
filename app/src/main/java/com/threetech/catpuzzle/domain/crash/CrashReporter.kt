package com.threetech.catpuzzle.domain.crash

/**
 * Crash reporting seam. Impl never throws. Keep messages and keys free of PII and secrets (they ship in release).
 * Collection is OFF until [setCollectionEnabled] is called by the consent gate.
 */
interface CrashReporter {
    fun setCollectionEnabled(enabled: Boolean)

    /** Breadcrumb attached to the next report. */
    fun log(message: String)

    fun setCustomKey(key: String, value: String)
    fun setCustomKey(key: String, value: Int)
    fun setCustomKey(key: String, value: Boolean)

    /** Non-fatal: caught problem worth seeing in the console. [context] is an optional short breadcrumb. */
    fun recordNonFatal(throwable: Throwable, context: String? = null)
}

object NoopCrashReporter : CrashReporter {
    override fun setCollectionEnabled(enabled: Boolean) = Unit
    override fun log(message: String) = Unit
    override fun setCustomKey(key: String, value: String) = Unit
    override fun setCustomKey(key: String, value: Int) = Unit
    override fun setCustomKey(key: String, value: Boolean) = Unit
    override fun recordNonFatal(throwable: Throwable, context: String?) = Unit
}
