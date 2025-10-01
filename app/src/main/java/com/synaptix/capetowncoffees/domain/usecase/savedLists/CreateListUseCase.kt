package com.synaptix.capetowncoffees.domain.usecase.savedLists

import com.synaptix.capetowncoffees.domain.repository.ISavedListRepository
import javax.inject.Inject

sealed class CreateListResult {
    data class Success(val id: String) : CreateListResult()
    data class Error(val message: String) : CreateListResult()
}

class CreateListUseCase @Inject constructor(
    private val repo: ISavedListRepository
) {
    suspend operator fun invoke(
        name: String,
        description: String?,
        isPublic: Boolean
    ): CreateListResult = try {
        val id = repo.createList(name, description, isPublic)
        CreateListResult.Success(id)
    } catch (e: Exception) {
        CreateListResult.Error(e.localizedMessage ?: "Failed to create list")
    }
}