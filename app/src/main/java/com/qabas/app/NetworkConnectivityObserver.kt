package com.qabas.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

interface ConnectivityObserver {
    fun observe(): Flow<Status>

    enum class Status {
        Available, Unavailable, Losing, Lost
    }
}

class NetworkConnectivityObserver(private val context: Context) : ConnectivityObserver {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private fun hasValidatedInternet(network: Network): Boolean {
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun currentNetworkStatus(): ConnectivityObserver.Status =
        if (NetworkUtils.isNetworkAvailable(context)) ConnectivityObserver.Status.Available
        else ConnectivityObserver.Status.Unavailable

    override fun observe(): Flow<ConnectivityObserver.Status> {
        return callbackFlow {
            // Check initial network state immediately
            val isInitiallyAvailable = NetworkUtils.isNetworkAvailable(context)
            send(if (isInitiallyAvailable) ConnectivityObserver.Status.Available else ConnectivityObserver.Status.Unavailable)

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    launch { send(currentNetworkStatus()) }
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    super.onCapabilitiesChanged(network, capabilities)
                    // Read the active route rather than letting an unrelated secondary network
                    // overwrite the app-wide connectivity state.
                    val networkIsValidated =
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    launch {
                        send(
                            if (connectivityManager.activeNetwork == network && networkIsValidated) {
                                ConnectivityObserver.Status.Available
                            } else {
                                currentNetworkStatus()
                            }
                        )
                    }
                }

                override fun onLosing(network: Network, maxMsToLive: Int) {
                    super.onLosing(network, maxMsToLive)
                    launch {
                        send(
                            if (connectivityManager.activeNetwork == network && hasValidatedInternet(network)) {
                                ConnectivityObserver.Status.Losing
                            } else {
                                currentNetworkStatus()
                            }
                        )
                    }
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    launch {
                        send(
                            if (currentNetworkStatus() == ConnectivityObserver.Status.Available) {
                                ConnectivityObserver.Status.Available
                            } else {
                                ConnectivityObserver.Status.Lost
                            }
                        )
                    }
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    launch { send(currentNetworkStatus()) }
                }
            }

            val registered = runCatching {
                connectivityManager.registerNetworkCallback(
                    NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build(),
                    callback
                )
            }.isSuccess

            if (registered) {
                awaitClose {
                    runCatching { connectivityManager.unregisterNetworkCallback(callback) }
                }
            } else {
                // Platform refused another callback (e.g. Android 14+ TooManyRequestsException).
                // Keep the last known status instead of crashing.
                awaitClose { }
            }
        }.distinctUntilChanged()
    }
}
