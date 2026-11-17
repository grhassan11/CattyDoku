package com.tm.meow.sudoku.puzzle.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.tm.meow.sudoku.puzzle.domain.network.ConnectivityObserver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [ConnectivityObserver] over the default-network callback (API 24+, minSdk 26 ok). Online = INTERNET + VALIDATED.
 * Registered once for the process (singleton, never unregistered: it is a few bytes of state). Any failure to register
 * (missing permission on a odd ROM, SecurityException) fails OPEN: stays online, ads behave as before.
 */
@Singleton
class NetworkConnectivityObserver @Inject constructor(
    @ApplicationContext context: Context,
) : ConnectivityObserver {

    private val _isOnline = MutableStateFlow(true)
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            // Seed from the current default network: with no network at all (airplane mode) the default callback never fires,
            // so without this the flag would stay "online" and the offline -> online edge could never be seen.
            _isOnline.value = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }?.let(::isUsable) ?: false
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                @Volatile private var current: Network? = null

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    current = network
                    _isOnline.value = isUsable(caps)
                }

                override fun onLost(network: Network) {
                    // Only the CURRENT default going away counts: on a wifi -> cell switch the old network may be lost AFTER the new
                    // one reported capabilities.
                    if (network != current) return
                    _isOnline.value = false
                }

                override fun onUnavailable() {
                    _isOnline.value = false
                }
            })
        } catch (t: Throwable) {
            _isOnline.value = true // fail open
        }
    }

    private fun isUsable(caps: NetworkCapabilities): Boolean =
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
