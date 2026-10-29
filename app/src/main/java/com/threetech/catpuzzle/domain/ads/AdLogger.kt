package com.threetech.catpuzzle.domain.ads

/** Debug-trace seam so domain use cases can log ad decisions without Android imports. Impl lives in DI (debug-only). */
fun interface AdLogger {
    fun log(message: String)
}

object NoopAdLogger : AdLogger {
    override fun log(message: String) = Unit
}
