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