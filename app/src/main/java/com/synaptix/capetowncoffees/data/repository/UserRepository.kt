package com.synaptix.capetowncoffees.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.synaptix.capetowncoffees.data.common.BaseRepository
import com.synaptix.capetowncoffees.data.model.UserDTO
import com.synaptix.capetowncoffees.data.mapper.toDTO
import com.synaptix.capetowncoffees.data.mapper.toDomain
import com.synaptix.capetowncoffees.domain.repository.IUserRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.synaptix.capetowncoffees.domain.model.User
import com.synaptix.capetowncoffees.util.TimeUtils

@Singleton
class UserRepository @Inject constructor(
    private val auth: FirebaseAuth,
    firestoreInstance: FirebaseFirestore
) : BaseRepository<UserDTO>(
    firestore = firestoreInstance,
    childCollection = "users"
), IUserRepository {

    override fun getType(): Class<UserDTO> = UserDTO::class.java

    // Get current Firebase authenticated user
    override fun getCurrentUser(): FirebaseUser? = auth.currentUser

    // Get current user ID
    override fun getCurrentUserId(): String? = auth.currentUser?.uid

    // Register a new user
    override suspend fun registerUser(
        email: String,
        password: String,
        fullName: String
    ): Result<User> {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user ?: throw Exception("Failed to create user")

            // Create new user dto
            val newUserDTO = UserDTO.newUserDTO(firebaseUser.uid, email, fullName)

            create(newUserDTO, firebaseUser.uid)
            Result.success(newUserDTO.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Login existing user
    override suspend fun loginUser(email: String, password: String): Result<FirebaseUser> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user ?: throw Exception("Failed to login user")
            Result.success(firebaseUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Logout current user
    override fun logoutUser(): Result<Unit> {
        return try {
            auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Get user profile
    override suspend fun getUserProfile(userId: String): Result<User?> {
        return try {
            val dtoResult = getById(userId)
            if (dtoResult.isSuccess) {
                Result.success(dtoResult.getOrNull()?.toDomain())
            } else {
                Result.failure(dtoResult.exceptionOrNull() ?: Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Get current user profile
    override suspend fun getCurrentUserProfile(): Result<User?> {
        val userId = getCurrentUserId() ?: return Result.failure(Exception("No user logged in"))
        return getUserProfile(userId)
    }

    // Observe user profile changes in real-time
    override fun observeUserProfile(userId: String): Flow<User?> {
        return observeDocument(userId).map { it?.toDomain() }
    }

    // Update user profile with provided fields
    override suspend fun updateUserProfile(userId: String, user: User): Result<Unit> {
        val updatedUser = user.copy(updatedAt = TimeUtils.nowSeconds())
        return update(userId, updatedUser.toDTO())
    }

    // Delete current user's account
    override suspend fun deleteUserAccount(): Result<Unit> {
        return try {
            val user = auth.currentUser ?: throw Exception("No user logged in")

            // Delete Firestore document
            delete(user.uid)

            // Delete from Firebase Auth
            user.delete().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Check if email is already registered
    override suspend fun emailExists(email: String): Result<Boolean> {
        return try {
            val result = auth.fetchSignInMethodsForEmail(email).await()
            Result.success(result.signInMethods?.isNotEmpty() == true)
        } catch (e: FirebaseAuthInvalidUserException) {
            // No user found with this email
            Result.success(false)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Send password reset email
    override suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe the current authentication state (true if logged in, false otherwise)
     */
    override fun observeAuthState(): Flow<Boolean> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser != null)
        }
        auth.addAuthStateListener(listener)
        // Emit initial state
        trySend(auth.currentUser != null)
        awaitClose { auth.removeAuthStateListener(listener) }
    }
}