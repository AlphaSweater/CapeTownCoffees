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

package com.synaptix.capetowncoffees.ui.common

import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.synaptix.capetowncoffees.domain.model.NetworkState
import com.synaptix.capetowncoffees.domain.model.OfflineReason
import com.synaptix.capetowncoffees.domain.usecase.connectivity.CheckConnectivityUseCase
import kotlinx.coroutines.launch

/**
 * Extension functions for making Fragments connectivity-aware with minimal boilerplate.
 * 
 * Usage in a Fragment:
 * ```kotlin
 * class MyFragment : Fragment() {
 *     @Inject lateinit var checkConnectivity: CheckConnectivityUseCase
 * 
 *     override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
 *         // Show a persistent banner when offline
 *         showOfflineBannerWhenNeeded(
 *             checkConnectivity = checkConnectivity,
 *             rootView = binding.root
 *         )
 * 
 *         // Or observe state changes for custom behavior
 *         observeConnectivity(checkConnectivity) { state ->
 *             if (!state.effectiveIsOnline) {
 *                 disableNetworkFeatures()
 *             } else {
 *                 enableNetworkFeatures()
 *             }
 *         }
 *     }
 * }
 * ```
 */

/**
 * Observe network connectivity state changes in a Fragment.
 * Automatically cancels when the Fragment is destroyed.
 * 
 * @param checkConnectivity The use case for checking connectivity
 * @param onStateChanged Callback invoked when connectivity state changes
 */
fun Fragment.observeConnectivity(
    checkConnectivity: CheckConnectivityUseCase,
    onStateChanged: (NetworkState) -> Unit
) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            checkConnectivity.observeConnectivityState().collect { state ->
                onStateChanged(state)
            }
        }
    }
}

/**
 * Show a persistent Snackbar when offline that updates based on connectivity state.
 * The banner is automatically shown/hidden as connectivity changes.
 * 
 * @param checkConnectivity The use case for checking connectivity
 * @param rootView The root view to attach the Snackbar to
 * @param customMessage Optional custom message function to override default messages
 */
fun Fragment.showOfflineBannerWhenNeeded(
    checkConnectivity: CheckConnectivityUseCase,
    rootView: View,
    customMessage: ((NetworkState) -> String)? = null
) {
    var currentSnackbar: Snackbar? = null
    
    observeConnectivity(checkConnectivity) { state ->
        if (!state.effectiveIsOnline) {
            // Show offline banner if not already showing
            if (currentSnackbar?.isShown != true) {
                val message = customMessage?.invoke(state) ?: getDefaultOfflineMessage(state)
                currentSnackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_INDEFINITE)
                    .setAction("Dismiss") { currentSnackbar?.dismiss() }
                currentSnackbar?.show()
            }
        } else {
            // Dismiss banner when back online
            currentSnackbar?.dismiss()
            currentSnackbar = null
        }
    }
}

/**
 * Execute an action only if online, otherwise show a Snackbar explaining why it's disabled.
 * 
 * @param checkConnectivity The use case for checking connectivity
 * @param rootView The root view to show the Snackbar on
 * @param action The action to execute if online
 */
fun Fragment.executeIfOnline(
    checkConnectivity: CheckConnectivityUseCase,
    rootView: View,
    action: () -> Unit
) {
    val state = checkConnectivity.getCurrentState()
    if (state.effectiveIsOnline) {
        action()
    } else {
        val message = getDefaultOfflineMessage(state)
        Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT).show()
    }
}

/**
 * Check if currently online (effective status considering network + user preference).
 */
fun Fragment.isOnline(checkConnectivity: CheckConnectivityUseCase): Boolean {
    return checkConnectivity.isEffectivelyOnline()
}

/**
 * Get a user-friendly message explaining why the app is offline.
 */
private fun getDefaultOfflineMessage(state: NetworkState): String {
    return when (state.getOfflineReason()) {
        OfflineReason.NO_NETWORK -> "No internet connection. Some features may be unavailable."
        OfflineReason.USER_FORCED -> "Offline mode is enabled. Network features are disabled."
        null -> "Connected"
    }
}
