package com.synaptix.capetowncoffees.domain.usecase.savedLists

import com.synaptix.capetowncoffees.domain.repository.ISavedListRepository
import javax.inject.Inject

sealed class CreateListResult {
    data class Success(val id: String) : CreateListResult()
    data class Error(val message: String) : CreateListResult()
}

class CreateListUseCase @Inject constructor(
    private val ISavedListRepository: ISavedListRepository
    ){
    suspend operator fun invoke(
        name: String,
        description: String?,
        isPublic: Boolean,
        placeId: String
    ): CreateListResult = try {
        val id = ISavedListRepository.createList(name, description, isPublic, placeId)
        CreateListResult.Success(id)
    } catch (e: Exception) {
        CreateListResult.Error(e.localizedMessage ?: "Failed to create list")
    }
}