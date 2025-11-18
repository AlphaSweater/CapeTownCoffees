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

// ─────────── Model — AppReviewDTO ───────────
// Match CoffeePlaceDTO style: all constructor params have defaults so Firebase can
// instantiate via the primary constructor (no explicit secondary no-arg constructor needed).
data class AppReviewDTO(
    @DocumentId
    val id: String? = "",                   // Firestore document id

    val userId: String? = null,            // reviewer user id
    val placeId: String? = null,           // target place id
    val rating: Double? = null,            // star rating (e.g., 0..5)
    val createdAt: Long? = null,           // utc seconds when created
    val text: String? = null,              // review body
    val textLanguageCode: String? = null,  // language of text (BCP-47)
    val originalText: String? = null,      // pre-translation source text
    val originalTextLanguageCode: String? = null // language of source text
)
