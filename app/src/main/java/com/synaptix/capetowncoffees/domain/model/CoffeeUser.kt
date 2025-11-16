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
//* ChatGPT was used to assist with the development, design, and debugging of this file.
//* AI support was used for learning purposes, improving clarity and resolving issues.
//* It also helped generate useful comments
//======================================================================================

package com.synaptix.capetowncoffees.domain.model

// Domain model for user data in the application domain layer
data class CoffeeUser(
    val id: String,
    val email: String,
    val fullName: String,
    val photoBase64: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val lastLoginAt: Long,
    val reviewCount: Int = 0
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
