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
import com.synaptix.capetowncoffees.data.connectivity.NetworkState
import com.synaptix.capetowncoffees.data.connectivity.OfflineReason
import com.synaptix.capetowncoffees.domain.usecase.connectivity.IsEffectivelyOnlineUseCase
import com.synaptix.capetowncoffees.domain.usecase.connectivity.ObserveConnectivityStateUseCase
import kotlinx.coroutines.launch


// UI helpers for observing connectivity.

fun Fragment.observeConnectivity(
    observeConnectivityStateUseCase: ObserveConnectivityStateUseCase,
    onStateChanged: (NetworkState) -> Unit
) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            observeConnectivityStateUseCase.observe().collect { state -> onStateChanged(state) }
        }
    }
}

fun Fragment.showOfflineBannerWhenNeeded(
    observeConnectivityStateUseCase: ObserveConnectivityStateUseCase,
    rootView: View,
    customMessage: ((NetworkState) -> String)? = null
) {
    var currentSnackbar: Snackbar? = null

    observeConnectivity(observeConnectivityStateUseCase) { state ->
        if (!state.effectiveIsOnline) {
            if (currentSnackbar?.isShown != true) {
                val message = customMessage?.invoke(state) ?: getDefaultOfflineMessage(state)
                currentSnackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_INDEFINITE)
                    .setAction("Dismiss") { currentSnackbar?.dismiss() }
                currentSnackbar?.show()
            }
        } else {
            currentSnackbar?.dismiss()
            currentSnackbar = null
        }
    }
}

fun Fragment.executeIfOnline(
    isEffectivelyOnlineUseCase: IsEffectivelyOnlineUseCase,
    rootView: View,
    action: () -> Unit
) {
    val isOnline = isEffectivelyOnlineUseCase()
    if (isOnline) {
        action()
    } else {
        // We don't have a NetworkState here; show a generic message
        Snackbar.make(rootView, "Offline", Snackbar.LENGTH_SHORT).show()
    }
}

fun Fragment.isOnline(isEffectivelyOnlineUseCase: IsEffectivelyOnlineUseCase): Boolean {
    return isEffectivelyOnlineUseCase()
}

private fun getDefaultOfflineMessage(state: NetworkState): String {
    return when (state.getOfflineReason()) {
        OfflineReason.NO_NETWORK -> "No internet connection. Some features may be unavailable."
        OfflineReason.USER_FORCED -> "Offline mode is enabled. Network features are disabled."
        null -> "Connected"
    }
}
