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

import com.google.firebase.firestore.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/* ──────────────────────────────────────────────────────────────────────────────
 * BASE REPOSITORY — CONTRACT & CONSTRUCTION
 * ────────────────────────────────────────────────────────────────────────────── */

/**
 * Generic Firestore repository with CRUD, queries, realtime observers, batching,
 * transactions, and cursor-based pagination.
 *
 * When to use it: extend in feature repos to avoid duplicating Firestore boilerplate.
 * Key rule: pass `parentCollection/parentDocumentId/childCollection` if you store entities
 * in a subcollection; otherwise just `childCollection`.
 *
 * Details:
 * - Mapping is defensive: bad documents are skipped via `runCatching { toObject(...) }`.
 * - All I/O is `suspend` and uses `await()`; errors surface as `Result` where applicable.
 * - Pagination is cursor-based with `startAfter(lastSnapshot)` and per-key cursors.
 *
 * Edge cases:
 * - Subcollections: if both `parentCollection` and a (provided or stored) parent doc id
 *   are present, operations target `/{parentCollection}/{parentDocId}/{childCollection}`.
 * - Missing/invalid docs: mapping failures do not crash the flow; items are omitted.
 *
 * Gotchas:
 * - You must implement [getType] so `toObject(T::class.java)` knows the entity class.
 * - Don’t mix different queries under the same pagination `key`; cursors won’t match.
 *
 * ### Examples
 * ```kotlin
 * // Example repository
 * @Singleton
 * class CoffeeListRepository @Inject Constructor(
 *   firestore: FirebaseFirestore
 * ) : BaseRepository<UserListDTO>(
 *       firestore, parentCollection = "users", childCollection = "saved_lists"
 *   ) {
 *   override fun getType(): Class<UserListDTO> = UserListDTO::class.java
 * }
 *
 * // Usage inside the repo (protected APIs)
 * suspend fun createList(dto: UserListDTO, userId: String) =
 *   create(item = dto, parentDocId = userId)  // Result<String> (doc id)
 *
 * suspend fun firstPage(userId: String) =
 *   fetchPage(pageSize = 20, parentDocId = userId) // PaginatedResult<UserListDTO>
 * ```
 *
 * @param firestore Firestore instance to use.
 * @param parentCollection Optional parent collection name for subcollections.
 * @param parentDocumentId Optional parent document id for subcollections.
 * @param childCollection Target collection (or subcollection) name for this entity type.
 */
