// =============================
// CoffeePlace Domain Models
// =============================
package com.synaptix.capetowncoffees.domain.model

import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.google.android.libraries.places.api.model.Place

// =============================
// Interfaces
// =============================

// Base interface for all coffee place models.
interface CoffeePlaceBase {
    fun getId(): String?
    fun getName(): String?
    fun getLocation(): LatLng?
}

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
    val id: String?,
    val name: String?,
    val address: String?,
    val location: LatLng?,
    val googleMapsUrl: String?,
    val rating: Double?,
    val ratingCount: Int?,
    val images: List<PhotoMetadata>?, // Handles Google images

    // ⏰ Hours
    val businessStatus: String?,
    val currentOpeningHours: List<String>?,

    // 📞 Contact Info
    val nationalPhoneNumber: String?,
    val internationalPhoneNumber: String? = null,
    val websiteUrl: String?,

    // 🍽️ Grouped Attributes
    val foodOptions: List<FoodOption>,       // e.g. BREAKFAST, DESSERT
    val atmosphere: List<Atmosphere>,        // e.g. PET_FRIENDLY, OUTDOOR_SEATING
    val serviceOptions: List<ServiceOption>, // e.g. DINE_IN, TAKEOUT
    val extras: List<ExtraFeature>,          // e.g. PARKING, WHEELCHAIR_ACCESS

    // ⭐ Reviews
    val reviews: List<Review>?, // In-app and Google reviews combined

    // 💰 Other attributes
    val priceLevel: Int? = null // Google price level (0-4)

) : CoffeePlaceBase {
    // ----------- Companion for mapping from Place -----------
    companion object : CoffeePlaceCompanion<CoffeePlaceFull> {
        override val fields = listOf(
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
        override fun fromPlace(place: Place): CoffeePlaceFull {
            return CoffeePlaceFull(
                id = place.id,
                name = place.displayName,
                address = place.formattedAddress,
                location = place.location,
                googleMapsUrl = place.googleMapsUri?.toString(),
                rating = place.rating,
                ratingCount = place.userRatingCount,
                images = place.photoMetadatas,
                businessStatus = place.businessStatus?.name,
                currentOpeningHours = place.currentOpeningHours?.weekdayText,
                nationalPhoneNumber = place.nationalPhoneNumber,
                internationalPhoneNumber = place.internationalPhoneNumber,
                websiteUrl = place.websiteUri?.toString(),
                foodOptions = TagBuilder.buildFoodOptions(place),
                atmosphere = TagBuilder.buildAtmosphere(place),
                serviceOptions = TagBuilder.buildServiceOptions(place),
                extras = TagBuilder.buildExtras(place),
                reviews = null,
                priceLevel = place.priceLevel
            )
        }
    }
    override fun getId() = id
    override fun getName() = name
    override fun getLocation() = location
}

// Lightweight model for feed/search results.
data class CoffeePlaceLite(
    val id: String?,
    val name: String?,
    val address: String?,
    val location: LatLng?,
    val googleMapsUrl: String?,
    val rating: Double?,
    val ratingCount: Int?,
    val images: List<PhotoMetadata>?,
    val businessStatus: String?,
    val currentOpeningHours: List<String>?,
    val nationalPhoneNumber: String?,
    val internationalPhoneNumber: String? = null,
    val foodOptions: List<FoodOption>,
    val atmosphere: List<Atmosphere>,
    val serviceOptions: List<ServiceOption>,
    val extras: List<ExtraFeature>,
    val priceLevel: Int? = null
) : CoffeePlaceBase {
    // ----------- Companion for mapping from Place -----------
    companion object : CoffeePlaceCompanion<CoffeePlaceLite> {
        override val fields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.LAT_LNG,
            Place.Field.ADDRESS,
            Place.Field.RATING,
            Place.Field.PHOTO_METADATAS,
            Place.Field.OPENING_HOURS,
            Place.Field.PHONE_NUMBER,
            Place.Field.PRICE_LEVEL,
            Place.Field.USER_RATINGS_TOTAL,
            Place.Field.BUSINESS_STATUS
        )
        override fun fromPlace(place: Place): CoffeePlaceLite {
            return CoffeePlaceLite(
                id = place.id,
                name = place.displayName,
                address = place.formattedAddress,
                location = place.location,
                googleMapsUrl = place.googleMapsUri?.toString(),
                rating = place.rating,
                ratingCount = place.userRatingCount,
                images = place.photoMetadatas,
                businessStatus = place.businessStatus?.name,
                currentOpeningHours = place.currentOpeningHours?.weekdayText,
                nationalPhoneNumber = place.nationalPhoneNumber,
                internationalPhoneNumber = place.internationalPhoneNumber,
                foodOptions = TagBuilder.buildFoodOptions(place),
                atmosphere = TagBuilder.buildAtmosphere(place),
                serviceOptions = TagBuilder.buildServiceOptions(place),
                extras = TagBuilder.buildExtras(place),
                priceLevel = place.priceLevel
            )
        }
    }
    override fun getId() = id
    override fun getName() = name
    override fun getLocation() = location
}

