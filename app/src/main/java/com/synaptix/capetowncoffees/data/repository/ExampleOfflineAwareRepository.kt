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

package com.synaptix.capetowncoffees.data.repository

import com.synaptix.capetowncoffees.data.connectivity.OfflineAwareRepository
import com.synaptix.capetowncoffees.data.connectivity.OfflineModeManager
import javax.inject.Inject

/**
 * Example repository demonstrating how to use the OfflineAwareRepository interface.
 * 
 * This shows the recommended pattern for making repositories aware of offline state
 * so they can fail fast when offline rather than attempting doomed network requests.
 * 
 * To make an existing repository offline-aware:
 * 
 * 1. Implement the OfflineAwareRepository interface
 * 2. Inject OfflineModeManager via constructor
 * 3. Override the offlineModeManager property
 * 4. Wrap network calls with executeIfOnline() or executeIfOnlineAsResult()
 * 
 * Example usage:
 * ```kotlin
 * class CoffeePlaceRepository @Inject constructor(
 *     private val api: CoffeePlaceApi,
 *     override val offlineModeManager: OfflineModeManager
 * ) : OfflineAwareRepository {
 * 
 *     suspend fun fetchCoffees(): Result<List<Coffee>> {
 *         // This will throw OfflineException if offline
 *         return executeIfOnlineAsResult {
 *             api.getCoffees()
 *         }
 *     }
 * 
 *     suspend fun fetchCoffeesWithFallback(cached: List<Coffee>): List<Coffee> {
 *         // This will return cached data if offline
 *         return executeOrFallback(
 *             operation = { api.getCoffees() },
 *             fallback = cached
 *         )
 *     }
 * 
 *     suspend fun syncFavorites(favorites: List<String>) {
 *         // This will throw if offline, caller must handle
 *         executeIfOnline {
 *             api.syncFavorites(favorites)
 *         }
 *     }
 * }
 * ```
 */
class ExampleOfflineAwareRepository @Inject constructor(
    override val offlineModeManager: OfflineModeManager
) : OfflineAwareRepository {
    
    /**
     * Example: Network operation that fails fast when offline.
     */
    suspend fun fetchDataFailFast(): Result<String> {
        return executeIfOnlineAsResult {
            // Simulate API call
            simulateApiCall()
        }
    }
    
    /**
     * Example: Network operation with fallback to cached data.
     */
    suspend fun fetchDataWithCache(cachedData: String): String {
        return executeOrFallback(
            operation = { simulateApiCall() },
            fallback = cachedData
        )
    }
    
    /**
     * Example: Conditional logic based on online status.
     */
    suspend fun smartFetch(): String {
        return if (isOnline()) {
            try {
                simulateApiCall()
            } catch (e: Exception) {
                "Failed to fetch: ${e.message}"
            }
        } else {
            "Using cached data (offline)"
        }
    }
    
    // Simulated API call
    private suspend fun simulateApiCall(): String {
        // In a real repository, this would be an actual API call
        return "Fresh data from API"
    }
}
