package com.synaptix.capetowncoffees.domain.usecase

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

//returns true if there is no internet connection
class IsOfflineUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {

    operator fun invoke(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Get the currently active network
        val network = cm.activeNetwork ?: return true

        // Get capabilities for the active network
        val caps = cm.getNetworkCapabilities(network) ?: return true

        // Device is online only if it has Internet + is Validated
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        return !(hasInternet && isValidated)
    }
}