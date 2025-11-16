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

package com.synaptix.capetowncoffees.domain.model

/**
 * Represents the current network connectivity state.
 * 
 * @property isOnline True if device has working internet connection
 * @property isUserForcedOffline True if user has manually enabled offline mode
 * @property effectiveIsOnline True if app should behave as online (isOnline AND NOT isUserForcedOffline)
 */
data class NetworkState(
    val isOnline: Boolean,
    val isUserForcedOffline: Boolean,
    val effectiveIsOnline: Boolean
) {
    /**
     * Returns the reason why the app is offline, if applicable.
     */
    fun getOfflineReason(): OfflineReason? {
        return when {
            effectiveIsOnline -> null
            isUserForcedOffline -> OfflineReason.USER_FORCED
            !isOnline -> OfflineReason.NO_NETWORK
            else -> null
        }
    }
}

/**
 * Reasons why the app might be offline.
 */
enum class OfflineReason {
    /** No network connection available */
    NO_NETWORK,
    
    /** User has manually enabled offline mode */
    USER_FORCED
}
