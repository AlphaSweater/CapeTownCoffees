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

