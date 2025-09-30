package com.synaptix.capetowncoffees.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.synaptix.capetowncoffees.data.common.BaseRepository
import com.synaptix.capetowncoffees.data.model.UserDTO
import com.synaptix.capetowncoffees.domain.repository.IUserRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IUserRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    firestoreInstance: FirebaseFirestore
) : BaseRepository<UserDTO>(firestoreInstance), IUserRepository  {

    override val collection = firestoreInstance.collection("users")
    override fun getType(): Class<UserDTO> = UserDTO::class.java

    // Get current Firebase authenticated user
    override fun getCurrentUser(): FirebaseUser? = auth.currentUser

    // Get current user ID
    override fun getCurrentUserId(): String? = auth.currentUser?.uid

    // Register a new user
    override suspend fun registerUser(
        email: String,
        password: String,
        userData: UserDTO
    ): Result<UserDTO> {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user ?: throw Exception("Failed to create user")

            val newUser = userData.copy(
                id = firebaseUser.uid,
                email = email,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            create(newUser, firebaseUser.uid)
            Result.success(newUser)
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
    override suspend fun getUserProfile(userId: String): Result<UserDTO?> {
        return getById(userId)
    }

    // Get current user profile
    override suspend fun getCurrentUserProfile(): Result<UserDTO?> {
        val userId = getCurrentUserId() ?: return Result.failure(Exception("No user logged in"))
        return getUserProfile(userId)
    }

    // Observe user profile changes in real-time
    override fun observeUserProfile(userId: String): Flow<UserDTO?> {
        return observeDocument(userId)
    }

    // Update user profile with provided fields
    override suspend fun updateUserProfile(userId: String, user: UserDTO): Result<Unit> {
        return update(userId, user)
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
            val snapshot = collection
                .whereEqualTo("email", email)
                .get()
                .await()
            Result.success(!snapshot.isEmpty)
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