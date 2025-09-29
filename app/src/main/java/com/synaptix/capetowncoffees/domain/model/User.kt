package com.synaptix.capetowncoffees.domain.model

// Domain model for user data in the application
data class User(
    val id: String,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastLoginAt: Long
)
