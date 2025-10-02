package com.synaptix.capetowncoffees.data.common

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

abstract class BaseRepository<T : Any>(
    protected val firestore: FirebaseFirestore,
    private val parentCollection: String? = null,
    private val parentDocumentId: String? = null,
    private val childCollection: String
) {
    protected val collection: CollectionReference
        get() = if (parentCollection != null && parentDocumentId != null) {
            firestore.collection(parentCollection)
                .document(parentDocumentId)
                .collection(childCollection)
        } else {
            firestore.collection(childCollection)
        }

    protected suspend fun create(item: T, id: String? = null): Result<String> {
        return try {
            val docRef = if (id != null) {
                collection.document(id)
            } else {
                collection.document()
            }
            docRef.set(item).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    protected suspend fun update(id: String, item: T): Result<Unit> {
        return try {
            collection.document(id).set(item).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    protected suspend fun delete(id: String): Result<Unit> {
        return try {
            collection.document(id).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    protected suspend fun getById(id: String): Result<T?> {
        return try {
            val snapshot = collection.document(id).get().await()
            Result.success(snapshot.toObject(getType()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    protected suspend fun getAll(query: Query = collection): Result<List<T>> {
        return try {
            val snapshot = query.get().await()
            val items = snapshot.documents.mapNotNull {
                it.toObject(getType())
            }
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    protected suspend fun getItemsByIds(ids: List<String>): Result<List<T>> {
        if (ids.isEmpty()) {
            return Result.success(emptyList())
        }
        return try {
            val items = mutableListOf<T>()
            ids.chunked(10).forEach { chunk ->
                val snapshots = chunk.map { id ->
                    collection.document(id).get().await()
                }
                items.addAll(snapshots.mapNotNull { it.toObject(getType()) })
            }
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Fetch the first document where a field equals a value
    protected suspend fun getByField(fieldName: String, value: Any): Result<T?> {
        return try {
            val query = collection.whereEqualTo(fieldName, value).limit(1)
            val snapshot = query.get().await()
            val item = snapshot.documents.firstOrNull()?.toObject(getType())
            Result.success(item)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Fetch all documents where a field equals a value, with optional limit
    protected suspend fun getAllByField(fieldName: String, value: Any, limit: Int? = null): Result<List<T>> {
        return try {
            var query = collection.whereEqualTo(fieldName, value)
            if (limit != null) {
                query = query.limit(limit.toLong())
            }
            val snapshot = query.get().await()
            val items = snapshot.documents.mapNotNull { it.toObject(getType()) }
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Real-time listener for a single document
    protected fun observeDocument(documentId: String): Flow<T?> = callbackFlow {
        val listener = collection.document(documentId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject(getType()))
            }
        awaitClose { listener.remove() }
    }

    // Real-time listener for a collection
    protected fun observeCollection(query: Query? = null): Flow<List<T>> =
        callbackFlow {
            val finalQuery = query ?: collection
            val listener = finalQuery
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    val items =
                        snapshot?.documents?.mapNotNull { it.toObject(getType()) } ?: emptyList()
                    trySend(items)
                }
            awaitClose { listener.remove() }
        }

    protected abstract fun getType(): Class<T>
}