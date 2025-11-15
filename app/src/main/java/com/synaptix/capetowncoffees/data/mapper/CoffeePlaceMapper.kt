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
import com.synaptix.capetowncoffees.data.model.TagDTO
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceFull
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceSuggestion
import com.synaptix.capetowncoffees.domain.model.Tag
import com.synaptix.capetowncoffees.domain.model.TagCategory
import com.synaptix.capetowncoffees.domain.model.TagExtractor
import com.synaptix.capetowncoffees.util.CoffeeTimeUtils

// ──────────────────────────────────────────────────────────────────────────────
// Mapper — CoffeePlace ⇄ DTO ⇄ Domain
// ──────────────────────────────────────────────────────────────────────────────
// - Maps raw Google Places objects into domain models.
// - Maps Places into Firestore DTOs for caching.
// - Maps cached DTOs back into domain models.
// Keep this layer dumb: only field selection/formatting, no I/O or lookups.
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
        rating = place.rating,
        ratingCount = place.userRatingCount,
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
        full: CoffeePlaceFull,
        addedAt: Long? = null,
        nowSeconds: Long = CoffeeTimeUtils.nowSeconds()
    ): CoffeePlaceDTO = CoffeePlaceDTO(
        id = full.id,
        name = full.name,
        address = full.address,
        locationLat = full.location?.latitude,
        locationLng = full.location?.longitude,
        googleMapsUrl = full.googleMapsUrl,
        websiteUrl = full.websiteUrl,
        rating = full.rating,
        ratingCount = full.ratingCount,
        primaryType = full.primaryType,
        types = full.types ?: emptyList(),
        businessStatus = full.businessStatus,
        currentOpeningHours = full.currentOpeningHours ?: emptyList(),
        nationalPhoneNumber = full.nationalPhoneNumber,
        internationalPhoneNumber = full.internationalPhoneNumber,
        priceLevel = full.priceLevel,
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
        rating = place.rating,                            // average rating 0..5
        ratingCount = place.userRatingCount,              // number of user ratings
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
            rating = dto.rating,
            ratingCount = dto.ratingCount,
            images = null,
            primaryType = dto.primaryType,
            types = dto.types.ifEmpty { null },
            businessStatus = dto.businessStatus,
            currentOpeningHours = dto.currentOpeningHours.ifEmpty { null },
            nationalPhoneNumber = dto.nationalPhoneNumber,
            internationalPhoneNumber = dto.internationalPhoneNumber,
            websiteUrl = dto.websiteUrl,
            tags = tags,
            priceLevel = dto.priceLevel
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
        rating = place.rating,
        ratingCount = place.userRatingCount,
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
            rating = dto.rating,
            ratingCount = dto.ratingCount,
            images = null, // images handled via separate source for cached places
            currentOpeningHours = dto.currentOpeningHours.ifEmpty { null },
            businessStatus = dto.businessStatus,
            tags = tags
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

// ──────────────────────────────────────────────────────────────────────────────
// TagMapper — TagDTO ⇄ Tag (Domain)
// ──────────────────────────────────────────────────────────────────────────────
// Dedicated mapper for tag data. Tags are stored under:
//   /coffeePlaces/{placeId}/tags/{tagId}
// and mapped to the domain Tag model used by the UI and business logic.
// ──────────────────────────────────────────────────────────────────────────────
object TagMapper {

    // DTO → Domain
    fun toDomain(dto: TagDTO): Tag {
        val category = runCatching { TagCategory.valueOf(dto.category) }
            .getOrDefault(TagCategory.EXTRA)

        return Tag(
            name = dto.name,
            category = category
        )
    }

    // Domain → DTO
    fun toDto(
        tag: Tag,
        nowSeconds: Long = CoffeeTimeUtils.nowSeconds()
    ): TagDTO = TagDTO(
        id = tag.name,                   // use the tag name as the document ID
        name = tag.name,
        category = tag.category.name,
        createdAt = nowSeconds
    )
}
