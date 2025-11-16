//======================================================================================
//Group 2 - Group Members:
//======================================================================================
//* Chad Fairlie ST10269509
//* Dhiren Ruthenavelu ST10256859
//* Kayla Ferreira ST10259527
//* Nathan Teixeira ST10249266
//======================================================================================
//References:
//======================================================================================
//* ChatGPT was used to assist with the development, design, and debugging of this file.
//* AI support was used for learning purposes, improving clarity and resolving issues.
//* It also helped generate useful comments
//======================================================================================

package com.synaptix.capetowncoffees.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service that monitors network connectivity and internet reachability.
 * 
 * Combines platform connectivity events with periodic active checks to a known
 * endpoint to provide reliable online/offline status. This is more accurate than
 * just checking if WiFi/mobile is connected, as those don't guarantee internet access.
 * 
 * Usage:
 * ```
 * networkStatusService.isOnline.collect { online ->
 *     if (online) {
 *         // Perform network operations
 *     } else {
 *         // Show offline UI
 *     }
 * }
 * ```
 */
@Singleton
class NetworkStatusService @Inject constructor(
    private val context: Context
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    // Use a supervised scope so failures don't cancel the whole service
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()
    
    private var pingJob: Job? = null
    private var isStarted = false
    
    // Configuration
    private val pingIntervalMs = 30_000L // Check every 30 seconds
    private val pingTimeoutMs = 5_000 // 5 second timeout for ping
    private val pingUrl = "https://www.google.com" // Reliable endpoint
    
    /**
     * Start monitoring network status.
     * Should be called once from Application.onCreate()
     */
    fun start() {
        if (isStarted) {
            Timber.w("NetworkStatusService already started")
            return
        }
        isStarted = true
        
        // Register connectivity callback for immediate updates
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        try {
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            Timber.d("NetworkStatusService: Registered network callback")
        } catch (e: Exception) {
            Timber.e(e, "Failed to register network callback")
        }
        
        // Perform initial check
        serviceScope.launch {
            checkConnectivity()
        }
        
        // Start periodic ping checks
        startPeriodicPing()
    }
    
    /**
     * Stop monitoring. Should be called if you need to clean up.
     */
    fun stop() {
        if (!isStarted) return
        isStarted = false
        
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            Timber.d("NetworkStatusService: Unregistered network callback")
        } catch (e: Exception) {
            Timber.e(e, "Failed to unregister network callback")
        }
        
        pingJob?.cancel()
        pingJob = null
    }
    
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Timber.d("NetworkStatusService: Network available")
            serviceScope.launch { checkConnectivity() }
        }
        
        override fun onLost(network: Network) {
            Timber.d("NetworkStatusService: Network lost")
            _isOnline.value = false
        }
        
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val hasValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            Timber.d("NetworkStatusService: Capabilities changed - hasInternet: $hasInternet, validated: $hasValidated")
            
            if (hasInternet && hasValidated) {
                serviceScope.launch { checkConnectivity() }
            }
        }
    }
    
    private fun startPeriodicPing() {
        pingJob?.cancel()
        pingJob = serviceScope.launch {
            while (isActive) {
                delay(pingIntervalMs)
                if (isActive) {
                    checkConnectivity()
                }
            }
        }
    }
    
    /**
     * Manually trigger a connectivity check.
     * Useful for on-demand checks before important operations.
     */
    suspend fun checkNow(): Boolean {
        checkConnectivity()
        return _isOnline.value
    }
    
    /**
     * Checks connectivity using both platform APIs and active reachability test.
     */
    private suspend fun checkConnectivity() {
        // First, quick check if we have any network
        val hasNetwork = hasActiveNetwork()
        
        if (!hasNetwork) {
            _isOnline.value = false
            Timber.d("NetworkStatusService: No active network")
            return
        }
        
        // If we have a network, verify we can actually reach the internet
        val canReachInternet = canReachInternet()
        _isOnline.value = canReachInternet
        
        Timber.d("NetworkStatusService: Connectivity check result: $canReachInternet")
    }
    
    /**
     * Check if device has an active network connection.
     */
    private fun hasActiveNetwork(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    
    /**
     * Actively test if we can reach the internet by making a HEAD request.
     * Uses a short timeout to avoid blocking for too long.
     */
    private suspend fun canReachInternet(): Boolean {
        return try {
            val url = URL(pingUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "HEAD"
                connectTimeout = pingTimeoutMs
                readTimeout = pingTimeoutMs
                instanceFollowRedirects = false
            }
            
            connection.connect()
            val responseCode = connection.responseCode
            connection.disconnect()
            
            // Accept any reasonable response (2xx, 3xx, even 4xx means we reached the server)
            val reachable = responseCode in 200..499
            Timber.d("NetworkStatusService: Ping result - code: $responseCode, reachable: $reachable")
            reachable
        } catch (e: Exception) {
            Timber.d("NetworkStatusService: Ping failed - ${e.message}")
            false
        }
    }
}
