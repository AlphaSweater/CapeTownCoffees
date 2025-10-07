// =============================
// CoffeePlace Domain Models
// =============================
package com.synaptix.capetowncoffees.domain.model

import androidx.compose.ui.graphics.vector.Path
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.google.android.libraries.places.api.model.Place
import com.synaptix.capetowncoffees.data.mapper.CoffeePlaceMapper

// =============================
// Interfaces
// =============================

// Base interface for all coffee place models.
interface CoffeePlaceBase

// Interface for dynamic field mapping from Google Place API.
interface CoffeePlaceCompanion<T : CoffeePlaceBase> {
    val fields: List<Place.Field>
    fun fromPlace(place: Place): T
}

// =============================
// Data Models
// =============================

// Full details model for a coffee place.
data class CoffeePlaceFull(
    val id: String,
    val name: String?,
    val address: String?,
    val location: LatLng?,
    val googleMapsUrl: String?,
    val rating: Double?,
    val ratingCount: Int?,
    val images: List<PhotoMetadata>?, // Handles Google images

    // 🏪 Types
    val primaryType: String?,          // new SDK: exactly one primary type
    val types: List<String>?,          // extra types list

    // ⏰ Hours
    val businessStatus: String?,
    val currentOpeningHours: List<String>?,

    // 📞 Contact Info
    val nationalPhoneNumber: String?,
    val internationalPhoneNumber: String? = null,
    val websiteUrl: String?,

    // 🍽️ Grouped Attributes
    val tags: List<Tag>, // Unified tags for UI

    // ⭐ Reviews
    val coffeeReviews: List<CoffeeReview> = emptyList(), // In-app and Google reviews combined

    // 💰 Other attributes
    val priceLevel: Int? = null // Google price level (0-4)

) : CoffeePlaceBase {
    // ----------- Companion for mapping from Place -----------
    companion object : CoffeePlaceCompanion<CoffeePlaceFull> {
        override val fields = listOf(
            Place.Field.ID,
            Place.Field.DISPLAY_NAME,
            Place.Field.FORMATTED_ADDRESS,
            Place.Field.LOCATION,
            Place.Field.PRIMARY_TYPE,
            Place.Field.TYPES,
            Place.Field.RATING,
            Place.Field.USER_RATING_COUNT,
            Place.Field.PHOTO_METADATAS,
            Place.Field.CURRENT_OPENING_HOURS,
            Place.Field.BUSINESS_STATUS,
            Place.Field.NATIONAL_PHONE_NUMBER,
            Place.Field.INTERNATIONAL_PHONE_NUMBER,
            Place.Field.WEBSITE_URI,
            Place.Field.PRICE_LEVEL,
            Place.Field.GOOGLE_MAPS_URI
        )
        override fun fromPlace(place: Place) = CoffeePlaceMapper.toFull(place)
    }
}

// Lightweight model for feed/search results.
data class CoffeePlaceLite(
    val id: String,
    val name: String?,
    val address: String?,
    val location: LatLng?,
    val primaryType: String?,
    val types: List<String>?,
    val rating: Double?,
    val ratingCount: Int?,
    val images: List<PhotoMetadata>?,
    val currentOpeningHours: List<String>?,
    val businessStatus: String?,
    val priceLevel: Int? = null,
    val tags: List<Tag>, // Unified tags for UI
    var isFavorite: Boolean = false
) : CoffeePlaceBase {
    // ----------- Companion for mapping from Place -----------
    companion object : CoffeePlaceCompanion<CoffeePlaceLite> {
        override val fields = listOf(
            Place.Field.ID,
            Place.Field.DISPLAY_NAME,
            Place.Field.FORMATTED_ADDRESS,
            Place.Field.LOCATION,
            Place.Field.PRIMARY_TYPE,
            Place.Field.TYPES,
            Place.Field.RATING,
            Place.Field.USER_RATING_COUNT,
            Place.Field.PHOTO_METADATAS,
            Place.Field.CURRENT_OPENING_HOURS,
            Place.Field.BUSINESS_STATUS,
            Place.Field.PRICE_LEVEL
        )
        override fun fromPlace(place: Place) = CoffeePlaceMapper.toLite(place)
    }
}

