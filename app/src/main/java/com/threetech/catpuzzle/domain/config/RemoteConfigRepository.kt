package com.threetech.catpuzzle.domain.config

/**
 * Remote Config boundary.
 *
 * [current] returns ONE snapshot per process: parsed on first read from the last ACTIVATED values (in-app defaults
 * when nothing was ever fetched) and then frozen, so policy never changes mid-session. [refresh] is fire-and-forget
 * (own scope, own time budget, never throws): a fetch that finishes after the first [current] applies next launch.
 */
interface RemoteConfigRepository {
    fun current(): AppRemoteConfig
    fun refresh()
}

/** Firebase not configured: in-app defaults forever. */
object NoopRemoteConfigRepository : RemoteConfigRepository {
    override fun current(): AppRemoteConfig = AppRemoteConfig()
    override fun refresh() = Unit
}