// Model for search/autocomplete suggestions.
data class CoffeePlaceSuggestion(
    val id: String,
    val description: String
)

// =============================
// TagBuilder Helper
// =============================

// Helper object for building tag lists from Place attributes.
object TagBuilder {
    fun buildFoodOptions(place: Place): List<FoodOption> {
        fun getBool(attr: Place.BooleanPlaceAttributeValue?) = attr == Place.BooleanPlaceAttributeValue.TRUE
        val list = mutableListOf<FoodOption>()
        if (getBool(place.servesBreakfast)) list += FoodOption.BREAKFAST
        if (getBool(place.servesBrunch)) list += FoodOption.BRUNCH
        if (getBool(place.servesLunch)) list += FoodOption.LUNCH
        if (getBool(place.servesDinner)) list += FoodOption.DINNER
        if (getBool(place.servesDessert)) list += FoodOption.DESSERT
        if (getBool(place.servesVegetarianFood)) list += FoodOption.VEGETARIAN
        if (getBool(place.servesBeer)) list += FoodOption.BEER
        if (getBool(place.servesWine)) list += FoodOption.WINE
        if (getBool(place.servesCocktails)) list += FoodOption.COCKTAILS
        return list
    }
    fun buildAtmosphere(place: Place): List<Atmosphere> {
        fun getBool(attr: Place.BooleanPlaceAttributeValue?) = attr == Place.BooleanPlaceAttributeValue.TRUE
        val list = mutableListOf<Atmosphere>()
        if (getBool(place.outdoorSeating)) list += Atmosphere.OUTDOOR_SEATING
        if (getBool(place.allowsDogs)) list += Atmosphere.PET_FRIENDLY
        if (getBool(place.goodForChildren)) list += Atmosphere.KID_FRIENDLY
        if (getBool(place.goodForGroups)) list += Atmosphere.GROUP_FRIENDLY
        if (getBool(place.liveMusic)) list += Atmosphere.LIVE_MUSIC
        if (getBool(place.goodForWatchingSports)) list += Atmosphere.SPORTS_FRIENDLY
        return list
    }
    fun buildServiceOptions(place: Place): List<ServiceOption> {
        fun getBool(attr: Place.BooleanPlaceAttributeValue?) = attr == Place.BooleanPlaceAttributeValue.TRUE
        val list = mutableListOf<ServiceOption>()
        if (getBool(place.dineIn)) list += ServiceOption.DINE_IN
        if (getBool(place.takeout)) list += ServiceOption.TAKEOUT
        if (getBool(place.delivery)) list += ServiceOption.DELIVERY
        if (getBool(place.curbsidePickup)) list += ServiceOption.CURBSIDE_PICKUP
        return list
    }
    fun buildExtras(place: Place): List<ExtraFeature> {
        fun getBool(attr: Place.BooleanPlaceAttributeValue?) = attr == Place.BooleanPlaceAttributeValue.TRUE
        val list = mutableListOf<ExtraFeature>()
        if (place.parkingOptions != null) list += ExtraFeature.PARKING
        if (place.accessibilityOptions != null) list += ExtraFeature.WHEELCHAIR_ACCESS
        if (getBool(place.restroom)) list += ExtraFeature.RESTROOM
        if (getBool(place.menuForChildren)) list += ExtraFeature.KIDS_MENU
        return list
    }
}

// =============================
// Enums for Tagging
// =============================

// Food options available at a coffee place.
enum class FoodOption {
    BREAKFAST, BRUNCH, LUNCH, DINNER, DESSERT, VEGETARIAN, BEER, WINE, COCKTAILS
}

// Atmosphere attributes for a coffee place.
enum class Atmosphere {
    OUTDOOR_SEATING, PET_FRIENDLY, KID_FRIENDLY, GROUP_FRIENDLY, LIVE_MUSIC, SPORTS_FRIENDLY
}

// Service options available at a coffee place.
enum class ServiceOption {
    DINE_IN, TAKEOUT, DELIVERY, CURBSIDE_PICKUP
}

// Extra features available at a coffee place.
enum class ExtraFeature {
    PARKING, WHEELCHAIR_ACCESS, RESTROOM, KIDS_MENU
}
