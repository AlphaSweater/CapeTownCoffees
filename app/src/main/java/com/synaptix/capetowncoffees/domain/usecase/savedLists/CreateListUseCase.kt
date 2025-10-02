package com.synaptix.capetowncoffees.domain.usecase.savedLists

import com.synaptix.capetowncoffees.domain.model.UserList
import com.synaptix.capetowncoffees.domain.repository.IUserListRepository
import javax.inject.Inject

sealed class CreateListResult {
    data class Success(val id: String) : CreateListResult()
    data class Error(val message: String) : CreateListResult()
}

class CreateListUseCase @Inject constructor(
    private val repo: IUserListRepository
) {
    suspend operator fun invoke(
        newUserList: UserList
    ): CreateListResult = try {
        val id = repo.createList(newUserList)
        CreateListResult.Success(id)
    } catch (e: Exception) {
        CreateListResult.Error(e.localizedMessage ?: "Failed to create list")
    }
}