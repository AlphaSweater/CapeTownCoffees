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

/**
 * Exception thrown when a network operation is attempted while offline.
 * 
 * This provides a clear signal to calling code that the failure was due to
 * being offline (either no network or user-forced offline mode), rather than
 * other types of network errors.
 */
class OfflineException(
    message: String = "Cannot perform operation while offline",
    val isUserForced: Boolean = false
) : Exception(message)

/**
 * Interface for repositories that need to be aware of offline status.
 * 
 * Provides helper methods to wrap network calls with offline checks, making it
 * easy to fail fast when offline rather than attempting doomed network requests.
 * 
 * Usage in a repository:
 * ```
 * class MyCoffeeRepository @Inject constructor(
 *     private val offlineModeManager: OfflineModeManager,
 *     // ... other dependencies
 * ) : OfflineAwareRepository {
 *     override val offlineModeManager get() = this@MyCoffeeRepository.offlineModeManager
 *     
 *     suspend fun fetchCoffees(): Result<List<Coffee>> {
 *         return executeIfOnline {
 *             // This only runs if online
 *             api.getCoffees()
 *         }
 *     }
 * }
 * ```
 */
interface OfflineAwareRepository {
    
    /**
     * The OfflineModeManager instance to use for checking connectivity.
     * Implementing classes should provide this via dependency injection.
     */
    val offlineModeManager: OfflineModeManager
    
    /**
     * Execute a network operation only if online.
     * If offline, throws an OfflineException instead of attempting the operation.
     * 
     * @param operation The network operation to perform
     * @return The result of the operation
     * @throws OfflineException if offline (either no network or user-forced)
     */
    suspend fun <T> executeIfOnline(operation: suspend () -> T): T {
        if (!offlineModeManager.getCurrentEffectiveOnlineStatus()) {
            val isUserForced = offlineModeManager.getUserOfflineMode()
            throw OfflineException(
                message = if (isUserForced) {
                    "Operation blocked: User has enabled offline mode"
                } else {
                    "Operation blocked: No network connection available"
                },
                isUserForced = isUserForced
            )
        }
        return operation()
    }
    
    /**
     * Execute a network operation and wrap it in a Result.
     * If offline, returns a failure Result with OfflineException.
     * If the operation throws, returns a failure Result with that exception.
     * 
     * @param operation The network operation to perform
     * @return Result.success if online and operation succeeds, Result.failure otherwise
     */
    suspend fun <T> executeIfOnlineAsResult(operation: suspend () -> T): Result<T> {
        return try {
            Result.success(executeIfOnline(operation))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Execute a network operation if online, otherwise return a fallback value.
     * Useful when you have cached data or a default to use when offline.
     * 
     * @param operation The network operation to perform
     * @param fallback The value to return if offline
     * @return Result of operation if online, fallback if offline
     */
    suspend fun <T> executeOrFallback(
        operation: suspend () -> T,
        fallback: T
    ): T {
        return if (offlineModeManager.getCurrentEffectiveOnlineStatus()) {
            try {
                operation()
            } catch (e: Exception) {
                // Network operation failed, use fallback
                fallback
            }
        } else {
            fallback
        }
    }
    
    /**
     * Check if currently online (considers both network and user preference).
     */
    fun isOnline(): Boolean {
        return offlineModeManager.getCurrentEffectiveOnlineStatus()
    }
    
    /**
     * Check if user has forced offline mode.
     */
    fun isUserOfflineMode(): Boolean {
        return offlineModeManager.getUserOfflineMode()
    }
}
