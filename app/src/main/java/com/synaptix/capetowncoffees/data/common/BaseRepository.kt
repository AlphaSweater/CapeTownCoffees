package com.synaptix.capetowncoffees.data.common

import com.google.firebase.firestore.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * BaseRepository provides generic Firestore CRUD, batch, transaction, observer, and pagination support for any entity type.
 *
 * @param firestore The Firestore instance.
 * @param parentCollection Optional parent collection name for subcollections.
 * @param parentDocumentId Optional parent document ID for subcollections.
 * @param childCollection The collection name for the entity type.
 */
abstract class BaseRepository<T : Any>(
    protected val firestore: FirebaseFirestore,
    private val parentCollection: String? = null,
    private val parentDocumentId: String? = null,
    private val childCollection: String
) {
    /**
     * Each repository must define its entity type for mapping and to know what type of repo it is.
     */
    protected abstract fun getType(): Class<T>

    /**
     * Reference to the collection for this repository, built dynamically if parent info is provided.
     */
    protected val collection: CollectionReference
        get() = getCollection()

    /**
     * Dynamically build the collection reference, supporting subcollections.
     */
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
    /**
     * Executes a query and maps results to entity type, with defensive mapping.
     */
    private suspend fun Query.getResults(limit: Int? = null): List<T> {
        var q = this
        if (limit != null) q = q.limit(limit.toLong())
        val snapshot = q.get().await()
        return snapshot.documents.mapNotNull { doc ->
            runCatching { doc.toObject(getType()) }.getOrNull()
        }
    }

    // --- 🔨 CRUD ---
    /**
     * Creates a new document for the entity, optionally with a custom ID and parent document.
     */
    protected suspend fun create(item: T, id: String? = null, parentDocId: String? = null): Result<String> =
        runCatching {
            val colRef = getCollection(parentDocId)
            val docRef = id?.let { colRef.document(it) } ?: colRef.document()
            docRef.set(item).await()
            docRef.id
        }

    /**
     * Updates the entire document for the entity.
     */
    protected suspend fun update(id: String, item: T, parentDocId: String? = null): Result<Unit> =
        runCatching {
            getCollection(parentDocId).document(id).set(item).await()
        }

    /**
     * Updates specific fields of the document for the entity.
     */
    protected suspend fun updateFields(id: String, fields: Map<String, Any>, parentDocId: String? = null): Result<Unit> =
        runCatching {
            getCollection(parentDocId).document(id).update(fields).await()
        }

    /**
     * Deletes the document for the entity.
     */
    protected suspend fun delete(id: String, parentDocId: String? = null): Result<Unit> =
        runCatching {
            getCollection(parentDocId).document(id).delete().await()
        }

    /**
     * Gets a document by ID for the entity.
     */
    protected suspend fun getById(id: String, parentDocId: String? = null): Result<T?> =
        runCatching {
            getCollection(parentDocId).document(id).get().await().toObject(getType())
        }

    /**
     * Gets all documents for the entity, optionally with a query and limit.
     */
    protected suspend fun getAll(query: Query? = null, limit: Int? = null, parentDocId: String? = null): Result<List<T>> =
        runCatching {
            (query ?: getCollection(parentDocId)).getResults(limit)
        }

    /**
     * Gets documents by a list of IDs for the entity.
     */
    protected suspend fun getItemsByIds(ids: List<String>, parentDocId: String? = null): Result<List<T>> =
        runCatching {
            if (ids.isEmpty()) return@runCatching emptyList()
            val colRef = getCollection(parentDocId)
            val items = mutableListOf<T>()
            ids.chunked(10).forEach { chunk ->
                val snapshots = chunk.map { id -> colRef.document(id).get().await() }
                items.addAll(snapshots.mapNotNull { doc ->
                    runCatching { doc.toObject(getType()) }.getOrNull()
                })
            }
            items
        }

    /**
     * Gets a document by field value for the entity.
     */
    protected suspend fun getByField(fieldName: String, value: Any, parentDocId: String? = null): Result<T?> =
        runCatching {
            val snapshot = getCollection(parentDocId)
                .whereEqualTo(fieldName, value)
                .limit(1)
                .get()
                .await()
            snapshot.documents.firstOrNull()?.toObject(getType())
        }

    /**
     * Gets all documents by field value for the entity, optionally with a limit.
     */
    protected suspend fun getAllByField(fieldName: String, value: Any, limit: Int? = null, parentDocId: String? = null): Result<List<T>> =
        runCatching {
            val query = getCollection(parentDocId).whereEqualTo(fieldName, value)
            query.getResults(limit)
        }

    /**
     * Gets all documents from a collection group, optionally with a query and limit.
     */
    protected suspend fun getAllFromCollectionGroup(
        childCollection: String,
        query: Query? = null,
        limit: Int? = null
    ): Result<List<T>> =
        runCatching {
            (query ?: firestore.collectionGroup(childCollection)).getResults(limit)
        }

    /**
     * Gets all documents by field value from a collection group, optionally with a limit.
     */
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
    /**
     * Observes a document for real-time updates, optionally under a parent document.
     * If parentDocId is null, observes at the root collection.
     */
    protected fun observeDocument(documentId: String, parentDocId: String? = null): Flow<Result<T?>> = callbackFlow {
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

    /**
     * Observes a query or collection for real-time updates, optionally under a parent document.
     * If parentDocId is null, observes at the root collection.
     */
    protected fun observeCollection(parentDocId: String? = null, query: Query? = null): Flow<Result<List<T>>> = callbackFlow {
        val finalQuery = query ?: getCollection(parentDocId)
        val listener = finalQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Result.failure(error))
                return@addSnapshotListener
            }
            val items = snapshot?.documents?.mapNotNull { doc ->
                runCatching { doc.toObject(getType()) }.getOrNull()
            } ?: emptyList()
            trySend(Result.success(items))
        }
        awaitClose { listener.remove() }
    }

    // --- ⚡ Batch & Transaction Support ---
    /**
     * Runs a Firestore batch operation.
     */
    protected suspend fun runBatch(actions: (WriteBatch) -> Unit): Result<Unit> =
        runCatching {
            firestore.runBatch { batch -> actions(batch) }.await()
        }

    /**
     * Runs a Firestore transaction.
     */
    protected suspend fun <R> runTransaction(actions: (Transaction) -> R): Result<R> =
        runCatching {
            firestore.runTransaction { tx -> actions(tx) }.await()
        }

    /**
     * Internal map of pagination cursors (DocumentSnapshot) for each independent pagination context.
     * Use the 'key' parameter in fetchPage/fetchPageFromCollectionGroup to manage multiple paginations (e.g., tabs, filters).
     */
    private val snapshotMap = mutableMapOf<String, DocumentSnapshot?>()
    private val collectionGroupSnapshotMap = mutableMapOf<String, DocumentSnapshot?>()

    /**
     * Fetches a page of results from the collection, supporting independent paginations via 'key'.
     *
     * @param pageSize Number of items per page.
     * @param parentDocId Optional parent document ID for subcollections.
     * @param reset If true, resets the pagination cursor for this key.
     * @param query Optional custom query.
     * @param orderBy Optional order by field and direction.
     * @param key Unique key for this pagination context (default: childCollection).
     * @return PaginatedResult<T> containing data and hasMore flag.
     */
    suspend fun fetchPage(
        pageSize: Int,
        parentDocId: String? = null,
        reset: Boolean = false,
        query: Query? = null,
        orderBy: Pair<String, Query.Direction>? = null,
        key: String = childCollection
    ): PaginatedResult<T> {
        if (reset) snapshotMap[key] = null

        var q = query ?: getCollection(parentDocId)
        orderBy?.let { q = q.orderBy(it.first, it.second) }
        q = q.limit(pageSize.toLong())

        snapshotMap[key]?.let { q = q.startAfter(it) }

        val snapshot = q.get().await()
        if (snapshot.isEmpty) return PaginatedResult(emptyList(), false)

        snapshotMap[key] = snapshot.documents.last()

        return PaginatedResult(
            data = snapshot.documents.mapNotNull { doc ->
                runCatching { doc.toObject(getType()) }.getOrNull()
            },
            hasMore = snapshot.size() == pageSize
        )
    }

    /**
     * Fetches a page of results from a Firestore collection group, supporting independent paginations via 'key'.
     *
     * @param childCollection The collection group name.
     * @param pageSize Number of items per page.
     * @param reset If true, resets the pagination cursor for this key.
     * @param query Optional custom query.
     * @param orderBy Optional order by field and direction.
     * @param key Unique key for this pagination context (default: childCollection).
     * @return PaginatedResult<T> containing data and hasMore flag.
     */
    suspend fun fetchPageFromCollectionGroup(
        childCollection: String,
        pageSize: Int,
        reset: Boolean = false,
        query: Query? = null,
        orderBy: Pair<String, Query.Direction>? = null,
        key: String = childCollection
    ): PaginatedResult<T> {
        if (reset) collectionGroupSnapshotMap[key] = null

        var q = query ?: firestore.collectionGroup(childCollection)
        orderBy?.let { q = q.orderBy(it.first, it.second) }
        q = q.limit(pageSize.toLong())

        collectionGroupSnapshotMap[key]?.let { q = q.startAfter(it) }

        val snapshot = q.get().await()
        if (snapshot.isEmpty) return PaginatedResult(emptyList(), false)

        collectionGroupSnapshotMap[key] = snapshot.documents.last()

        return PaginatedResult(
            data = snapshot.documents.mapNotNull { doc ->
                runCatching { doc.toObject(getType()) }.getOrNull()
            },
            hasMore = snapshot.size() == pageSize
        )
    }
}

/**
 * Wrapper for paginated results.
 * @param data The list of items for this page.
 * @param hasMore True if more data is available for further paging.
 */
data class PaginatedResult<T>(
    val data: List<T>,
    val hasMore: Boolean
)
