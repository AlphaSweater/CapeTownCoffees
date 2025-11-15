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
import com.synaptix.capetowncoffees.domain.model.Tag
import com.synaptix.capetowncoffees.domain.model.TagCategory
import com.synaptix.capetowncoffees.util.CoffeeTimeUtils

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

    // Ratings
    val rating: Double? = null,
    val ratingCount: Int? = null,

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
        fun createNew(id: String): CoffeePlaceDTO {
            return CoffeePlaceDTO(
                id = id,
                addedAt = CoffeeTimeUtils.nowSeconds()
            )
        }
    }
}

//======================================================================================
// TagDTO
//--------------------------------------------------------------------------------------
// Represents a tag stored in:
//     /coffeePlaces/{placeId}/tags/{tagId}
//======================================================================================
data class TagDTO(

    @DocumentId
    val id: String = "",

    val name: String = "",
    val category: String = TagCategory.EXTRA.name,

    val createdAt: Long = CoffeeTimeUtils.nowSeconds()
)