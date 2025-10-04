package com.synaptix.capetowncoffees.domain.usecase.coffeeList

import com.synaptix.capetowncoffees.domain.repository.ICoffeeListRepository
import javax.inject.Inject

sealed class AddToListsResult {
    object Success : AddToListsResult()
    data class Error(val message: String) : AddToListsResult()
}
class AddPlacesToListUseCase @Inject constructor(
    private val repo: ICoffeeListRepository
) {
    suspend operator fun invoke(placeId: String, listIds: List<String>): AddToListsResult = try {
        repo.addPlaceToLists(listIds, placeId).getOrThrow()
        AddToListsResult.Success
    } catch (e: Exception) {
        AddToListsResult.Error(e.localizedMessage ?: "Failed to add to lists")
    }
}