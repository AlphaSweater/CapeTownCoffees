package com.synaptix.capetowncoffees.data.repository

import com.google.firebase.auth.FirebaseAuth
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
    override suspend fun getLists(): Result<List<CoffeeList>> {
        val dtoResult = getAll(parentDocId = auth.currentUser?.uid)
        return if (dtoResult.isSuccess) {
            Result.success(dtoResult.getOrNull()?.map { it.toDomain() } ?: emptyList())
        } else {
            Result.failure(dtoResult.exceptionOrNull() ?: Exception("Error fetching coffee lists"))
        }
    }

    override suspend fun createList(newCoffeeList: CoffeeList): Result<String> {
        return try {
            create(newCoffeeList.toDTO(), parentDocId = auth.currentUser?.uid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateList(id: String, updatedCoffeeList: CoffeeList): Result<Unit> {
        return try {
            update(id, updatedCoffeeList.toDTO(), parentDocId = auth.currentUser?.uid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteList(id: String): Result<Unit> {
        return try {
            delete(id, parentDocId = auth.currentUser?.uid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getListById(id: String): Result<CoffeeList?> {
        val dtoResult = getById(id, parentDocId = auth.currentUser?.uid)
        return if (dtoResult.isSuccess) {
            Result.success(dtoResult.getOrNull()?.toDomain())
        } else {
            Result.failure(dtoResult.exceptionOrNull() ?: Exception("Error fetching coffee list by id"))
        }
    }

    // ----------------------------
    // Pagination
    // ----------------------------
    override suspend fun getListsPaginated(
        pageSize: Int,
        reset: Boolean,
        orderBy: Pair<String, Query.Direction>?
    ): PaginatedResult<CoffeeList> {
        val dtoResult = fetchPage(
            pageSize = pageSize,
            parentDocId = auth.currentUser?.uid,
            reset = reset,
            orderBy = orderBy
        )
        return PaginatedResult(
            data = dtoResult.data.map { it.toDomain() },
            hasMore = dtoResult.hasMore
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
}
