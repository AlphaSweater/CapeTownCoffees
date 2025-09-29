package com.synaptix.capetowncoffees.data.model

import com.google.firebase.firestore.DocumentId

// --- Domain Model ---
// Represents a user in the domain layer
data class Userv2(
    val id: String,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastLoginAt: Long
)

// --- DTO ---
// Represents a user document in Firestore
data class UserDTOv2(
    @DocumentId
    val id: String = "",    // Firestore document ID
    val email: String = "",
    val firstName: String? = null,
    val lastName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis()
)

// --- Mappers ---

// Extension functions to convert between User and UserDTO
fun Userv2.toDTO(): UserDTOv2 = UserDTOv2(
    id = id,
    email = email,
    firstName = firstName,
    lastName = lastName,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastLoginAt = lastLoginAt
)

// Converts UserDTO to User
fun UserDTOv2.toDomain(): Userv2 = Userv2(
    id = id,
    email = email,
    firstName = firstName,
    lastName = lastName,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastLoginAt = lastLoginAt
)