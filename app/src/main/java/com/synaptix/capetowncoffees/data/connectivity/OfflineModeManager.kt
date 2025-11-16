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

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the user's offline mode preference and combines it with actual network status
 * to determine the effective connectivity state.
 * 
 * This allows users to manually force the app into offline mode even when network is available,
 * which is useful for:
 * - Saving data/battery
 * - Testing offline behavior
 * - Working with cached data only
 * 
 * The effective online status is: (network is online) AND (user has NOT forced offline mode)
 * 
 * Usage:
 * ```
 * // Check effective status
 * offlineModeManager.effectiveIsOnline.collect { online ->
 *     if (online) {
 *         // Safe to make network requests
 *     } else {
 *         // Work with cache or show offline UI
 *     }
 * }
 * 
 * // Toggle user preference
 * offlineModeManager.setUserOfflineMode(true) // Force offline
 * offlineModeManager.setUserOfflineMode(false) // Allow online
 * ```
 */
@Singleton
class OfflineModeManager @Inject constructor(
    private val networkStatusService: NetworkStatusService,
    private val sharedPreferences: SharedPreferences
) {
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    companion object {
        private const val PREF_KEY_USER_OFFLINE_MODE = "user_forced_offline_mode"
    }
    
    // User's preference for forcing offline mode
    private val _isUserOfflineMode = MutableStateFlow(loadUserOfflineMode())
    val isUserOfflineMode: StateFlow<Boolean> = _isUserOfflineMode.asStateFlow()
    
    // The effective online status combining network + user preference
    private val _effectiveIsOnline = MutableStateFlow(false)
    val effectiveIsOnline: StateFlow<Boolean> = _effectiveIsOnline.asStateFlow()
    
    // Last known network status
    private var lastNetworkOnline = false
    
    init {
        // Start observing network status changes
        managerScope.launch {
            networkStatusService.isOnline.collect { networkOnline ->
                lastNetworkOnline = networkOnline
                updateEffectiveStatus()
            }
        }
        
        // Also observe user preference changes
        managerScope.launch {
            _isUserOfflineMode.collect {
                updateEffectiveStatus()
            }
        }
    }
    
    /**
     * Set whether the user wants to force offline mode.
     * When true, the app will behave as if offline even when network is available.
     */
    suspend fun setUserOfflineMode(enabled: Boolean) {
        Timber.d("OfflineModeManager: Setting user offline mode to: $enabled")
        _isUserOfflineMode.value = enabled
        saveUserOfflineMode(enabled)
    }
    
    /**
     * Toggle the user's offline mode preference.
     */
    suspend fun toggleUserOfflineMode() {
        setUserOfflineMode(!_isUserOfflineMode.value)
    }
    
    /**
     * Get the current effective online status (considers both network and user preference).
     */
    fun getCurrentEffectiveOnlineStatus(): Boolean {
        return _effectiveIsOnline.value
    }
    
    /**
     * Get the current user offline mode preference.
     */
    fun getUserOfflineMode(): Boolean {
        return _isUserOfflineMode.value
    }
    
    /**
     * Get the last known network status (regardless of user preference).
     */
    fun getLastNetworkStatus(): Boolean {
        return lastNetworkOnline
    }
    
    /**
     * Update the effective online status based on network + user preference.
     * Effective online = network is online AND user has NOT forced offline mode
     */
    private fun updateEffectiveStatus() {
        val effective = lastNetworkOnline && !_isUserOfflineMode.value
        _effectiveIsOnline.value = effective
        
        Timber.d(
            "OfflineModeManager: Updated effective status - " +
            "network: $lastNetworkOnline, userOffline: ${_isUserOfflineMode.value}, effective: $effective"
        )
    }
    
    /**
     * Load user's offline mode preference from SharedPreferences.
     */
    private fun loadUserOfflineMode(): Boolean {
        val saved = sharedPreferences.getBoolean(PREF_KEY_USER_OFFLINE_MODE, false)
        Timber.d("OfflineModeManager: Loaded user offline mode: $saved")
        return saved
    }
    
    /**
     * Save user's offline mode preference to SharedPreferences.
     */
    private fun saveUserOfflineMode(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(PREF_KEY_USER_OFFLINE_MODE, enabled)
            .apply()
        Timber.d("OfflineModeManager: Saved user offline mode: $enabled")
    }
}
