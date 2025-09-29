package com.example.capetowncoffees.data.model

data class UserDTO(
    @DocumentId
    val id: String = "", // Firestore document ID
    val email: String = "",
    val firstName: String? = null,
    val lastName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis()
)