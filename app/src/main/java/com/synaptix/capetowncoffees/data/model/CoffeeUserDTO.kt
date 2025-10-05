package com.synaptix.capetowncoffees.data.model

import com.google.firebase.firestore.DocumentId
import com.synaptix.capetowncoffees.util.CoffeeTimeUtils

// User Data Transfer Object (DTO) for Firestore
data class CoffeeUserDTO(
    @DocumentId
    val id: String = "",    // Firestore document ID
    val email: String = "",
    val fullName: String? = null,
    val photoBase64: String? = null,
    val createdAt: Long = CoffeeTimeUtils.nowSeconds(),
    val updatedAt: Long = CoffeeTimeUtils.nowSeconds(),
    val lastLoginAt: Long = CoffeeTimeUtils.nowSeconds()
) {
    companion object {
        fun newUserDTO(
            id: String,
            email: String,
            fullName: String
        ): CoffeeUserDTO {
            return CoffeeUserDTO(
                id = id,
                email = email,
                fullName = fullName,
                photoBase64 = null,
                createdAt = CoffeeTimeUtils.nowSeconds(),
                updatedAt = CoffeeTimeUtils.nowSeconds(),
                lastLoginAt = CoffeeTimeUtils.nowSeconds()
            )
        }
    }
}