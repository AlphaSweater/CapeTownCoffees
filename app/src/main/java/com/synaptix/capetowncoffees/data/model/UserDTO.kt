package com.synaptix.capetowncoffees.data.model

import com.google.firebase.firestore.DocumentId

// User Data Transfer Object (DTO) for Firestore
data class UserDTO(
    @DocumentId
    val id: String = "",    // Firestore document ID
    val email: String = "",
    val firstName: String? = null,
    val lastName: String? = null,
    val photoBase64: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis()
)