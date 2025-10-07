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

/**
 * Data Transfer Object (DTO) representing a review submitted via the app for a specific place.
 *
 * Each AppReviewDTO instance corresponds to a single review document in Firestore.
 * - The 'id' field is the Firestore document ID for this review.
 * - The 'userId' field is a foreign key referencing the user who submitted the review. This can be used to fetch user details (e.g., display name, avatar) when constructing a full domain review object for UI or business logic.
 * - The 'placeId' field links the review to a specific place.
 * - The 'rating' field stores the user's rating for the place (e.g., out of 5 stars).
 * - The 'createdAt' field is a timestamp (utc seconds) of when the review was created.
 * - The 'text' and 'textLanguageCode' fields contain the review text and its language code, respectively.
 * - The 'originalText' and 'originalTextLanguageCode' fields are used if the review was translated from another language.
 *
 * This DTO is used for storing and retrieving review data from Firestore, and for mapping to domain models that may require additional user or place details.
 */
data class AppReviewDTO(
    @DocumentId
    val id: String? = null,       // Firestore document ID
    val userId: String? = null,   // FK → User; used to fetch user details for full domain review
    val placeId: String? = null,  // FK → PlaceDTO.id
    val rating: Double?,
    val createdAt: Long? = null,
    val text: String? = null,
    val textLanguageCode: String? = null,
    val originalText: String? = null,
    val originalTextLanguageCode: String? = null
)
