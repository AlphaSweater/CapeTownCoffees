package com.synaptix.capetowncoffees.domain.repository

import com.synaptix.capetowncoffees.domain.model.CoffeeList
import kotlinx.coroutines.flow.Flow

interface ICoffeeListRepository {
    suspend fun createList(newCoffeeList: CoffeeList): String

    // Observe all saved lists for the current user in real-time
    fun observeLists(): Flow<List<CoffeeList>>

    // Observe a single list by ID
    fun observeList(id: String): Flow<CoffeeList?>

    // Delete a list by ID
    suspend fun deleteList(id: String)

    // Fetch all saved lists once
    suspend fun getLists(): List<CoffeeList>
}