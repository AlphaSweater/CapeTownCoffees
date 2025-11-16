package com.synaptix.capetowncoffees.domain.usecase.connectivity

import com.synaptix.capetowncoffees.data.connectivity.OfflineModeManager
import javax.inject.Inject

/**
 * Use-case to set or toggle the user 'force offline' preference.
 *
 * Behavior:
 * - If `enabled` is non-null -> set the preference to that value.
 * - If `enabled` is null -> toggle the current preference.
 *
 * This keeps a single easy-to-understand API for new developers.
 *
 * Example:
 *   // toggle
 *   setUserOfflineModeUseCase()
 *
 *   // explicitly set
 *   setUserOfflineModeUseCase(true)
 */
class SetUserOfflineModeUseCase @Inject constructor(
    private val offlineModeManager: OfflineModeManager
) {
    operator fun invoke(enabled: Boolean? = null) {
        if (enabled == null) {
            // toggle
            offlineModeManager.setUserOfflineMode(!offlineModeManager.getUserOfflineMode())
        } else {
            // set explicit value
            offlineModeManager.setUserOfflineMode(enabled)
        }
    }
}
