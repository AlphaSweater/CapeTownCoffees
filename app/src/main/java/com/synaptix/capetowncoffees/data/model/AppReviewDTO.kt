package com.synaptix.capetowncoffees.data.model

import com.google.firebase.firestore.DocumentId

// ─────────── Model — AppReviewDTO ───────────
// Firestore DTO for an in-app place review. We keep fields nullable to mirror storage
// and avoid accidental defaults; domain mapping can enforce stronger types.
public data class AppReviewDTO(
    @DocumentId
    val id: String? = null,                  // Firestore document id
    val userId: String? = null,              // reviewer user id
    val placeId: String? = null,             // target place id
    val rating: Double?,                     // star rating (e.g., 0..5)
    val createdAt: Long? = null,             // utc seconds when created
    val text: String? = null,                // review body
    val textLanguageCode: String? = null,    // language of text (BCP-47)
    val originalText: String? = null,        // pre-translation source text
    val originalTextLanguageCode: String? = null // language of source text
)
