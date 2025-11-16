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
//* ChatGPT assisted in defining clear and consistent data models for DTO classes,
//ensuring compatibility with APIs and repository layers.
//* It was also used to format and document class structures.
//* It also helped generate useful comments
//======================================================================================

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
    val lastLoginAt: Long = CoffeeTimeUtils.nowSeconds(),
    val reviewCount: Int = 0
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
                lastLoginAt = CoffeeTimeUtils.nowSeconds(),
                reviewCount = 0
            )
        }
    }
}