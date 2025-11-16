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
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
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

@Singleton
class OfflineModeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPreferences: SharedPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val PREF_KEY_USER_OFFLINE_MODE = "user_forced_offline_mode"
    }

    // Raw network availability (true when platform reports network with INTERNET capability)
    private val _networkIsOnline = MutableStateFlow(false)
    val networkIsOnline: StateFlow<Boolean> = _networkIsOnline.asStateFlow()

    // User preference to force offline mode
    private val _isUserOfflineMode = MutableStateFlow(loadUserOfflineMode())
    val isUserOfflineMode: StateFlow<Boolean> = _isUserOfflineMode.asStateFlow()

    // Effective online: network available && NOT user forced offline
    private val _effectiveIsOnline = MutableStateFlow(false)
    val effectiveIsOnline: StateFlow<Boolean> = _effectiveIsOnline.asStateFlow()

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var started = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateNetworkStatus()
        }

        override fun onLost(network: Network) {
            _networkIsOnline.value = false
            updateEffectiveStatus()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val hasValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            // Treat network as online if it has INTERNET capability (validated is nice-to-have)
            _networkIsOnline.value = hasInternet && hasValidated
            updateEffectiveStatus()
        }
    }

    /**
     * Start listening to platform connectivity. Call once from Application.onCreate().
     */
    fun start() {
        if (started) return
        started = true

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (t: Throwable) {
            Timber.w(t, "OfflineModeManager: Failed to register network callback")
        }

        // Do initial synchronous check
        updateNetworkStatus()
    }

    fun stop() {
        if (!started) return
        started = false
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (t: Throwable) {
            Timber.w(t, "OfflineModeManager: Failed to unregister network callback")
        }
    }

    private fun updateNetworkStatus() {
        scope.launch {
            try {
                val network = connectivityManager.activeNetwork
                if (network == null) {
                    _networkIsOnline.value = false
                } else {
                    val caps = connectivityManager.getNetworkCapabilities(network)
                    _networkIsOnline.value = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                }
            } catch (t: Throwable) {
                Timber.w(t, "OfflineModeManager: updateNetworkStatus failed")
                _networkIsOnline.value = false
            }
            updateEffectiveStatus()
        }
    }

    private fun updateEffectiveStatus() {
        _effectiveIsOnline.value = _networkIsOnline.value && !_isUserOfflineMode.value
        Timber.d("OfflineModeManager: network=${_networkIsOnline.value} userOffline=${_isUserOfflineMode.value} effective=${_effectiveIsOnline.value}")
    }

    fun setUserOfflineMode(enabled: Boolean) {
        _isUserOfflineMode.value = enabled
        saveUserOfflineMode(enabled)
        updateEffectiveStatus()
    }

    fun toggleUserOfflineMode() {
        setUserOfflineMode(!_isUserOfflineMode.value)
    }

    fun getCurrentEffectiveOnlineStatus(): Boolean = _effectiveIsOnline.value

    fun getUserOfflineMode(): Boolean = _isUserOfflineMode.value

    fun getLastNetworkStatus(): Boolean = _networkIsOnline.value

    private fun loadUserOfflineMode(): Boolean {
        return sharedPreferences.getBoolean(PREF_KEY_USER_OFFLINE_MODE, false)
    }

    private fun saveUserOfflineMode(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(PREF_KEY_USER_OFFLINE_MODE, enabled).apply()
    }
}

/**
 * Represents the current network connectivity state.
 *
 * Kept here inside `data.connectivity` package for compactness. Code that
 * previously imported `com.synaptix.capetowncoffees.domain.model.NetworkState`
 * should now import `com.synaptix.capetowncoffees.data.connectivity.NetworkState`.
 */
data class NetworkState(
    val isOnline: Boolean,
    val isUserForcedOffline: Boolean,
    val effectiveIsOnline: Boolean
) {
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
    NO_NETWORK,
    USER_FORCED
}
