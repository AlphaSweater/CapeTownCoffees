package com.synaptix.capetowncoffees.domain.model

// Domain model for user data in the application domain layer
data class CoffeeUser(
    val id: String,
    val email: String,
    val fullName: String,
    val photoBase64: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val lastLoginAt: Long
) {
    companion object {
        fun newUser(
            id: String,
            email: String,
            fullName: String
        ): CoffeeUser {
            return CoffeeUser(
                id = id,
                email = email,
                fullName = fullName,
                photoBase64 = null,
                createdAt = 0L,
                updatedAt = 0L,
                lastLoginAt = 0L
            )
        }
    }
}
