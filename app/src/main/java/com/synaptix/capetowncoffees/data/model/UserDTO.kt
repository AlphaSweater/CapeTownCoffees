package com.synaptix.capetowncoffees.data.model

import com.google.firebase.firestore.DocumentId
import com.synaptix.capetowncoffees.util.TimeUtils

// User Data Transfer Object (DTO) for Firestore
data class UserDTO(
    @DocumentId
    val id: String = "",    // Firestore document ID
    val email: String = "",
    val firstName: String? = null,
    val lastName: String? = null,
    val photoBase64: String? = null,
    val createdAt: Long = TimeUtils.nowSeconds(),
    val updatedAt: Long = TimeUtils.nowSeconds(),
    val lastLoginAt: Long = TimeUtils.nowSeconds()
)