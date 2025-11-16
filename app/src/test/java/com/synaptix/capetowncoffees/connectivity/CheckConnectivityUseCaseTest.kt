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

package com.synaptix.capetowncoffees.connectivity

import com.synaptix.capetowncoffees.data.connectivity.NetworkStatusService
import com.synaptix.capetowncoffees.data.connectivity.OfflineModeManager
import com.synaptix.capetowncoffees.domain.model.NetworkState
import com.synaptix.capetowncoffees.domain.model.OfflineReason
import com.synaptix.capetowncoffees.domain.usecase.connectivity.CheckConnectivityUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Unit tests for CheckConnectivityUseCase.
 * 
 * Tests the logic that combines network status with user offline mode preference
 * to determine effective connectivity state.
 */
class CheckConnectivityUseCaseTest {

    private lateinit var networkStatusService: NetworkStatusService
    private lateinit var offlineModeManager: OfflineModeManager
    private lateinit var checkConnectivityUseCase: CheckConnectivityUseCase

    private lateinit var networkOnlineFlow: MutableStateFlow<Boolean>
    private lateinit var userOfflineModeFlow: MutableStateFlow<Boolean>
    private lateinit var effectiveOnlineFlow: MutableStateFlow<Boolean>

    @Before
    fun setup() {
        // Create mock services
        networkStatusService = mock()
        offlineModeManager = mock()

        // Create flows to simulate state changes
        networkOnlineFlow = MutableStateFlow(true)
        userOfflineModeFlow = MutableStateFlow(false)
        effectiveOnlineFlow = MutableStateFlow(true)

        // Wire up mocks
        whenever(networkStatusService.isOnline).thenReturn(networkOnlineFlow)
        whenever(offlineModeManager.isUserOfflineMode).thenReturn(userOfflineModeFlow)
        whenever(offlineModeManager.effectiveIsOnline).thenReturn(effectiveOnlineFlow)
        whenever(offlineModeManager.getCurrentEffectiveOnlineStatus()).thenAnswer {
            effectiveOnlineFlow.value
        }
        whenever(offlineModeManager.getUserOfflineMode()).thenAnswer {
            userOfflineModeFlow.value
        }

        // Create use case with mocked dependencies
        checkConnectivityUseCase = CheckConnectivityUseCase(
            networkStatusService,
            offlineModeManager
        )
    }

    @Test
    fun `getCurrentState returns online when network available and user not offline`() {
        // Given
        networkOnlineFlow.value = true
        userOfflineModeFlow.value = false
        effectiveOnlineFlow.value = true

        // When
        val state = checkConnectivityUseCase.getCurrentState()

        // Then
        assertTrue(state.isOnline)
        assertFalse(state.isUserForcedOffline)
        assertTrue(state.effectiveIsOnline)
        assertNull(state.getOfflineReason())
    }

    @Test
    fun `getCurrentState returns offline when network unavailable`() {
        // Given
        networkOnlineFlow.value = false
        userOfflineModeFlow.value = false
        effectiveOnlineFlow.value = false

        // When
        val state = checkConnectivityUseCase.getCurrentState()

        // Then
        assertFalse(state.isOnline)
        assertFalse(state.isUserForcedOffline)
        assertFalse(state.effectiveIsOnline)
        assertEquals(OfflineReason.NO_NETWORK, state.getOfflineReason())
    }

    @Test
    fun `getCurrentState returns offline when user forces offline mode`() {
        // Given: Network available but user forced offline
        networkOnlineFlow.value = true
        userOfflineModeFlow.value = true
        effectiveOnlineFlow.value = false

        // When
        val state = checkConnectivityUseCase.getCurrentState()

        // Then
        assertTrue(state.isOnline)
        assertTrue(state.isUserForcedOffline)
        assertFalse(state.effectiveIsOnline)
        assertEquals(OfflineReason.USER_FORCED, state.getOfflineReason())
    }

    @Test
    fun `getCurrentState returns offline when both network unavailable and user forced offline`() {
        // Given: Double offline condition
        networkOnlineFlow.value = false
        userOfflineModeFlow.value = true
        effectiveOnlineFlow.value = false

        // When
        val state = checkConnectivityUseCase.getCurrentState()

        // Then
        assertFalse(state.isOnline)
        assertTrue(state.isUserForcedOffline)
        assertFalse(state.effectiveIsOnline)
        // User forced takes precedence in reason
        assertEquals(OfflineReason.USER_FORCED, state.getOfflineReason())
    }

    @Test
    fun `isEffectivelyOnline returns correct value`() {
        // Online case
        effectiveOnlineFlow.value = true
        assertTrue(checkConnectivityUseCase.isEffectivelyOnline())

        // Offline case
        effectiveOnlineFlow.value = false
        assertFalse(checkConnectivityUseCase.isEffectivelyOnline())
    }

    @Test
    fun `isNetworkAvailable returns correct value`() {
        // Available
        networkOnlineFlow.value = true
        assertTrue(checkConnectivityUseCase.isNetworkAvailable())

        // Unavailable
        networkOnlineFlow.value = false
        assertFalse(checkConnectivityUseCase.isNetworkAvailable())
    }

    @Test
    fun `isUserOfflineModeEnabled returns correct value`() {
        // Enabled
        userOfflineModeFlow.value = true
        assertTrue(checkConnectivityUseCase.isUserOfflineModeEnabled())

        // Disabled
        userOfflineModeFlow.value = false
        assertFalse(checkConnectivityUseCase.isUserOfflineModeEnabled())
    }
}
