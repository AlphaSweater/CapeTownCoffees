package com.synaptix.capetowncoffees.domain.repository

import com.google.android.libraries.places.api.model.LocationBias
import com.google.android.libraries.places.api.model.Place
import com.synaptix.capetowncoffees.domain.model.CoffeePlace
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceSuggestion

interface ICoffeePlacesRepository {
    /**
     * Search for coffee places near 'lat,lng' within 'radiusMeters'.
     * Uses base coffee place filters (e.g., "cafe", "coffee_shop").
     */
    suspend fun searchNearbyCoffeePlaces(
        lat: Double,
        lng: Double,
        radiusMeters: Int = 1000,
        includedTypes: List<String> = BASE_COFFEE_TYPES,
        maxResults: Int = 20
    ): Result<List<CoffeePlace>>

    /**
     * Get autocomplete suggestions for coffee places.
     */
    suspend fun autocompleteCoffeePlace(
        query: String,
        locationBias: LocationBias? = null,
        countries: List<String> = emptyList(),
        includedTypes: List<String> = BASE_COFFEE_TYPES
    ): Result<List<CoffeePlaceSuggestion>>

    /**
     * Fetch detailed info for a coffee place by placeId.
     */
    suspend fun fetchCoffeePlaceDetails(
        placeId: String,
        fields: List<Place.Field> = BASE_COFFEE_FIELDS
    ): Result<CoffeePlace>

    companion object {
        val BASE_COFFEE_TYPES = listOf("cafe", "coffee_shop", "coffeehouse")
        val BASE_COFFEE_FIELDS = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.LAT_LNG,
            Place.Field.ADDRESS,
            Place.Field.TYPES,
            Place.Field.RATING,
            Place.Field.PHOTO_METADATAS,
            Place.Field.OPENING_HOURS,
            Place.Field.WEBSITE_URI,
            Place.Field.PHONE_NUMBER,
            Place.Field.PRICE_LEVEL,
            Place.Field.USER_RATINGS_TOTAL,
            Place.Field.BUSINESS_STATUS
        )
    }
}
