package com.synaptix.capetowncoffees.domain.repository

import com.google.android.gms.maps.model.LatLng
import com.synaptix.capetowncoffees.data.model.CoffeePlaceDTO
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceFull
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceSuggestion
import com.synaptix.capetowncoffees.domain.model.CoffeeSearchParameters

interface ICoffeePlaceRepository {
    suspend fun checkCoffeePlaceExists(id: String): Result<Boolean>
    suspend fun addCoffeePlace(coffeePlace: CoffeePlaceDTO, placeId: String?): Result<String>
    suspend fun deleteCoffeePlace(id: String): Result<Unit>
    suspend fun getCoffeePlaceDetails(placeId: String): Result<CoffeePlaceFull>
    suspend fun searchNearbyCoffeePlaces(
        params: CoffeeSearchParameters,
        userLatLng: LatLng
    ): Result<List<CoffeePlaceLite>>
    suspend fun getSuggestions(
        params: CoffeeSearchParameters,
        userLatLng: LatLng
    ): Result<List<CoffeePlaceSuggestion>>
}