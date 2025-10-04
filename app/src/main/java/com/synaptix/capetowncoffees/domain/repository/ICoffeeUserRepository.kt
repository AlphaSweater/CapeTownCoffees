package com.synaptix.capetowncoffees.domain.repository

import com.google.firebase.auth.FirebaseUser
import com.synaptix.capetowncoffees.domain.model.CoffeeUser
import kotlinx.coroutines.flow.Flow

/**
 * IUserRepository defines the contract for accessing and managing user authentication and profiles.
 * It supports registration, login, profile management, password reset, and observing authentication state.
 */
interface ICoffeeUserRepository {
    // ----------------------------
    // Auth related
    // ----------------------------

    /**
     * Gets the currently authenticated Firebase user, or null if not logged in.
     * @return The current FirebaseUser, or null if not authenticated.
     */
    fun getCurrentUser(): FirebaseUser?

    /**
     * Gets the UID of the currently authenticated user, or null if not logged in.
     * @return The current user's UID, or null if not authenticated.
     */
    fun getCurrentUserId(): String?

    /**
     * Registers a new user with the given email, password, and full name.
     * Also creates a user profile in Firestore.
     * @param email The user's email address.
     * @param password The user's password.
     * @param fullName The user's full name.
     * @return Result containing the created User domain object, or an error.
     */
    suspend fun registerUser(email: String, password: String, fullName: String): Result<CoffeeUser>

    /**
     * Checks if an email address is already registered.
     * @param email The email address to check.
     * @return Result containing true if the email exists, false otherwise.
     */
    suspend fun emailExists(email: String): Result<Boolean>

    /**
     * Logs in a user with the given email and password.
     * @param email The user's email address.
     * @param password The user's password.
     * @return Result containing the authenticated FirebaseUser, or an error.
     */
    suspend fun loginUser(email: String, password: String): Result<FirebaseUser>

    /**
     * Logs out the current user.
     * @return Result indicating success or failure.
     */
    fun logoutUser(): Result<Unit>

    /**
     * Sends a password reset email to the given address.
     * @param email The email address to send the reset link to.
     * @return Result indicating success or failure.
     */
    suspend fun resetPassword(email: String): Result<Unit>

    /**
     * Observes the current authentication state.
     * Emits true if a user is logged in, false otherwise.
     * @return Flow emitting authentication state changes.
     */
    fun observeAuthState(): Flow<Boolean>

    // ----------------------------
    // User profile (Firestore)
    // ----------------------------

    /**
     * Gets the profile of a user by their UID.
     * @param userId The user's UID.
     * @return Result containing the User domain object, or null if not found.
     */
    suspend fun getUserProfile(userId: String): Result<CoffeeUser?>

    /**
     * Gets the profile of the currently authenticated user.
     * @return Result containing the User domain object, or null if not found or not logged in.
     */
    suspend fun getCurrentUserProfile(): Result<CoffeeUser?>

    /**
     * Observes real-time updates to a user's profile by UID.
     * @param userId The user's UID.
     * @return Flow emitting the User domain object, or null if not found.
     */
    fun observeUserProfile(userId: String): Flow<CoffeeUser?>

    /**
     * Updates a user's profile in Firestore.
     * @param userId The user's UID.
     * @param user The updated User domain object.
     * @return Result indicating success or failure.
     */
    suspend fun updateUserProfile(userId: String, user: CoffeeUser): Result<Unit>

    /**
     * Deletes the currently authenticated user's account and profile.
     * @return Result indicating success or failure.
     */
    suspend fun deleteUserAccount(): Result<Unit>
}