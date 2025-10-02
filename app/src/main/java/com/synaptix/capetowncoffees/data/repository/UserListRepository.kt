package com.synaptix.capetowncoffees.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.synaptix.capetowncoffees.data.model.ListDTO
import com.synaptix.capetowncoffees.data.model.toDomain
import com.synaptix.capetowncoffees.data.model.toDTO
import com.synaptix.capetowncoffees.domain.model.UserList
import com.synaptix.capetowncoffees.domain.repository.IUserListRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserListRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : IUserListRepository {

    //creates list for currently signed in user as a subcollection of their user document
    val collection: CollectionReference
        get() {
            val userId = auth.currentUser?.uid ?: throw IllegalStateException("No User signed in")
            return firestore
                .collection("users")
                .document(userId)
                .collection("saved_lists")
        }

    override suspend fun getLists(): List<UserList> {
        Timber.d("Fetching saved lists from Firestore (DTO)")
        return try {
            val snapshot = collection.get().await()
            snapshot.documents.mapNotNull { it.toObject(ListDTO::class.java)?.toDomain() }
        } catch (e: Exception) {
            Timber.e("Error fetching saved lists: $e")
            emptyList()
        }
    }

    override suspend fun createList(
        newUserList: UserList
    ): String {
        return try {
            val id = collection.document().id
            val dto = newUserList.toDTO()
            Timber.d("Creating new list DTO: $dto")
            collection.document(id).set(dto).await()
            Timber.d("Successfully created list with ID: $id")
            id
        } catch (e: Exception) {
            Timber.e(e, "Failed to create list: ${e.message}")
            throw e
        }
    }

    override suspend fun deleteList(id: String) {
        Timber.d("Deleting saved list id=$id")
        collection.document(id).delete().await()
    }

    override fun observeLists(): Flow<List<UserList>> = callbackFlow {
        Timber.d("Setting up saved lists observation (DTO)")
        val listener = collection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val items = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(ListDTO::class.java)?.toDomain()
            } ?: emptyList()
            trySend(items)
            Timber.d("Received ${items.size} saved lists from Firestore")
        }
        awaitClose { listener.remove() }
    }

    override fun observeList(id: String): Flow<UserList?> = callbackFlow {
        Timber.d("Observing saved list id=$id (DTO)")
        val listener = collection.document(id).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val item = snapshot?.toObject(ListDTO::class.java)?.toDomain()
            Timber.d("Observed list: $item")
            trySend(item)
        }
        awaitClose { listener.remove() }
    }
}
