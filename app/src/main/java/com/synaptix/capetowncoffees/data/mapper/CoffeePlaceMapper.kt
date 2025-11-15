//======================================================================================
// Group 2 - Group Members:
//======================================================================================
// * Chad Fairlie ST10269509
// * Dhiren Ruthenavelu ST10256859
// * Kayla Ferreira ST10259527
// * Nathan Teixeira ST10249266
//======================================================================================
// References:
//======================================================================================
// * ChatGPT was used to guide the creation of mapper classes responsible for converting
//   between entities, DTOs, and domain models.
// * It also helped ensure consistent naming and mapping logic throughout the project.
// * It also helped generate useful comments.
//======================================================================================

package com.synaptix.capetowncoffees.data.mapper

import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.Place
import com.synaptix.capetowncoffees.data.model.CoffeePlaceDTO
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceFull
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceSuggestion
import com.synaptix.capetowncoffees.domain.model.Tag
import com.synaptix.capetowncoffees.domain.model.TagExtractor
import com.synaptix.capetowncoffees.util.CoffeeTimeUtils

// ──────────────────────────────────────────────────────────────────────────────
// Mapper — CoffeePlace ⇄ DTO ⇄ Domain
// ──────────────────────────────────────────────────────────────────────────────
// - Maps raw Google Places objects into domain models.
// - Maps Places into Firestore DTOs for caching.
// - Maps cached DTOs back into domain models.
// ──────────────────────────────────────────────────────────────────────────────
object CoffeePlaceMapper {

    // We prefer a named empty id so the fallback is visible and consistent.
    private const val EMPTY_ID: String = ""

    // =====================================================================
    // Place → DTO
    // =====================================================================

    // Converts a live Google Places `Place` into a Firestore DTO for caching.
    fun toDto(
        place: Place,
        nowSeconds: Long = CoffeeTimeUtils.nowSeconds()
    ): CoffeePlaceDTO = CoffeePlaceDTO(
        id = place.id ?: EMPTY_ID,
        name = place.displayName,
        address = place.formattedAddress,
        locationLat = place.location?.latitude,
        locationLng = place.location?.longitude,
        googleMapsUrl = place.googleMapsUri?.toString(),
        websiteUrl = place.websiteUri?.toString(),
        googleRating = place.rating,
        googleRatingCount = place.userRatingCount,
        primaryType = place.primaryType,
        types = place.placeTypes?.map { it.toString() } ?: emptyList(),
        businessStatus = place.businessStatus?.name,
        currentOpeningHours = place.currentOpeningHours?.weekdayText ?: emptyList(),
        nationalPhoneNumber = place.nationalPhoneNumber,
        internationalPhoneNumber = place.internationalPhoneNumber,
        priceLevel = place.priceLevel,
        addedAt = nowSeconds,
        updatedAt = nowSeconds
    )

    // Converts a domain `CoffeePlaceFull` into a Firestore DTO for caching.
    fun toDto(
        coffeePlaceFull: CoffeePlaceFull,
        addedAt: Long? = null,
        nowSeconds: Long = CoffeeTimeUtils.nowSeconds()
    ): CoffeePlaceDTO = CoffeePlaceDTO(
        id = coffeePlaceFull.id,
        name = coffeePlaceFull.name,
        address = coffeePlaceFull.address,
        locationLat = coffeePlaceFull.location?.latitude,
        locationLng = coffeePlaceFull.location?.longitude,
        googleMapsUrl = coffeePlaceFull.googleMapsUrl,
        websiteUrl = coffeePlaceFull.websiteUrl,
        imageUrl = coffeePlaceFull.cachedImageUrl,
        googleRating = coffeePlaceFull.googleRating,
        googleRatingCount = coffeePlaceFull.googleRatingCount,
        primaryType = coffeePlaceFull.primaryType,
        types = coffeePlaceFull.types ?: emptyList(),
        businessStatus = coffeePlaceFull.businessStatus,
        currentOpeningHours = coffeePlaceFull.currentOpeningHours ?: emptyList(),
        nationalPhoneNumber = coffeePlaceFull.nationalPhoneNumber,
        internationalPhoneNumber = coffeePlaceFull.internationalPhoneNumber,
        addedAt = addedAt ?: nowSeconds,
        updatedAt = nowSeconds
    )

    // =====================================================================
    // Place/DTO → CoffeePlaceFull (domain)
    // =====================================================================

