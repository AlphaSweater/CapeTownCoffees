package com.synaptix.capetowncoffees.domain.repository

import com.synaptix.capetowncoffees.domain.model.UserList as DomainList
import kotlinx.coroutines.flow.Flow

interface IUserListRepository {
    suspend fun createList(name: String, description: String?, isPublic: Boolean): String

    // Observe all saved lists for the current user in real-time
    fun observeLists(): Flow<kotlin.collections.List<DomainList>>

    // Observe a single list by ID
    fun observeList(id: String): Flow<DomainList?>

    // Delete a list by ID
    suspend fun deleteList(id: String)

    // Fetch all saved lists once
    suspend fun getLists(): kotlin.collections.List<DomainList>
}