package com.synaptix.capetowncoffees.domain.repository

import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.Place
import com.synaptix.capetowncoffees.domain.model.*

interface IPlacesApiRepository {

    suspend fun searchCoffeePlaces(
        params: CoffeeSearchParams
    ): Result<List<CoffeePlaceLite>>

    suspend fun getCoffeePlaceDetails(
        placeId: String,
        fields: List<Place.Field> = CoffeePlaceFull.fields
    ): Result<CoffeePlaceFull>

    suspend fun getSuggestions(
        query: String,
        location: LatLng? = null,
        maxResults: Int = 10
    ): Result<List<CoffeePlaceSuggestion>>

    // -----------------------------
    // Search parameter definitions
    // -----------------------------

    /**
     * Immutable base params applied to every search.
     * These cannot be overridden by caller.
     */
    object BaseSearchParams {
        val requiredTypes = listOf("cafe", "coffee_shop")
        val requireOperational = true
    }

    /**
     * Configurable search parameters provided by the caller.
     * These are merged with [BaseSearchParams] inside the implementation.
     */
    data class CoffeeSearchParams(
        val location: LatLng,
        val radiusMeters: Int = 2000,
        val query: String? = null,
        val openNow: Boolean = false,
        val maxResults: Int = 20,

        // Client-side tag filtering (post-fetch)
        val foodOptions: Set<FoodOption> = emptySet(),
        val atmosphere: Set<Atmosphere> = emptySet(),
        val serviceOptions: Set<ServiceOption> = emptySet(),
        val extras: Set<ExtraFeature> = emptySet(),

        // API fields to request
        val fields: List<Place.Field> = CoffeePlaceLite.fields
    )
}