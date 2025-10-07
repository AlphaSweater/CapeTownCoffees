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
//* ChatGPT was used to guide the creation of mapper classes responsible for converting
//between entities, DTOs, and domain models.
//* It also helped ensure consistent naming and mapping logic throughout the project.
//* It also helped generate useful comments
//======================================================================================

package com.synaptix.capetowncoffees.data.mapper

import com.google.android.libraries.places.api.model.Place
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceFull
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceSuggestion
import com.synaptix.capetowncoffees.domain.model.TagExtractor

// ─────────── Mapper — CoffeePlace ⇄ Domain ───────────
// We map raw Google Places objects into our domain models.
// Keep this layer dumb: only field selection/formatting, no I/O or lookups.
public object CoffeePlaceMapper {

    // ─────────── Constants ───────────
    // We prefer a named empty id so the fallback is visible and consistent.
    private const val EMPTY_ID: String = ""

    // ─────────── Public API ───────────

    // Full model for detail screens; we carry richer fields like phones, site, hours.
    public fun toFull(place: Place): CoffeePlaceFull = CoffeePlaceFull(
        id = place.id ?: EMPTY_ID,                        // ensure non-null id string
        name = place.displayName,                         // human name shown by Google
        address = place.formattedAddress,                 // postal-like formatted address
        location = place.location,                        // lat/lng; null-safe upstream
        googleMapsUrl = place.googleMapsUri?.toString(),  // deep-link to Maps when available
        rating = place.rating,                            // average rating 0..5
        ratingCount = place.userRatingCount,              // number of user ratings
        images = place.photoMetadatas,                    // metadata list for lazy photo fetch
        primaryType = place.primaryType,                  // main category (e.g., CAFE)
        types = place.placeTypes?.map { it.toString() },  // keep raw strings for filtering
        businessStatus = place.businessStatus?.name,      // e.g., OPERATIONAL
        currentOpeningHours = place.currentOpeningHours?.weekdayText, // readable hours
        nationalPhoneNumber = place.nationalPhoneNumber,
        internationalPhoneNumber = place.internationalPhoneNumber,
        websiteUrl = place.websiteUri?.toString(),
        tags = TagExtractor.extract(place)                // lightweight tag hints from fields
    )

    // Lite model for lists and suggestions; only essentials for quick rendering.
    public fun toLite(place: Place): CoffeePlaceLite = CoffeePlaceLite(
        id = place.id ?: EMPTY_ID,
        name = place.displayName,
        address = place.formattedAddress,
        location = place.location,
        primaryType = place.primaryType,
        types = place.placeTypes?.map { it.toString() },
        rating = place.rating,
        ratingCount = place.userRatingCount,
        images = place.photoMetadatas,
        currentOpeningHours = place.currentOpeningHours?.weekdayText,
        businessStatus = place.businessStatus?.name,
        tags = TagExtractor.extract(place)
    )

    // Minimal model for typeahead UX; we keep only id + display name.
    public fun toSuggestion(place: Place): CoffeePlaceSuggestion = CoffeePlaceSuggestion(
        id = place.id ?: EMPTY_ID,
        name = place.displayName
    )
}