package com.synaptix.capetowncoffees.domain.model

// Domain model for user data in the application domain layer
data class User(
    val id: String,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val photoBase64: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val lastLoginAt: Long
)
