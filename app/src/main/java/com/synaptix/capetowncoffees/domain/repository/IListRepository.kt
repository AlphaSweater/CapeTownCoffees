package com.synaptix.capetowncoffees.domain.repository

import com.synaptix.capetowncoffees.domain.model.List
import kotlinx.coroutines.flow.Flow

interface IListRepository {
    suspend fun createList(name: String, description: String?, isPublic: Boolean): String

    // Observe all saved lists for the current user in real-time
    fun observeLists(): Flow<kotlin.collections.List<List>>

    // Observe a single list by ID
    fun observeList(id: String): Flow<List?>

    // Delete a list by ID
    suspend fun deleteList(id: String)

    // Fetch all saved lists once
    suspend fun getLists(): kotlin.collections.List<List>
}