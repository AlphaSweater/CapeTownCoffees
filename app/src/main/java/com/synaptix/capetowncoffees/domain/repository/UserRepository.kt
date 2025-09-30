package com.synaptix.capetowncoffees.domain.repository

import com.google.firebase.auth.FirebaseUser
import com.synaptix.capetowncoffees.data.model.UserDTO
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun getCurrentUser(): FirebaseUser?
    fun getCurrentUserId(): String?
    suspend fun registerUser(email: String, password: String, userData: UserDTO): Result<UserDTO>
    suspend fun loginUser(email: String, password: String): Result<FirebaseUser>
    fun logoutUser(): Result<Unit>
    suspend fun getUserProfile(userId: String): Result<UserDTO?>
    suspend fun getCurrentUserProfile(): Result<UserDTO?>
    fun observeUserProfile(userId: String): Flow<UserDTO?>
    suspend fun updateUserProfile(userId: String, user: UserDTO): Result<Unit>
    suspend fun deleteUserAccount(): Result<Unit>
    suspend fun emailExists(email: String): Result<Boolean>
    suspend fun resetPassword(email: String): Result<Unit>
}