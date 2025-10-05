package com.synaptix.capetowncoffees.domain.usecase.coffeePlace

import com.synaptix.capetowncoffees.domain.repository.ICoffeePlaceRepository
import javax.inject.Inject

class DeleteCoffeePlaceUseCase @Inject constructor(
    private val coffeePlaceRepository: ICoffeePlaceRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> {
        return coffeePlaceRepository.deleteCoffeePlace(id)
    }
}

