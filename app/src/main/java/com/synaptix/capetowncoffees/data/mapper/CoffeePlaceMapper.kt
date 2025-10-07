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

object CoffeePlaceMapper {
    fun toFull(place: Place): CoffeePlaceFull = CoffeePlaceFull(
        id = place.id ?: "",
        name = place.displayName,
        address = place.formattedAddress,
        location = place.location,
        googleMapsUrl = place.googleMapsUri?.toString(),
        rating = place.rating,
        ratingCount = place.userRatingCount,
        images = place.photoMetadatas,
        primaryType = place.primaryType,
        types = place.placeTypes?.map { it.toString() },
        businessStatus = place.businessStatus?.name,
        currentOpeningHours = place.currentOpeningHours?.weekdayText,
        nationalPhoneNumber = place.nationalPhoneNumber,
        internationalPhoneNumber = place.internationalPhoneNumber,
        websiteUrl = place.websiteUri?.toString(),
        tags = TagExtractor.extract(place)
    )

    fun toLite(place: Place): CoffeePlaceLite = CoffeePlaceLite(
        id = place.id ?: "",
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

    fun toSuggestion(place: Place): CoffeePlaceSuggestion = CoffeePlaceSuggestion(
        id = place.id ?: "",
        name = place.displayName
    )
}