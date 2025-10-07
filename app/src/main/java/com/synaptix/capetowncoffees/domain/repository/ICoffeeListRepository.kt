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
//* ChatGPT was used to assist with the development, design, and debugging of this file.
//* AI support was used for learning purposes, improving clarity and resolving issues.
//* It also helped generate useful comments
//======================================================================================

package com.synaptix.capetowncoffees.domain.repository

import com.synaptix.capetowncoffees.domain.model.CoffeeList
import com.synaptix.capetowncoffees.data.common.PaginatedResult
import kotlinx.coroutines.flow.Flow
import com.google.firebase.firestore.Query

interface ICoffeeListRepository {
    // ----------------------------
    // CRUD
    // ----------------------------

    /**
     * Creates a new coffee list for the current user.
     * @param newCoffeeList The CoffeeList to create.
     * @return Result containing the new list's ID on success, or an error.
     */
    suspend fun createList(newCoffeeList: CoffeeList): Result<String>

    /**
     * Deletes a coffee list by its ID for the current user.
     * @param id The ID of the list to delete.
     * @return Result indicating success or failure.
     */
    suspend fun deleteList(id: String): Result<Unit>

    /**
     * Updates a coffee list by its ID for the current user.
     * @param id The ID of the list to update.
     * @param updatedCoffeeList The updated CoffeeList data.
     * @return Result indicating success or failure.
     */
    suspend fun updateList(id: String, updatedCoffeeList: CoffeeList): Result<Unit>

    /**
     * Fetches all saved coffee lists for the current user.
     * @return Result containing a list of CoffeeList objects, or an error.
     */
    suspend fun getLists(): Result<List<CoffeeList>>

    /**
     * Fetches a single coffee list by its ID for the current user.
     * @param id The ID of the list to fetch.
     * @return Result containing the CoffeeList if found, or null if not.
     */
    suspend fun getListById(id: String): Result<CoffeeList?>

    // ----------------------------
    // Pagination
    // ----------------------------

    /**
     * Fetches paginated coffee lists for the current user.
     * @param pageSize Number of items per page.
     * @param reset Whether to reset pagination.
     * @param orderBy Optional field and direction to order by.
     * @return PaginatedResult containing CoffeeList data and hasMore flag.
     */
    suspend fun getListsPaginated(
        pageSize: Int,
        reset: Boolean = false,
        orderBy: Pair<String, Query.Direction>? = null
    ): PaginatedResult<CoffeeList>

    // ----------------------------
    // Observables
    // ----------------------------

    /**
     * Observes all saved coffee lists for a given user in real-time.
     * @param parentDocId The parent document ID (user ID).
     * @return Flow emitting the list of CoffeeList objects.
     */
    fun observeLists(parentDocId: String): Flow<List<CoffeeList>>

    /**
     * Observes a single coffee list by its ID for a given user in real-time.
     * @param id The ID of the list to observe.
     * @param parentDocId The parent document ID (user ID).
     * @return Flow emitting the CoffeeList if found, or null if not.
     */
    fun observeList(id: String, parentDocId: String): Flow<CoffeeList?>

    suspend fun addPlaceToLists(listIds: List<String>, placeId: String): Result<Unit>
}