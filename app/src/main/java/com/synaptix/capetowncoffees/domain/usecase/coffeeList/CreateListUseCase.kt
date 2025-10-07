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

import com.synaptix.capetowncoffees.domain.model.CoffeeList
import com.synaptix.capetowncoffees.domain.repository.ICoffeeListRepository
import javax.inject.Inject

sealed class CreateListResult {
    data class Success(val id: String) : CreateListResult()
    data class Error(val message: String) : CreateListResult()
}

class CreateListUseCase @Inject constructor(
    private val coffeeListRepository: ICoffeeListRepository
) {
    suspend operator fun invoke(
        newCoffeeList: CoffeeList
    ): CreateListResult = try {
        val result = coffeeListRepository.createList(newCoffeeList)
        val id = result.getOrThrow()
        CreateListResult.Success(id)
    } catch (e: Exception) {
        CreateListResult.Error(e.localizedMessage ?: "Failed to create list")
    }
}