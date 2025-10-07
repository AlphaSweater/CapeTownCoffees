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

package com.synaptix.capetowncoffees.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.synaptix.capetowncoffees.data.common.BaseRepository
import com.synaptix.capetowncoffees.data.common.PaginatedResult
import com.synaptix.capetowncoffees.data.model.UserListDTO
import com.synaptix.capetowncoffees.data.model.toDomain
import com.synaptix.capetowncoffees.data.model.toDTO
import com.synaptix.capetowncoffees.domain.model.CoffeeList
import com.synaptix.capetowncoffees.domain.repository.ICoffeeListRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoffeeListRepository @Inject constructor(
    private val auth: FirebaseAuth,
    firestore: FirebaseFirestore
) : BaseRepository<UserListDTO>(
    firestore = firestore,
    parentCollection = "users",
    childCollection = "saved_lists"
), ICoffeeListRepository {

    override fun getType(): Class<UserListDTO> = UserListDTO::class.java

    // ----------------------------
    // CRUD
    // ----------------------------
    override suspend fun getLists(): Result<List<CoffeeList>> =
        getAll(parentDocId = currentUidOrNull())
            .map { list -> list.orEmpty().map { it.toDomain() } }

    override suspend fun createList(newCoffeeList: CoffeeList): Result<String> {
        val uid = currentUidOrNull() ?: return Result.failure(IllegalStateException("No user logged in"))
        return create(newCoffeeList.toDTO(), parentDocId = uid)
    }

    override suspend fun updateList(id: String, updatedCoffeeList: CoffeeList): Result<Unit> {
        val uid = currentUidOrNull() ?: return Result.failure(IllegalStateException("No user logged in"))
        return update(id, updatedCoffeeList.toDTO(), parentDocId = uid)
    }

    override suspend fun deleteList(id: String): Result<Unit> {
        val uid = currentUidOrNull() ?: return Result.failure(IllegalStateException("No user logged in"))
        return delete(id, parentDocId = uid)
    }

    override suspend fun getListById(id: String): Result<CoffeeList?> =
        getById(id, parentDocId = currentUidOrNull())
            .map { it?.toDomain() }

    // ----------------------------
    // Pagination
    // ----------------------------
    override suspend fun getListsPaginated(
        pageSize: Int,
        reset: Boolean,
        orderBy: Pair<String, Query.Direction>?
    ): PaginatedResult<CoffeeList> {
        val dtoPage = fetchPage(
            pageSize = pageSize,
            parentDocId = currentUidOrNull(),
            reset = reset,
            orderBy = orderBy
        )
        return PaginatedResult(
            data = dtoPage.data.map { it.toDomain() },
            hasMore = dtoPage.hasMore
        )
    }

    // ----------------------------
    // Observables
    // ----------------------------
    override fun observeLists(parentDocId: String): Flow<List<CoffeeList>> =
        observeCollection(parentDocId = parentDocId).map { result ->
            result.getOrElse { emptyList() }.map { it.toDomain() }
        }

    override fun observeList(id: String, parentDocId: String): Flow<CoffeeList?> =
        observeDocument(documentId = id, parentDocId = parentDocId).map { result ->
            result.getOrNull()?.toDomain()
        }

    // ----------------------------
    // Helpers
    // ----------------------------
    private fun currentUidOrNull(): String? = auth.currentUser?.uid
    override suspend fun addPlaceToLists(listIds: List<String>, placeId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("No user logged in"))
        if (listIds.isEmpty()) return Result.success(Unit)

        return try {
            val batch = firestore.batch()
            val col = firestore.collection("users").document(uid).collection("saved_lists")
            listIds.forEach { id ->
                batch.update(col.document(id), "placeIds", FieldValue.arrayUnion(placeId))
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
