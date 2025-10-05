package com.synaptix.capetowncoffees.domain.usecase.coffeePlace

import com.google.android.gms.maps.model.LatLng
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite
import com.synaptix.capetowncoffees.domain.model.CoffeeSearchParameters
import com.synaptix.capetowncoffees.domain.repository.ICoffeePlaceRepository
import javax.inject.Inject

class SearchNearbyCoffeePlacesUseCase @Inject constructor(
    private val coffeePlaceRepository: ICoffeePlaceRepository
) {
    suspend operator fun invoke(params: CoffeeSearchParameters, userLatLng: LatLng): Result<List<CoffeePlaceLite>> {
        return coffeePlaceRepository.searchNearbyCoffeePlaces(params, userLatLng)
    }
}

