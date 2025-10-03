package com.synaptix.capetowncoffees.data.common

import com.google.firebase.firestore.*
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
        get() = getCollection()

    // Dynamically build the collection reference
    protected fun getCollection(parentDocId: String? = null): CollectionReference {
        return if (parentCollection != null && (parentDocId ?: parentDocumentId) != null) {
            firestore.collection(parentCollection)
                .document(parentDocId ?: parentDocumentId!!)
                .collection(childCollection)
        } else {
            firestore.collection(childCollection)
        }
    }

    // --- 🔧 Helpers ---
    private suspend fun Query.getResults(limit: Int? = null): List<T> {
        var q = this
        if (limit != null) q = q.limit(limit.toLong())
        val snapshot = q.get().await()
        return snapshot.documents.mapNotNull { it.toObject(getType()) }
    }

    // --- 🔨 CRUD ---
    protected suspend fun create(item: T, id: String? = null, parentDocId: String? = null): Result<String> =
        runCatching {
            val colRef = getCollection(parentDocId)
            val docRef = id?.let { colRef.document(it) } ?: colRef.document()
            docRef.set(item).await()
            docRef.id
        }

    protected suspend fun update(id: String, item: T, parentDocId: String? = null): Result<Unit> =
        runCatching {
            getCollection(parentDocId).document(id).set(item).await()
        }

    protected suspend fun updateFields(id: String, fields: Map<String, Any>, parentDocId: String? = null): Result<Unit> =
        runCatching {
            getCollection(parentDocId).document(id).update(fields).await()
        }

    protected suspend fun delete(id: String, parentDocId: String? = null): Result<Unit> =
        runCatching {
            getCollection(parentDocId).document(id).delete().await()
        }

    protected suspend fun getById(id: String, parentDocId: String? = null): Result<T?> =
        runCatching {
            getCollection(parentDocId).document(id).get().await().toObject(getType())
        }

    protected suspend fun getAll(query: Query? = null, limit: Int? = null, parentDocId: String? = null): Result<List<T>> =
        runCatching {
            (query ?: getCollection(parentDocId)).getResults(limit)
        }

    protected suspend fun getItemsByIds(ids: List<String>, parentDocId: String? = null): Result<List<T>> =
        runCatching {
            if (ids.isEmpty()) return@runCatching emptyList()
            val colRef = getCollection(parentDocId)
            val items = mutableListOf<T>()
            ids.chunked(10).forEach { chunk ->
                val snapshots = chunk.map { id -> colRef.document(id).get().await() }
                items.addAll(snapshots.mapNotNull { it.toObject(getType()) })
            }
            items
        }

    protected suspend fun getByField(fieldName: String, value: Any, parentDocId: String? = null): Result<T?> =
        runCatching {
            val snapshot = getCollection(parentDocId)
                .whereEqualTo(fieldName, value)
                .limit(1)
                .get()
                .await()
            snapshot.documents.firstOrNull()?.toObject(getType())
        }

    protected suspend fun getAllByField(fieldName: String, value: Any, limit: Int? = null, parentDocId: String? = null): Result<List<T>> =
        runCatching {
            val query = getCollection(parentDocId).whereEqualTo(fieldName, value)
            query.getResults(limit)
        }

    protected suspend fun getAllFromCollectionGroup(
        childCollection: String,
        query: Query? = null,
        limit: Int? = null
    ): Result<List<T>> =
        runCatching {
            (query ?: firestore.collectionGroup(childCollection)).getResults(limit)
        }

    protected suspend fun getAllByFieldFromCollectionGroup(
        childCollection: String,
        fieldName: String,
        value: Any,
        limit: Int? = null
    ): Result<List<T>> =
        runCatching {
            firestore.collectionGroup(childCollection)
                .whereEqualTo(fieldName, value)
                .getResults(limit)
        }

    // --- 🔄 Realtime Observers ---
    protected fun observeDocument(documentId: String): Flow<Result<T?>> = callbackFlow {
        val listener = collection.document(documentId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                trySend(Result.success(snapshot?.toObject(getType())))
            }
        awaitClose { listener.remove() }
    }

    protected fun observeCollection(query: Query? = null): Flow<Result<List<T>>> = callbackFlow {
        val finalQuery = query ?: collection
        val listener = finalQuery
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { it.toObject(getType()) } ?: emptyList()
                trySend(Result.success(items))
            }
        awaitClose { listener.remove() }
    }

    // --- ⚡ Batch & Transaction Support ---
    protected suspend fun runBatch(actions: (WriteBatch) -> Unit): Result<Unit> =
        runCatching {
            firestore.runBatch { batch -> actions(batch) }.await()
        }

    protected suspend fun <R> runTransaction(actions: (Transaction) -> R): Result<R> =
        runCatching {
            firestore.runTransaction { tx -> actions(tx) }.await()
        }

    // --- ⏭ Pagination helper (cursor) ---
    protected suspend fun getPage(
        pageSize: Int,
        lastSnapshot: DocumentSnapshot? = null,
        parentDocId: String? = null
    ): Result<List<T>> =
        runCatching {
            var query: Query = getCollection(parentDocId).limit(pageSize.toLong())
            if (lastSnapshot != null) query = query.startAfter(lastSnapshot)
            query.getResults()
        }

    // Each repository defines its entity type
    protected abstract fun getType(): Class<T>
}
