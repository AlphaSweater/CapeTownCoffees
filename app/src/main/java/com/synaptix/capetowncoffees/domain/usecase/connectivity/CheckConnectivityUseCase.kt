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

package com.synaptix.capetowncoffees.domain.usecase.connectivity

import com.synaptix.capetowncoffees.data.connectivity.NetworkStatusService
import com.synaptix.capetowncoffees.data.connectivity.OfflineModeManager
import com.synaptix.capetowncoffees.domain.model.NetworkState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * Use case for checking network connectivity status.
 * 
 * This provides a simple interface for other parts of the app to check whether
 * they should attempt network operations or fall back to offline behavior.
 * 
 * Usage:
 * ```
 * // Get current state (snapshot)
 * val state = checkConnectivityUseCase.getCurrentState()
 * if (state.effectiveIsOnline) {
 *     // Make network request
 * } else {
 *     // Show cached data or offline message
 * }
 * 
 * // Observe state changes (reactive)
 * checkConnectivityUseCase.observeConnectivityState().collect { state ->
 *     updateUI(state)
 * }
 * ```
 */
class CheckConnectivityUseCase @Inject constructor(
    private val networkStatusService: NetworkStatusService,
    private val offlineModeManager: OfflineModeManager
) {
    
    /**
     * Get the current network state as a snapshot.
     * Use this for one-time checks before network operations.
     */
    fun getCurrentState(): NetworkState {
        val isOnline = networkStatusService.isOnline.value
        val isUserForcedOffline = offlineModeManager.isUserOfflineMode.value
        val effectiveIsOnline = offlineModeManager.effectiveIsOnline.value
        
        return NetworkState(
            isOnline = isOnline,
            isUserForcedOffline = isUserForcedOffline,
            effectiveIsOnline = effectiveIsOnline
        )
    }
    
    /**
     * Observe network state changes as a Flow.
     * Use this to reactively update UI or trigger actions when connectivity changes.
     */
    fun observeConnectivityState(): Flow<NetworkState> {
        return combine(
            networkStatusService.isOnline,
            offlineModeManager.isUserOfflineMode,
            offlineModeManager.effectiveIsOnline
        ) { isOnline, isUserForcedOffline, effectiveIsOnline ->
            NetworkState(
                isOnline = isOnline,
                isUserForcedOffline = isUserForcedOffline,
                effectiveIsOnline = effectiveIsOnline
            )
        }
    }
    
    /**
     * Quick check if app should behave as online.
     * Shortcut for getCurrentState().effectiveIsOnline
     */
    fun isEffectivelyOnline(): Boolean {
        return offlineModeManager.getCurrentEffectiveOnlineStatus()
    }
    
    /**
     * Quick check if network is available (ignoring user preference).
     */
    fun isNetworkAvailable(): Boolean {
        return networkStatusService.isOnline.value
    }
    
    /**
     * Quick check if user has forced offline mode.
     */
    fun isUserOfflineModeEnabled(): Boolean {
        return offlineModeManager.getUserOfflineMode()
    }
}
