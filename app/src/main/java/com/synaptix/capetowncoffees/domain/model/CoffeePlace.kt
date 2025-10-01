package com.synaptix.capetowncoffees.domain.model

import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.google.android.libraries.places.api.model.Place

/**
 * A clean, compact model for coffee shops.
 * Attributes are grouped into enums/lists so UI can render tags easily.
 * No direct Google dependencies; all types are app-friendly.
 */
data class CoffeePlace(
    // 🏠 Core Info
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

    // Contact Info
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

    // Other attributes
    val priceLevel: Int? = null // Google price level (0-4)

) {
    companion object {
        private fun getBooleanAttribute(attr: Place.BooleanPlaceAttributeValue?): Boolean {
            return attr == Place.BooleanPlaceAttributeValue.TRUE
        }

        fun fromPlace(place: Place): CoffeePlace {
            return CoffeePlace(
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
                foodOptions = buildFoodOptions(place),
                atmosphere = buildAtmosphere(place),
                serviceOptions = buildServiceOptions(place),
                extras = buildExtras(place),
                // TODO: Fetch and map Google reviews if needed
                reviews = null,
                priceLevel = place.priceLevel
            )
        }

        // --- Enum Builders ---
        private fun buildFoodOptions(place: Place): List<FoodOption> {
            val list = mutableListOf<FoodOption>()
            if (getBooleanAttribute(place.servesBreakfast)) list += FoodOption.BREAKFAST
            if (getBooleanAttribute(place.servesBrunch)) list += FoodOption.BRUNCH
            if (getBooleanAttribute(place.servesLunch)) list += FoodOption.LUNCH
            if (getBooleanAttribute(place.servesDinner)) list += FoodOption.DINNER
            if (getBooleanAttribute(place.servesDessert)) list += FoodOption.DESSERT
            if (getBooleanAttribute(place.servesVegetarianFood)) list += FoodOption.VEGETARIAN
            if (getBooleanAttribute(place.servesBeer)) list += FoodOption.BEER
            if (getBooleanAttribute(place.servesWine)) list += FoodOption.WINE
            if (getBooleanAttribute(place.servesCocktails)) list += FoodOption.COCKTAILS
            return list
        }

        private fun buildAtmosphere(place: Place): List<Atmosphere> {
            val list = mutableListOf<Atmosphere>()
            if (getBooleanAttribute(place.outdoorSeating)) list += Atmosphere.OUTDOOR_SEATING
            if (getBooleanAttribute(place.allowsDogs)) list += Atmosphere.PET_FRIENDLY
            if (getBooleanAttribute(place.goodForChildren)) list += Atmosphere.KID_FRIENDLY
            if (getBooleanAttribute(place.goodForGroups)) list += Atmosphere.GROUP_FRIENDLY
            if (getBooleanAttribute(place.liveMusic)) list += Atmosphere.LIVE_MUSIC
            if (getBooleanAttribute(place.goodForWatchingSports)) list += Atmosphere.SPORTS_FRIENDLY
            return list
        }

        private fun buildServiceOptions(place: Place): List<ServiceOption> {
            val list = mutableListOf<ServiceOption>()
            if (getBooleanAttribute(place.dineIn)) list += ServiceOption.DINE_IN
            if (getBooleanAttribute(place.takeout)) list += ServiceOption.TAKEOUT
            if (getBooleanAttribute(place.delivery)) list += ServiceOption.DELIVERY
            if (getBooleanAttribute(place.curbsidePickup)) list += ServiceOption.CURBSIDE_PICKUP
            return list
        }

        private fun buildExtras(place: Place): List<ExtraFeature> {
            val list = mutableListOf<ExtraFeature>()
            if (place.parkingOptions != null) list += ExtraFeature.PARKING
            if (place.accessibilityOptions != null) list += ExtraFeature.WHEELCHAIR_ACCESS
            if (getBooleanAttribute(place.restroom)) list += ExtraFeature.RESTROOM
            if (getBooleanAttribute(place.menuForChildren)) list += ExtraFeature.KIDS_MENU
            return list
        }
    }
}

/**
 * Enums to group attributes into clean categories.
 * Great for displaying tags in the UI (chips/badges).
 */
enum class FoodOption {
    BREAKFAST, BRUNCH, LUNCH, DINNER, DESSERT, VEGETARIAN, BEER, WINE, COCKTAILS
}

enum class Atmosphere {
    OUTDOOR_SEATING, PET_FRIENDLY, KID_FRIENDLY, GROUP_FRIENDLY, LIVE_MUSIC, SPORTS_FRIENDLY
}

enum class ServiceOption {
    DINE_IN, TAKEOUT, DELIVERY, CURBSIDE_PICKUP
}

enum class ExtraFeature {
    PARKING, WHEELCHAIR_ACCESS, RESTROOM, KIDS_MENU
}