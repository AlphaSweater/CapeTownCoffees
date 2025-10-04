package com.synaptix.capetowncoffees.domain.usecase.coffeePlace

import com.google.android.gms.maps.model.LatLng
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceSuggestion
import com.synaptix.capetowncoffees.domain.repository.ICoffeePlaceRepository
import javax.inject.Inject

class GetCoffeePlaceSuggestionsUseCase @Inject constructor(
    private val coffeePlaceRepository: ICoffeePlaceRepository
) {
    suspend operator fun invoke(query: String, userLatLng: LatLng): Result<List<CoffeePlaceSuggestion>> {
        return coffeePlaceRepository.getSuggestions(query, userLatLng)
    }
}

