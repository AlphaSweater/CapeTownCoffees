package com.synaptix.capetowncoffees.domain.usecase.connectivity

import com.synaptix.capetowncoffees.data.connectivity.OfflineModeManager
import com.synaptix.capetowncoffees.data.connectivity.NetworkState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * Observe connectivity state as a Flow of [NetworkState].
 *
 * Intended for UI or long-lived consumers that need to react to connectivity changes.
 * NetworkState contains:
 *  - isOnline: whether the platform currently reports a network with INTERNET capability
 *  - isUserForcedOffline: whether the user explicitly forced offline mode
 *  - effectiveIsOnline: final computed value (isOnline && !isUserForcedOffline)
 *
 * Collection guidance:
 *  - Collect this flow in lifecycle-aware scopes (e.g. ViewModelScope, or lifecycleScope in Fragments)
 */
class ObserveConnectivityStateUseCase @Inject constructor(
    private val offlineModeManager: OfflineModeManager
) {
    fun observe(): Flow<NetworkState> {
        return combine(
            offlineModeManager.networkIsOnline,
            offlineModeManager.isUserOfflineMode,
            offlineModeManager.effectiveIsOnline
        ) { isOnline, isUserForcedOffline, effectiveIsOnline ->
            NetworkState(isOnline, isUserForcedOffline, effectiveIsOnline)
        }
    }
}
