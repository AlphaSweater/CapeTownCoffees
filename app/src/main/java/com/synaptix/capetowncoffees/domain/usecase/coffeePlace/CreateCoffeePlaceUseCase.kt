package com.synaptix.capetowncoffees.domain.usecase.coffeePlace

import com.synaptix.capetowncoffees.data.model.CoffeePlaceDTO
import com.synaptix.capetowncoffees.domain.repository.ICoffeePlaceRepository
import javax.inject.Inject

class CreateCoffeePlaceUseCase @Inject constructor(
    private val coffeePlaceRepository: ICoffeePlaceRepository
) {
    suspend operator fun invoke(coffeePlace: CoffeePlaceDTO, placeId: String? = null): Result<String> {
        return coffeePlaceRepository.addCoffeePlace(coffeePlace, placeId)
    }
}