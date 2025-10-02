package com.synaptix.capetowncoffees.data.model

import com.google.firebase.firestore.DocumentId
import com.synaptix.capetowncoffees.domain.model.User
import com.synaptix.capetowncoffees.util.TimeUtils

// User Data Transfer Object (DTO) for Firestore
data class UserDTO(
    @DocumentId
    val id: String = "",    // Firestore document ID
    val email: String = "",
    val fullName: String? = null,
    val photoBase64: String? = null,
    val createdAt: Long = TimeUtils.nowSeconds(),
    val updatedAt: Long = TimeUtils.nowSeconds(),
    val lastLoginAt: Long = TimeUtils.nowSeconds()
) {
    companion object {
        fun newUserDTO(
            id: String,
            email: String,
            fullName: String
        ): UserDTO {
            return UserDTO(
                id = id,
                email = email,
                fullName = fullName,
                photoBase64 = null,
                createdAt = TimeUtils.nowSeconds(),
                updatedAt = TimeUtils.nowSeconds(),
                lastLoginAt = TimeUtils.nowSeconds()
            )
        }
    }
}