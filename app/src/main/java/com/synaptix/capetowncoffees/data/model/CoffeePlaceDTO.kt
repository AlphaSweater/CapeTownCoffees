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
//* ChatGPT assisted in defining clear and consistent data models for DTO classes,
//ensuring compatibility with APIs and repository layers.
//* It was also used to format and document class structures.
//* It also helped generate useful comments
//======================================================================================

package com.synaptix.capetowncoffees.data.model

import com.google.firebase.firestore.DocumentId
import com.synaptix.capetowncoffees.data.mapper.CoffeePlaceMapper
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceFull

//======================================================================================
// CoffeePlaceDTO
//======================================================================================
data class CoffeePlaceDTO(

    @DocumentId
    val id: String = "",    // Firestore document ID == Google Place ID

    // Basic info
    val name: String? = null,
    val address: String? = null,

    // Location
    val locationLat: Double? = null,
    val locationLng: Double? = null,

    // URLs
    val googleMapsUrl: String? = null,
    val websiteUrl: String? = null,
    val imageUrl: String? = null,

    // Ratings & counts
    val googleRating: Double? = null,
    val googleRatingCount: Int? = null,
    val appRating: Double? = null,
    val appRatingCount: Int? = null,

    // Place types
    val primaryType: String? = null,
    val types: List<String> = emptyList(),

    // Business hours & status
    val businessStatus: String? = null,
    val currentOpeningHours: List<String> = emptyList(),

    // Contact info
    val nationalPhoneNumber: String? = null,
    val internationalPhoneNumber: String? = null,

    // Pricing
    val priceLevel: Int? = null,

    // Metadata for caching
    val addedAt: Long = 0L,
    val updatedAt: Long? = null
) {
    companion object {
        fun fromFull(place: CoffeePlaceFull) = CoffeePlaceMapper.toDto(place)
    }
}