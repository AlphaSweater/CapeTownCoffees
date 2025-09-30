package com.synaptix.capetowncoffees.data.model

import com.google.firebase.firestore.DocumentId

data class ReviewDTO(
    @DocumentId
    val id: String? = null,       // Firestore document ID
    val userId: String,           // FK → User
    val placeId: String,          // FK → PlaceDTO.id
    val rating: Double,
    val comment: String,
    val createdAt: Long = System.currentTimeMillis()
)
