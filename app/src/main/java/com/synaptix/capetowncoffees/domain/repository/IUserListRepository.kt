package com.synaptix.capetowncoffees.domain.repository

import com.synaptix.capetowncoffees.domain.model.UserList
import kotlinx.coroutines.flow.Flow

interface IUserListRepository {
    suspend fun createList(newUserList: UserList): String

    // Observe all saved lists for the current user in real-time
    fun observeLists(): Flow<List<UserList>>

    // Observe a single list by ID
    fun observeList(id: String): Flow<UserList?>

    // Delete a list by ID
    suspend fun deleteList(id: String)

    // Fetch all saved lists once
    suspend fun getLists(): List<UserList>
}