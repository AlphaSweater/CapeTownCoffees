package com.synaptix.capetowncoffees.domain.repository

import com.google.android.gms.maps.model.LatLng
import com.synaptix.capetowncoffees.domain.model.*

interface IPlacesApiRepository {

    // -----------------------------
    // Core Operations
    // -----------------------------

    /**
     * Nearby search for coffee places.
     * Uses new Places SDK FindNearbyPlaces API.
     */
    suspend fun searchNearbyCoffeePlaces(
        params: CoffeeSearchParams
    ): Result<List<CoffeePlaceLite>>

    /**
     * Fetch full details for a place.
     */
    suspend fun getCoffeePlaceDetails(
        placeId: String
    ): Result<CoffeePlaceFull>

    /**
     * Get autocomplete suggestions (for search box).
     */
    suspend fun getSuggestions(
        query: String,
        location: LatLng? = null
    ): Result<List<CoffeePlaceSuggestion>>


    // -----------------------------
    // Search Parameters
    // -----------------------------

    /**
     * Immutable base search parameters and type filters for coffee searches.
     * Includes strict vs relaxed modes and a primary type blacklist.
     */
    object BaseSearchParams {
        // Strict: only primary types that are dedicated coffee places
        val strictPrimaryAllowed: Set<String> = setOf(
            "coffee_shop",
            "cafe"
        )

        // Relaxed: broader primary types allowed, but require a coffee subtype if too broad
        val relaxedPrimaryAllowed: Set<String> = setOf(
            "coffee_shop",
            "cafe",
            "bakery",
            "restaurant",
            "breakfast_restaurant",
            "brunch_restaurant",
            "tea_house",
            "dessert_shop",
            "dessert_restaurant"
        )

        // Blacklist: primary types that should never be included
        val primaryBlacklist: Set<String> = setOf(
            "fast_food_restaurant",
            "bar",
            "pub",
            "wine_bar"
        )

        // Coffee-relevant subtype tags that can appear in the types array
        val coffeeSubTypes: Set<String> = setOf(
            "coffee_shop",
            "cafe"
        )

        val excludedSubtypes: Set<String> = setOf(
            "bar",
            "night_club",
            "pub",
            "liquor_store",
            "casino"
        )

        // Default search configuration
        const val requireOperational: Boolean = true
        const val defaultRadiusMeters: Int = 2000
        const val maxResults: Int = 20
    }

    /**
     * Caller-configurable search params.
     * These are merged with [BaseSearchParams] inside implementation.
     */
    data class CoffeeSearchParams(
        val location: LatLng,
        val radiusMeters: Int = BaseSearchParams.defaultRadiusMeters,
        val query: String? = "coffee",
        val onlyOpenNow: Boolean = false,
        val maxResults: Int = BaseSearchParams.maxResults,
        val sortByDistance: Boolean = true,

        // Strictness: if true → only strict coffee places; else relaxed mode
        val strictCoffeeOnly: Boolean = true
    )
}