abstract class BaseRepository<T : Any>(
    protected val firestore: FirebaseFirestore,
    private val parentCollection: String? = null,
    private val parentDocumentId: String? = null,
    private val childCollection: String
) {
    /**
     * Concrete repos must return their entity `Class` for Firestore mapping.
     *
     * @return The Java `Class<T>` used by `toObject`.
     */
    protected abstract fun getType(): Class<T>

    /** Lazily resolves the collection for this repository (root or subcollection). */
    protected val collection: CollectionReference
        get() = getCollection()

    /**
     * Builds the collection reference, optionally resolving a subcollection.
     *
     * Details:
     * - If both `parentCollection` and a parent id are set (parameter or stored), returns the
     *   subcollection. Otherwise returns `firestore.collection(childCollection)`.
     *
     * @param parentDocId Optional parent id overriding the stored `parentDocumentId`.
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

    /* ──────────────────────────────────────────────────────────────────────────
     * QUERY HELPERS (PRIVATE)
     * ────────────────────────────────────────────────────────────────────────── */

    /** Executes the query, applies optional `limit`, maps to `T` (silently skips failures). */
    private suspend fun Query.getResults(limit: Int? = null): List<T> {
        var q = this
        if (limit != null) q = q.limit(limit.toLong())
        val snapshot = q.get().await()
        return snapshot.documents.mapNotNull { doc ->
            runCatching { doc.toObject(getType()) }.getOrNull()
        }
    }

    /* ──────────────────────────────────────────────────────────────────────────
     * CRUD (PROTECTED)
     * ────────────────────────────────────────────────────────────────────────── */

    /**
     * Creates a document; returns its id in a `Result`.
     *
     * Details:
     * - If `id` is null a new document id is generated.
     * - Supports subcollections via `parentDocId`.
     *
     * Edge cases:
     * - Write failures surface as `Result.failure`.
     */
    protected suspend fun create(item: T, id: String? = null, parentDocId: String? = null): Result<String> =
        runCatching {
            val colRef = getCollection(parentDocId)
            val docRef = id?.let { colRef.document(it) } ?: colRef.document()
            docRef.set(item).await()
            docRef.id
        }

    /**
     * Replaces a document with `item` (upsert-style).
     * Use when you want to overwrite the full payload.
     */
    protected suspend fun update(id: String, item: T, parentDocId: String? = null): Result<Unit> =
        runCatching {
            getCollection(parentDocId).document(id).set(item).await()
        }

    /**
     * Partially updates a document with field map.
     * Prefer this for targeted changes to reduce write size/costs.
     */
    protected suspend fun updateFields(id: String, fields: Map<String, Any>, parentDocId: String? = null): Result<Unit> =
        runCatching {
            getCollection(parentDocId).document(id).update(fields).await()
        }

    /** Deletes the document with the given id. */
    protected suspend fun delete(id: String, parentDocId: String? = null): Result<Unit> =
        runCatching {
            getCollection(parentDocId).document(id).delete().await()
        }

    /**
     * Fetches a single document by id and maps it to `T?`.
     * Returns `Result.success(null)` if the doc is missing.
     */
    protected suspend fun getById(id: String, parentDocId: String? = null): Result<T?> =
        runCatching {
            getCollection(parentDocId).document(id).get().await().toObject(getType())
        }

    /**
     * Returns all documents (or those from a custom `query`) with optional `limit`.
     *
     * Gotchas:
     * - When supplying a custom `query`, ensure it targets the same collection/subcollection.
     */
    protected suspend fun getAll(query: Query? = null, limit: Int? = null, parentDocId: String? = null): Result<List<T>> =
        runCatching {
            (query ?: getCollection(parentDocId)).getResults(limit)
        }

    /**
     * Fetches documents by explicit ids (chunks of 10) and maps them to `T`.
     * Missing or malformed docs are skipped.
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
     * Gets the first document where `fieldName == value`, or `null` if none.
     * Efficient for unique-key lookups (ensure an index if needed).
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
     * Returns all documents where `fieldName == value`, with optional `limit`.
     */
    protected suspend fun getAllByField(fieldName: String, value: Any, limit: Int? = null, parentDocId: String? = null): Result<List<T>> =
        runCatching {
            val query = getCollection(parentDocId).whereEqualTo(fieldName, value)
            query.getResults(limit)
        }

    /* ──────────────────────────────────────────────────────────────────────────
     * COLLECTION GROUP QUERIES (PROTECTED)
     * ────────────────────────────────────────────────────────────────────────── */

    /**
     * Reads from a collection group (same subcollection name across parents).
     * Optionally applies a custom `query` and `limit`.
     *
     * Gotchas:
     * - Composite indexes are often required; create them if Firestore prompts you.
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
     * Reads from a collection group where `fieldName == value`, with optional `limit`.
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

    /* ──────────────────────────────────────────────────────────────────────────
     * REALTIME OBSERVERS (PROTECTED)
     * ────────────────────────────────────────────────────────────────────────── */

    /**
     * Observes a document for realtime updates, returning `Flow<Result<T?>>`.
     * Emits failures from the listener, or `null` when the doc is missing.
     *
     * Details:
     * - Snapshot mapping is defensive; bad payloads emit `Result.success(null)` rather than crash.
     *
     * ### Examples
     * ```kotlin
     * // Inside a repo
     * fun observeList(id: String, userId: String) =
     *   observeDocument(documentId = id, parentDocId = userId) // Flow<Result<UserListDTO?>>
     * ```
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
     * Observes a collection or custom query, returning `Flow<Result<List<T>>>`.
     * Emits failures from the listener; malformed docs are skipped.
     *
     * ### Examples
     * ```kotlin
     * // Observe all for a user
     * fun observeMine(userId: String) =
     *   observeCollection(parentDocId = userId)
     * ```
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

    /* ──────────────────────────────────────────────────────────────────────────
     * BATCH & TRANSACTION (PROTECTED)
     * ────────────────────────────────────────────────────────────────────────── */

    /** Runs a Firestore write batch and returns `Result<Unit>`. */
    protected suspend fun runBatch(actions: (WriteBatch) -> Unit): Result<Unit> =
        runCatching {
            firestore.runBatch { batch -> actions(batch) }.await()
        }

    /** Runs a Firestore transaction and returns its result in `Result<R>`. */
    protected suspend fun <R> runTransaction(actions: (Transaction) -> R): Result<R> =
        runCatching {
            firestore.runTransaction { tx -> actions(tx) }.await()
        }

    /* ──────────────────────────────────────────────────────────────────────────
     * PAGINATION (PUBLIC API SURFACE OF THE REPO CLASS)
     * ────────────────────────────────────────────────────────────────────────── */

    /** In-memory cursor per pagination `key` (collection-level). */
    private val snapshotMap = mutableMapOf<String, DocumentSnapshot?>()

    /** In-memory cursor per pagination `key` (collection-group level). */
    private val collectionGroupSnapshotMap = mutableMapOf<String, DocumentSnapshot?>()

    /**
     * Fetches a page from this repository’s collection using a per-`key` cursor.
     *
     * Details:
     * - Uses `orderBy` when provided; otherwise Firestore default ordering applies.
     * - Call with `reset=true` to restart from the first page for that `key`.
     * - `hasMore` is true when `snapshot.size == pageSize`; fetch again to continue.
     *
     * Edge cases:
     * - Empty page returns `PaginatedResult(emptyList(), false)` and **does not** advance the cursor.
     *
     * Gotchas:
     * - Keep `key` stable per logical pagination stream (e.g., tab or filter). Don’t reuse a key
     *   for different queries or cursors will be inconsistent.
     *
     * ### Examples
     * ```kotlin
     * // First page
     * val page1 = fetchPage(pageSize = 20, parentDocId = userId, reset = true)
     *
     * // Next page
     * val page2 = fetchPage(pageSize = 20, parentDocId = userId)
     * ```
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
     * Fetches a page from a **collection group** using a per-`key` cursor.
     *
     * Details:
     * - Behaves like [fetchPage] but operates on `firestore.collectionGroup(childCollection)`.
     * - `orderBy` should match an index for performance (create if Firestore prompts).
     *
     * ### Examples
     * ```kotlin
     * val page = fetchPageFromCollectionGroup(
     *   childCollection = "reviews",
     *   pageSize = 50,
     *   orderBy = "publishTime" to Query.Direction.DESCENDING,
     *   key = "global-reviews"
     * )
     * ```
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

/* ──────────────────────────────────────────────────────────────────────────────
 * PAGINATION — MODEL
 * ────────────────────────────────────────────────────────────────────────────── */

/**
 * Page of items with a flag indicating whether more data can be fetched.
 *
 * Details:
 * - `hasMore == true` implies you should call the same pagination method again to continue.
 * - Empty pages always set `hasMore = false`.
 */
data class PaginatedResult<T>(
    val data: List<T>,
    val hasMore: Boolean
)
