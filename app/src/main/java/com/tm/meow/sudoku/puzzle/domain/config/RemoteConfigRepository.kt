package com.tm.meow.sudoku.puzzle.domain.config

/**
 * Remote Config boundary.
 *
 * [current] returns the latest ACTIVATED values (in-app defaults when nothing was ever fetched). It is cached, but the
 * cache is replaced when a fetch activates, so a late fetch applies to the NEXT read in the same session (B+C: the first
 * fetch is also awaited briefly on splash via [refreshAndAwait], so a freshly published value applies on that launch).
 * Consumers must read once per decision (never capture a value). [refresh] is fire-and-forget (own scope, own time
 * budget, never throws).
 */
interface RemoteConfigRepository {
    fun current(): AppRemoteConfig
    fun refresh()

    /**
     * [refresh], then wait at most [timeoutMs] for that fetch + activate. True = fresh values are active. Never throws;
     * timeout / failure = false (cached / default values stay, the fetch may still finish in the background).
     * Default: fire-and-forget (no await).
     */
    suspend fun refreshAndAwait(timeoutMs: Long): Boolean {
        refresh()
        return false
    }
}

/** Firebase not configured: in-app defaults forever. */
object NoopRemoteConfigRepository : RemoteConfigRepository {
    override fun current(): AppRemoteConfig = AppRemoteConfig()
    override fun refresh() = Unit
}