// Model for search/autocomplete suggestions.
data class CoffeePlaceSuggestion(
    val id: String,
    val name: String?,
    val address: String? = null,
    val distance: Int? = null
) : CoffeePlaceBase {
    // ----------- Companion for mapping from Place -----------
    companion object : CoffeePlaceCompanion<CoffeePlaceSuggestion> {
        override val fields = listOf(
            Place.Field.ID,
            Place.Field.DISPLAY_NAME,
            Place.Field.FORMATTED_ADDRESS
        )
        override fun fromPlace(place: Place) = CoffeePlaceMapper.toSuggestion(place)
    }
}

// =============================================================================================
// Tag Model & Extractor
// =============================================================================================

/**
 * Unified tags used for displaying "chips" in UI.
 */
data class Tag(
    val name: String,
    val category: TagCategory
)

enum class TagCategory {
    FOOD, SERVICE, ATMOSPHERE, EXTRA
}

/**
 * Extracts unified Tags from a Google Place.
 * Uses new Places SDK fields: primaryType, types, and BooleanPlaceAttributeValue.
 */
object TagExtractor {
    fun extract(place: Place): List<Tag> {
        val tags = mutableListOf<Tag>()

        // 1. Primary Type → core identity tag
        place.primaryType?.let { type ->
            tags.add(Tag(type.lowercase().replace("_", " ").capitalizeWords(), TagCategory.FOOD))
        }

        // 2. Secondary Types → more identity/context
        place.placeTypes?.forEach { type ->
            val clean = type.toString().lowercase().replace("_", " ").capitalizeWords()
            when (type.toString().lowercase()) {
                "bakery", "restaurant", "meal_takeaway" ->
                    tags.add(Tag(clean, TagCategory.FOOD))
                "bar", "night_club" ->
                    tags.add(Tag("Nightlife", TagCategory.ATMOSPHERE))
                "cafe" ->
                    tags.add(Tag("Cafe", TagCategory.FOOD))
            }
        }

        // 3. Boolean attributes (new SDK style)
        addIfTrue(tags, place, { it.servesCoffee }, "Coffee", TagCategory.FOOD)
        addIfTrue(tags, place, { it.servesBreakfast }, "Breakfast", TagCategory.FOOD)
        addIfTrue(tags, place, { it.servesBrunch }, "Brunch", TagCategory.FOOD)
        addIfTrue(tags, place, { it.servesLunch }, "Lunch", TagCategory.FOOD)
        addIfTrue(tags, place, { it.servesDinner }, "Dinner", TagCategory.FOOD)
        addIfTrue(tags, place, { it.servesBeer }, "Beer", TagCategory.EXTRA)
        addIfTrue(tags, place, { it.servesWine }, "Wine", TagCategory.EXTRA)
        addIfTrue(tags, place, { it.servesCocktails }, "Cocktails", TagCategory.EXTRA)
        addIfTrue(tags, place, { it.servesVegetarianFood }, "Vegetarian Options", TagCategory.FOOD)

        addIfTrue(tags, place, { it.dineIn }, "Dine In", TagCategory.SERVICE)
        addIfTrue(tags, place, { it.takeout }, "Takeout", TagCategory.SERVICE)
        addIfTrue(tags, place, { it.delivery }, "Delivery", TagCategory.SERVICE)

        addIfTrue(tags, place, { it.outdoorSeating }, "Outdoor Seating", TagCategory.EXTRA)
        addIfTrue(tags, place, { it.reservable }, "Reservable", TagCategory.SERVICE)
        addIfTrue(tags, place, { it.goodForChildren }, "Kid Friendly", TagCategory.EXTRA)
        addIfTrue(tags, place, { it.liveMusic }, "Live Music", TagCategory.ATMOSPHERE)

        // Deduplicate & return
        return tags.distinctBy { it.name }
    }

    // Helper for Place.BooleanPlaceAttributeValue
    private fun addIfTrue(
        tags: MutableList<Tag>,
        place: Place,
        getter: (Place) -> Place.BooleanPlaceAttributeValue?,
        name: String,
        category: TagCategory
    ) {
        val value = getter(place)
        if (value == Place.BooleanPlaceAttributeValue.TRUE) {
            tags.add(Tag(name, category))
        }
    }

    // String formatting helper
    private fun String.capitalizeWords(): String =
        split(" ").joinToString(" ") { word ->
            if (word.isNotEmpty()) word.replaceFirstChar { c -> c.uppercase() } else word
        }
}
