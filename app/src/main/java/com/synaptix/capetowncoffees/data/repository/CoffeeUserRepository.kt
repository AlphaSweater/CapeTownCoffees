package com.synaptix.capetowncoffees.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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
import kotlinx.coroutines.flow.distinctUntilChanged
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

    override suspend fun registerUser(
        email: String,
        password: String,
        fullName: String
    ): Result<CoffeeUser> =
        runCatching {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            authResult.user ?: error("Failed to create user")
        }.mapCatching { firebaseUser ->
            val dto = CoffeeUserDTO.newUserDTO(firebaseUser.uid, email, fullName)
            // propagate BaseRepository result as exception if failure
            create(dto, firebaseUser.uid).getOrThrow()
            dto.toDomain()
        }.recoverCatching { e ->
            // standardize error if needed (optional)
            throw e
        }

    override suspend fun emailExists(email: String): Result<Boolean> =
        getByField("email", email)
            .map { dto -> dto != null }
            .recoverCatching { e ->
                throw e
            }

    override suspend fun loginUser(email: String, password: String): Result<FirebaseUser> =
        runCatching {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            authResult.user ?: error("Failed to login user")
        }

    override fun logoutUser(): Result<Unit> = runCatching { auth.signOut() }

    override suspend fun resetPassword(email: String): Result<Unit> =
        runCatching { auth.sendPasswordResetEmail(email).await() }

    override fun observeAuthState(): Flow<Boolean> =
        callbackFlow {
            val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                trySend(firebaseAuth.currentUser != null)
            }
            auth.addAuthStateListener(listener)
            trySend(auth.currentUser != null)
            awaitClose { auth.removeAuthStateListener(listener) }
        }.distinctUntilChanged()


    override suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> =
        runCatching {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val user = authResult.user ?: error("Google sign-in returned no user")
            upsertUserProfileFromFirebase(user)
            user
        }
    // ----------------------------
    // User profile (Firestore)
    // ----------------------------

    override suspend fun getUserProfile(userId: String): Result<CoffeeUser?> =
        getById(userId).map { it?.toDomain() }

    override suspend fun getCurrentUserProfile(): Result<CoffeeUser?> =
        getCurrentUserId()
            ?.let { getUserProfile(it) }
            ?: Result.failure(IllegalStateException("No user logged in"))

    override fun observeUserProfile(userId: String): Flow<CoffeeUser?> =
        observeDocument(userId).map { it.getOrNull()?.toDomain() }

    override suspend fun updateUserProfile(userId: String, user: CoffeeUser): Result<Unit> =
        update(userId, user.copy(updatedAt = CoffeeTimeUtils.nowSeconds()).toDTO())

    override suspend fun deleteUserAccount(): Result<Unit> =
        runCatching {
            val user = auth.currentUser ?: error("No user logged in")
            // First delete Firestore doc
            delete(user.uid).getOrThrow()
            // Then delete Auth user
            user.delete().await()
        }

    //checks to see if user profile exists, if not creates one, if it does merges data
    private suspend fun upsertUserProfileFromFirebase(user: FirebaseUser) {
        runTransaction { tx ->
            val docRef = getCollection().document(user.uid)
            val snap   = tx.get(docRef)
            val existing = snap.toObject(CoffeeUserDTO::class.java)

            val merged = CoffeeUserDTO(
                id          = user.uid,
                email       = user.email ?: existing?.email.orEmpty(),
                fullName    = user.displayName ?: existing?.fullName,
                photoBase64 = existing?.photoBase64, // keep your locally-stored photo if any
                createdAt   = existing?.createdAt ?: CoffeeTimeUtils.nowSeconds(),
                updatedAt   = CoffeeTimeUtils.nowSeconds(),
                lastLoginAt = CoffeeTimeUtils.nowSeconds()
            )
            tx.set(docRef, merged, SetOptions.merge())
            true
        }.getOrThrow()
    }
}