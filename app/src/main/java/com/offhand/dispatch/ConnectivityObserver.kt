package com.offhand.dispatch

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Live online/offline signal for the UI banner and (later) the dispatcher's
 * immediate-drain trigger. The dispatcher additionally does its own
 * reachability check at execution time — the banner is informational only.
 */
class ConnectivityObserver(context: Context) {

    private val cm =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _online = MutableStateFlow(isCurrentlyOnline())
    val online: StateFlow<Boolean> = _online

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _online.value = true
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            _online.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        override fun onLost(network: Network) {
            // Another network may still be up; re-check instead of assuming offline.
            _online.value = isCurrentlyOnline()
        }
    }

    private var started = false

    fun start() {
        if (started) return
        started = true
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
        _online.value = isCurrentlyOnline()
    }

    private fun isCurrentlyOnline(): Boolean {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
