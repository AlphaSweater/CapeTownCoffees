package com.synaptix.capetowncoffees.domain.usecase.connectivity

import com.synaptix.capetowncoffees.data.connectivity.OfflineModeManager
import javax.inject.Inject

/**
 * Simple use-case returning whether the app should behave as online (considers user toggle).
 * Use this in ViewModels when you only need a quick synchronous check. Example:
 *
 *   val isOnline = isEffectivelyOnlineUseCase()
 *
 * Prefer `ObserveConnectivityStateUseCase` when you need to react to changes over time.
 */
class IsEffectivelyOnlineUseCase @Inject constructor(
    private val offlineModeManager: OfflineModeManager
) {
    operator fun invoke(): Boolean = offlineModeManager.getCurrentEffectiveOnlineStatus()
}
