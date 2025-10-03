package com.synaptix.capetowncoffees.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.synaptix.capetowncoffees.data.common.BaseRepository
import com.synaptix.capetowncoffees.data.mapper.toDTO
import com.synaptix.capetowncoffees.data.mapper.toDomain
import com.synaptix.capetowncoffees.data.model.CoffeeUserDTO
import com.synaptix.capetowncoffees.domain.model.CoffeeUser
import com.synaptix.capetowncoffees.domain.repository.ICoffeeUserRepository
import com.synaptix.capetowncoffees.util.CoffeeTimeUtils
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoffeeUserRepository @Inject constructor(
    private val auth: FirebaseAuth,
    firestore: FirebaseFirestore
) : BaseRepository<CoffeeUserDTO>(
    firestore = firestore,
    childCollection = "users"
), ICoffeeUserRepository {

    override fun getType(): Class<CoffeeUserDTO> = CoffeeUserDTO::class.java

    // ----------------------------
    // Auth related
    // ----------------------------

    override fun getCurrentUser(): FirebaseUser? = auth.currentUser
    override fun getCurrentUserId(): String? = auth.currentUser?.uid

    override suspend fun registerUser(email: String, password: String, fullName: String): Result<CoffeeUser> {
        val authResult = auth.createUserWithEmailAndPassword(email, password).await()
        val firebaseUser = authResult.user ?: return Result.failure(Exception("Failed to create user"))
        val newCoffeeUserDTO = CoffeeUserDTO.newUserDTO(firebaseUser.uid, email, fullName)
        val createResult = create(newCoffeeUserDTO, firebaseUser.uid)
        return if (createResult.isSuccess) {
            Result.success(newCoffeeUserDTO.toDomain())
        } else {
            Result.failure(createResult.exceptionOrNull() ?: Exception("Failed to create user in Firestore"))
        }
    }

    override suspend fun emailExists(email: String): Result<Boolean> {
        val result = getByField("email", email)
        return if (result.isSuccess) {
            Result.success(result.getOrNull() != null)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Error checking email existence"))
        }
    }

    override suspend fun loginUser(email: String, password: String): Result<FirebaseUser> =
        runCatching {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            authResult.user ?: error("Failed to login user")
        }

    override fun logoutUser(): Result<Unit> = runCatching {
        auth.signOut()
    }

    override suspend fun resetPassword(email: String): Result<Unit> =
        runCatching { auth.sendPasswordResetEmail(email).await() }

    override fun observeAuthState(): Flow<Boolean> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser != null)
        }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser != null)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    // ----------------------------
    // User profile (Firestore)
    // ----------------------------

    override suspend fun getUserProfile(userId: String): Result<CoffeeUser?> {
        val result = getById(userId)
        return if (result.isSuccess) {
            Result.success(result.getOrNull()?.toDomain())
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Error fetching user profile"))
        }
    }

    override suspend fun getCurrentUserProfile(): Result<CoffeeUser?> {
        val userId = getCurrentUserId() ?: return Result.failure(Exception("No user logged in"))
        return getUserProfile(userId)
    }

    override fun observeUserProfile(userId: String): Flow<CoffeeUser?> =
        observeDocument(userId).map { it.getOrNull()?.toDomain() }

    override suspend fun updateUserProfile(userId: String, user: CoffeeUser): Result<Unit> {
        val updatedUser = user.copy(updatedAt = CoffeeTimeUtils.nowSeconds())
        return update(userId, updatedUser.toDTO())
    }

    override suspend fun deleteUserAccount(): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception("No user logged in"))
        val deleteResult = delete(user.uid)
        return if (deleteResult.isSuccess) {
            try {
                user.delete().await()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            Result.failure(deleteResult.exceptionOrNull() ?: Exception("Error deleting user document"))
        }
    }
}
