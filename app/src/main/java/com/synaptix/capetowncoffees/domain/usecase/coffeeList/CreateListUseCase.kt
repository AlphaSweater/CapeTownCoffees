package com.synaptix.capetowncoffees.domain.usecase.coffeeList

import com.synaptix.capetowncoffees.domain.model.CoffeeList
import com.synaptix.capetowncoffees.domain.repository.ICoffeeListRepository
import javax.inject.Inject

sealed class CreateListResult {
    data class Success(val id: String) : CreateListResult()
    data class Error(val message: String) : CreateListResult()
}

class CreateListUseCase @Inject constructor(
    private val repo: ICoffeeListRepository
) {
    suspend operator fun invoke(
        newCoffeeList: CoffeeList
    ): CreateListResult = try {
        val result = repo.createList(newCoffeeList)
        val id = result.getOrThrow()
        CreateListResult.Success(id)
    } catch (e: Exception) {
        CreateListResult.Error(e.localizedMessage ?: "Failed to create list")
    }
}