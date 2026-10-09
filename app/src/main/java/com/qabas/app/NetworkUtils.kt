package com.qabas.app

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.delay

object NetworkUtils {
    suspend fun <T> safeApiCall(
        context: Context,
        fallback: () -> T,
        block: suspend () -> T
    ): T {
        return try {
            block()
        } catch (e: Exception) {
            e.printStackTrace()
            fallback()
        }
    }

    suspend fun <T> safeApiCallWithRetry(
        context: Context,
        maxRetries: Int = 3,
        initialDelayMs: Long = 1000L,
        fallback: () -> T,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                e.printStackTrace()
                if (attempt == maxRetries - 1) {
                    return fallback()
                }
                delay(currentDelay)
                currentDelay *= 2
            }
        }
        return fallback()
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        if (connectivityManager != null) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        return false
    }
}
