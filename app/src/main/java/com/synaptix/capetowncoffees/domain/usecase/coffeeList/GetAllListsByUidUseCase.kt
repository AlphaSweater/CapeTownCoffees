package com.synaptix.capetowncoffees.domain.usecase.coffeeList

import com.synaptix.capetowncoffees.domain.model.CoffeeList
import com.synaptix.capetowncoffees.domain.repository.ICoffeeListRepository
import javax.inject.Inject

class GetAllListsByUidUseCase @Inject constructor(
    private val repo: ICoffeeListRepository
){
    suspend operator fun invoke(): Result<List<CoffeeList>> = repo.getLists()
}