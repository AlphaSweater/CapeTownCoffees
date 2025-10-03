package com.synaptix.capetowncoffees.domain.usecase.coffeePlace

import com.synaptix.capetowncoffees.domain.model.CoffeePlaceFull
import com.synaptix.capetowncoffees.domain.repository.ICoffeePlaceRepository
import javax.inject.Inject

class GetCoffeePlaceDetailsUseCase @Inject constructor(
    private val coffeePlaceRepository: ICoffeePlaceRepository
) {
    suspend operator fun invoke(placeId: String): Result<CoffeePlaceFull> {
        return coffeePlaceRepository.getCoffeePlaceDetails(placeId)
    }
}

