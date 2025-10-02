package com.synaptix.capetowncoffees.domain.repository

import com.google.firebase.auth.FirebaseUser
import com.synaptix.capetowncoffees.domain.model.User
import kotlinx.coroutines.flow.Flow

interface IUserRepository {
    fun getCurrentUser(): FirebaseUser?
    fun getCurrentUserId(): String?
    suspend fun registerUser(email: String, password: String, fullName: String): Result<User>
    suspend fun loginUser(email: String, password: String): Result<FirebaseUser>
    fun logoutUser(): Result<Unit>
    suspend fun getUserProfile(userId: String): Result<User?>
    suspend fun getCurrentUserProfile(): Result<User?>
    fun observeUserProfile(userId: String): Flow<User?>
    suspend fun updateUserProfile(userId: String, user: User): Result<Unit>
    suspend fun deleteUserAccount(): Result<Unit>
    suspend fun emailExists(email: String): Result<Boolean>
    suspend fun resetPassword(email: String): Result<Unit>
    /**
     * Observe the current authentication state (true if logged in, false otherwise)
     */
    fun observeAuthState(): Flow<Boolean>
}