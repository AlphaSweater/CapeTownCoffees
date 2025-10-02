package com.synaptix.capetowncoffees.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.synaptix.capetowncoffees.data.common.BaseRepository
import com.synaptix.capetowncoffees.domain.model.List
import com.synaptix.capetowncoffees.domain.repository.IListRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListRepository @Inject constructor(
    private val auth: FirebaseAuth,
    firestore: FirebaseFirestore
) : BaseRepository<List>(firestore), IListRepository {

    //creates list for currently signed in user as a subcollection of their user document
    override val collection: CollectionReference
        get() {
            val userId = auth.currentUser?.uid ?: throw IllegalStateException("No User signed in")
            return firestore
                .collection("users")
                .document(userId)
                .collection("saved_lists")
        }

    override fun getType(): Class<List> = List::class.java
    
    override suspend fun getLists(): kotlin.collections.List<List> {
        Timber.d("Fetching saved lists from Firestore")
        return getAll().onSuccess { lists ->
            Timber.d("Successfully fetched ${lists.size} saved lists")
            if (lists.isNotEmpty()) {
                Timber.d("First list: ${lists[0]}")
            }
        }.getOrElse { 
            Timber.e("Error fetching saved lists: $it")
            emptyList() 
        }
    }

    override suspend fun createList(
        name: String,
        description: String?,
        isPublic: Boolean
    ): String {
        return try {
            val id = collection.document().id
            val item = List(
                id = id,
                name = name.trim(),
                description = description?.trim()?.takeIf { it.isNotBlank() },
                isPublic = isPublic,
                placeIds = emptyList()
            )
            Timber.d("Creating new list: $item")
            val result = create(item, id).getOrElse { throw it }
            Timber.d("Successfully created list with ID: $result")
            result
        } catch (e: Exception) {
            Timber.e(e, "Failed to create list: ${e.message}")
            throw e
        }
    }

    override suspend fun deleteList(id: String) {
        Timber.d("Deleting saved list id=$id")
        delete(id).getOrElse { throw it }
    }

    override fun observeLists(): Flow<kotlin.collections.List<List>> = callbackFlow {
        Timber.d("Setting up saved lists observation (with ids)")
        val listener = collection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val items = snapshot?.documents?.mapNotNull { doc ->
                val list = doc.toObject(List::class.java)?.copy(
                    id = doc.id,
                    // Ensure isPublic is properly set (default to false if null)
                    isPublic = doc.getBoolean("isPublic") ?: false
                )
                Timber.d("Mapped list: $list")
                list
            } ?: emptyList()
            trySend(items)
            Timber.d("Received ${items.size} saved lists from Firestore")
            items.forEach { Timber.d("List: $it") }
        }
        awaitClose { listener.remove() }
    }

    override fun observeList(id: String): Flow<List?> = callbackFlow {
        Timber.d("Observing saved list id=$id (with id)")
        val listener = collection.document(id).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val item = snapshot?.let { doc ->
                doc.toObject(List::class.java)?.copy(
                    id = doc.id,
                    // Ensure isPublic is properly set (default to false if null)
                    isPublic = doc.getBoolean("isPublic") ?: false
                )
            }
            Timber.d("Observed list: $item")
            trySend(item)
        }
        awaitClose { listener.remove() }
    }
}
