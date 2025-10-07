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

package com.synaptix.capetowncoffees.domain.repository

import com.google.android.gms.maps.model.LatLng
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceFull
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceSuggestion
import com.synaptix.capetowncoffees.domain.model.CoffeeSearchParameters
import com.synaptix.capetowncoffees.domain.model.GooglePlaceReview

interface IPlacesApiRepository {

    // -----------------------------
    // Core Operations
    // -----------------------------

    /**
     * Nearby search for coffee places.
     * Uses new Places SDK FindNearbyPlaces API.
     */
    suspend fun searchNearbyCoffeePlaces(
        params: CoffeeSearchParameters,
        userLatLng: LatLng
    ): Result<List<CoffeePlaceLite>>

    /**
     * Fetch full details for a place.
     */
    suspend fun getCoffeePlaceDetails(
        placeId: String
    ): Result<CoffeePlaceFull>

    /**
     * Get reviews for a coffee place.
     */
    suspend fun getCoffeePlaceReviews(placeId: String): Result<List<GooglePlaceReview>>

    /**
     * Get autocomplete suggestions (for search box).
     */
    suspend fun getSuggestions(
        params: CoffeeSearchParameters,
        userLatLng: LatLng
    ): Result<List<CoffeePlaceSuggestion>>


    // -----------------------------
    // Base Search Parameters
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
            "tea_house"
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
    }
}