    // Full model for detail screens from a live Google Places object.
    // We carry richer fields like phones, website, hours, and images.
    fun toFull(place: Place): CoffeePlaceFull = CoffeePlaceFull(
        id = place.id ?: EMPTY_ID,                        // ensure non-null id string
        name = place.displayName,                         // human name shown by Google
        address = place.formattedAddress,                 // postal-like formatted address
        location = place.location,                        // lat/lng; null-safe upstream
        googleMapsUrl = place.googleMapsUri?.toString(),  // deep-link to Maps when available
        googleRating = place.rating,                      // average rating 0..5
        googleRatingCount = place.userRatingCount,        // number of user ratings
        images = place.photoMetadatas,                    // metadata for lazy photo fetch
        primaryType = place.primaryType,                  // main category (e.g., CAFE)
        types = place.placeTypes?.map { it.toString() },  // keep raw strings for filtering
        businessStatus = place.businessStatus?.name,      // e.g., OPERATIONAL
        currentOpeningHours = place.currentOpeningHours?.weekdayText, // readable hours
        nationalPhoneNumber = place.nationalPhoneNumber,
        internationalPhoneNumber = place.internationalPhoneNumber,
        websiteUrl = place.websiteUri?.toString(),
        tags = TagExtractor.extract(place)                // lightweight tag hints from fields
    )

    // Full model from a cached Firestore DTO (+ tags loaded from subcollection).
    fun toFull(
        dto: CoffeePlaceDTO,
        tags: List<Tag> = emptyList()
    ): CoffeePlaceFull {
        val location: LatLng? = dto.toLatLngOrNull()

        return CoffeePlaceFull(
            id = dto.id,
            name = dto.name,
            address = dto.address,
            location = location,
            googleMapsUrl = dto.googleMapsUrl,
            googleRating = dto.googleRating,
            googleRatingCount = dto.googleRatingCount,
            appRating = dto.appRating,
            appRatingCount = dto.appRatingCount,
            images = null,
            cachedImageUrl = dto.imageUrl,
            primaryType = dto.primaryType,
            types = dto.types.ifEmpty { null },
            businessStatus = dto.businessStatus,
            currentOpeningHours = dto.currentOpeningHours.ifEmpty { null },
            nationalPhoneNumber = dto.nationalPhoneNumber,
            internationalPhoneNumber = dto.internationalPhoneNumber,
            websiteUrl = dto.websiteUrl,
            tags = tags,
            isCached = true
        )
    }

    // =====================================================================
    // Place/DTO → CoffeePlaceLite (domain)
    // =====================================================================

    // Lite model for lists and suggestions from a live Place.
    // Only essentials for quick rendering.
    fun toLite(place: Place): CoffeePlaceLite = CoffeePlaceLite(
        id = place.id ?: EMPTY_ID,
        name = place.displayName,
        address = place.formattedAddress,
        location = place.location,
        primaryType = place.primaryType,
        types = place.placeTypes?.map { it.toString() },
        googleRating = place.rating,
        googleRatingCount = place.userRatingCount,
        images = place.photoMetadatas,
        currentOpeningHours = place.currentOpeningHours?.weekdayText,
        businessStatus = place.businessStatus?.name,
        tags = TagExtractor.extract(place)
    )

    // Lite model from a cached DTO (+ tags from subcollection).
    fun toLite(
        dto: CoffeePlaceDTO,
        tags: List<Tag> = emptyList()
    ): CoffeePlaceLite {
        val location: LatLng? = dto.toLatLngOrNull()

        return CoffeePlaceLite(
            id = dto.id,
            name = dto.name,
            address = dto.address,
            location = location,
            primaryType = dto.primaryType,
            types = dto.types.ifEmpty { null },
            googleRating = dto.googleRating,
            googleRatingCount = dto.googleRatingCount,
            appRating = dto.appRating,
            appRatingCount = dto.appRatingCount,
            images = null,
            cachedImageUrl = dto.imageUrl,
            currentOpeningHours = dto.currentOpeningHours.ifEmpty { null },
            businessStatus = dto.businessStatus,
            tags = tags,
            isCached = true
        )
    }

    // =====================================================================
    // Place/DTO → CoffeePlaceSuggestion (domain)
    // =====================================================================

    // Minimal model for typeahead UX from a live Place.
    fun toSuggestion(place: Place): CoffeePlaceSuggestion = CoffeePlaceSuggestion(
        id = place.id ?: EMPTY_ID,
        name = place.displayName
    )

    fun toSuggestion(dto: CoffeePlaceDTO): CoffeePlaceSuggestion =
        CoffeePlaceSuggestion(
            id = dto.id,
            name = dto.name
        )

    // =====================================================================
    // Private helpers
    // =====================================================================

    private fun CoffeePlaceDTO.toLatLngOrNull(): LatLng? =
        if (locationLat != null && locationLng != null) {
            LatLng(locationLat, locationLng)
        } else {
            null
        }
}
