//======================================================================================
//Group 2 - Group Members:
//======================================================================================
//* Chad Fairlie ST10269509
//* Dhiren Ruthenavelu ST10256859
//* Kayla Ferreira ST10259527
//* Nathan Teixeira ST10249266
//======================================================================================
//References:
//======================================================================================
//* ChatGPT was used to clarify repository patterns, data source integration, and best
//practices for separating data access logic from UI components.
//* It also provided suggestions to improve maintainability and consistency.
//* It also helped generate useful comments
//======================================================================================

package com.synaptix.capetowncoffees.data.common

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Transaction
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

// ─────────── Base type & construction ───────────
// Generic Firestore base repo. We centralize CRUD, queries, realtime listeners,
// and cursor-based pagination so feature repos stay small and consistent.
abstract class BaseRepository<T : Any>(
    protected val firestore: FirebaseFirestore,
    private val parentCollection: String? = null,
    private val parentDocumentId: String? = null,
    private val childCollection: String
) {
    // Concrete repos return their entity Class so Firestore can map objects.
    protected abstract fun getType(): Class<T>

    // Shortcut to the resolved collection (root or subcollection).
    protected val collection: CollectionReference
        get() = getCollection()

    // ─────────── Collection helpers ───────────
    // Resolves root collection or /{parent}/{id}/{child} subcollection.
    protected fun getCollection(parentDocId: String? = null): CollectionReference {
        val parentId = parentDocId ?: parentDocumentId
        return if (parentCollection != null && parentId != null) {
            firestore
                .collection(parentCollection)
                .document(parentId)
                .collection(childCollection)
        } else {
            firestore.collection(childCollection)
        }
    }

    // ─────────── Query helpers (internal) ───────────
    // Runs the query, applies optional max, maps defensively (skips bad docs).
    private suspend fun Query.getResults(max: Int? = null): List<T> {
        val q = if (max != null) this.limit(max.toLong()) else this
        val snapshot = q.get().await()
        return snapshot.documents.mapNotNull { doc ->
            runCatching { doc.toObject(getType()) }.getOrNull()
        }
    }

    // ─────────── CRUD (protected) ───────────

    // Create a new document (optional custom id). Returns created id.
    protected suspend fun create(
        item: T,
        id: String? = null,
        parentDocId: String? = null
    ): Result<String> = runCatching {
        val colRef = getCollection(parentDocId)
        val docRef = id?.let { colRef.document(it) } ?: colRef.document()
        docRef.set(item).await()
        docRef.id
    }

    // Overwrite a document with the given payload.
    protected suspend fun update(
        id: String,
        item: T,
        parentDocId: String? = null
    ): Result<Unit> = runCatching {
        getCollection(parentDocId).document(id).set(item).await()
    }

    // Patch specific fields on a document.
    protected suspend fun updateFields(
        id: String,
        fields: Map<String, Any>,
        parentDocId: String? = null
    ): Result<Unit> = runCatching {
        getCollection(parentDocId).document(id).update(fields).await()
    }

    // Delete a document by id.
    protected suspend fun delete(
        id: String,
        parentDocId: String? = null
    ): Result<Unit> = runCatching {
        getCollection(parentDocId).document(id).delete().await()
    }

    // Read a single document. Returns null when missing.
    protected suspend fun getById(
        id: String,
        parentDocId: String? = null
    ): Result<T?> = runCatching {
        getCollection(parentDocId).document(id).get().await().toObject(getType())
    }

    // Read all documents (or a custom query). Optional limit.
    protected suspend fun getAll(
        query: Query? = null,
        limit: Int? = null,
        parentDocId: String? = null
    ): Result<List<T>> = runCatching {
        (query ?: getCollection(parentDocId)).getResults(limit)
    }

    // Read by explicit ids in small chunks (avoids large 'in' constraints).
    protected suspend fun getItemsByIds(
        ids: List<String>,
        parentDocId: String? = null
    ): Result<List<T>> = runCatching {
        if (ids.isEmpty()) return@runCatching emptyList()
        val colRef = getCollection(parentDocId)
        val items = mutableListOf<T>()
        val chunkSize = 10 // small, safe batch size for reads
        ids.chunked(chunkSize).forEach { chunk ->
            val snapshots = chunk.map { id -> colRef.document(id).get().await() }
            items.addAll(
                snapshots.mapNotNull { doc ->
                    runCatching { doc.toObject(getType()) }.getOrNull()
                }
            )
        }
        items
    }

    // First document where field == value, else null.
    protected suspend fun getByField(
        fieldName: String,
        value: Any,
        parentDocId: String? = null
    ): Result<T?> = runCatching {
        val snapshot = getCollection(parentDocId)
            .whereEqualTo(fieldName, value)
            .limit(1)
            .get()
            .await()
        snapshot.documents.firstOrNull()?.toObject(getType())
    }

    // All documents where field == value (optional limit).
    protected suspend fun getAllByField(
        fieldName: String,
        value: Any,
        limit: Int? = null,
        parentDocId: String? = null
    ): Result<List<T>> = runCatching {
        val q = getCollection(parentDocId).whereEqualTo(fieldName, value)
        q.getResults(limit)
    }

    // ─────────── Collection group queries (protected) ───────────

    // Read from a collection group (shared subcollection name).
    protected suspend fun getAllFromCollectionGroup(
        childCollection: String,
        query: Query? = null,
        limit: Int? = null
    ): Result<List<T>> = runCatching {
        (query ?: firestore.collectionGroup(childCollection)).getResults(limit)
    }

    // Read from a collection group with a simple equality filter.
    protected suspend fun getAllByFieldFromCollectionGroup(
        childCollection: String,
        fieldName: String,
        value: Any,
        limit: Int? = null
    ): Result<List<T>> = runCatching {
        firestore
            .collectionGroup(childCollection)
            .whereEqualTo(fieldName, value)
            .getResults(limit)
    }

    // ─────────── Realtime observers (protected) ───────────

    // Listen to a single document; emits Result<T?> on each change or error.
    protected fun observeDocument(
        documentId: String,
        parentDocId: String? = null
    ): Flow<Result<T?>> = callbackFlow {
        val docRef = getCollection(parentDocId).document(documentId)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Result.failure(error))
                return@addSnapshotListener
            }
            trySend(Result.success(snapshot?.toObject(getType())))
        }
        awaitClose { listener.remove() }
    }

    // Listen to a collection or custom query; skips malformed docs.
    protected fun observeCollection(
        parentDocId: String? = null,
        query: Query? = null
    ): Flow<Result<List<T>>> = callbackFlow {
        val finalQuery = query ?: getCollection(parentDocId)
        val listener = finalQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Result.failure(error))
                return@addSnapshotListener
            }
            val items = snapshot?.documents?.mapNotNull { doc ->
                runCatching { doc.toObject(getType()) }.getOrNull()
            }.orEmpty()
            trySend(Result.success(items))
        }
        awaitClose { listener.remove() }
    }

    // ─────────── Batch & transaction (protected) ───────────

    // Run a write batch.
    protected suspend fun runBatch(
        actions: (WriteBatch) -> Unit
    ): Result<Unit> = runCatching {
        firestore.runBatch { batch -> actions(batch) }.await()
    }

    // Run a transaction and return its computed result.
    protected suspend fun <R> runTransaction(
        actions: (Transaction) -> R
    ): Result<R> = runCatching {
        firestore.runTransaction { tx -> actions(tx) }.await()
    }

    // ─────────── Pagination (public) ───────────
    // We keep per-key cursors so multiple tabs/filters can paginate independently.

    private val snapshotMap: MutableMap<String, DocumentSnapshot?> = mutableMapOf()
    private val collectionGroupSnapshotMap: MutableMap<String, DocumentSnapshot?> = mutableMapOf()

    // Page through this repo's collection using a per-key cursor.
    suspend fun fetchPage(
        pageSize: Int,
        parentDocId: String? = null,
        reset: Boolean = false,
        query: Query? = null,
        orderBy: Pair<String, Query.Direction>? = null,
        key: String = childCollection
    ): PaginatedResult<T> {
        if (reset) snapshotMap[key] = null

        var q: Query = (query ?: getCollection(parentDocId)).let { base ->
            orderBy?.let { base.orderBy(it.first, it.second) } ?: base
        }.limit(pageSize.toLong())

        snapshotMap[key]?.let { cursor -> q = q.startAfter(cursor) }

        val snapshot = q.get().await()
        if (snapshot.isEmpty) return PaginatedResult(emptyList(), false)

        snapshotMap[key] = snapshot.documents.last()

        val data = snapshot.documents.mapNotNull { doc ->
            runCatching { doc.toObject(getType()) }.getOrNull()
        }
        val hasMore = snapshot.size() == pageSize
        return PaginatedResult(data = data, hasMore = hasMore)
    }

    // Page through a collection group using a per-key cursor.
    suspend fun fetchPageFromCollectionGroup(
        childCollection: String,
        pageSize: Int,
        reset: Boolean = false,
        query: Query? = null,
        orderBy: Pair<String, Query.Direction>? = null,
        key: String = childCollection
    ): PaginatedResult<T> {
        if (reset) collectionGroupSnapshotMap[key] = null

        var q: Query = (query ?: firestore.collectionGroup(childCollection)).let { base ->
            orderBy?.let { base.orderBy(it.first, it.second) } ?: base
        }.limit(pageSize.toLong())

        collectionGroupSnapshotMap[key]?.let { cursor -> q = q.startAfter(cursor) }

        val snapshot = q.get().await()
        if (snapshot.isEmpty) return PaginatedResult(emptyList(), false)

        collectionGroupSnapshotMap[key] = snapshot.documents.last()

        val data = snapshot.documents.mapNotNull { doc ->
            runCatching { doc.toObject(getType()) }.getOrNull()
        }
        val hasMore = snapshot.size() == pageSize
        return PaginatedResult(data = data, hasMore = hasMore)
    }
}

// ─────────── Pagination model ───────────
// Single page of results. When hasMore is true, we fetch again with the same key.
data class PaginatedResult<T>(
    val data: List<T>,
    val hasMore: Boolean
)
