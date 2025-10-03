package com.synaptix.capetowncoffees.data.mapper

import com.synaptix.capetowncoffees.data.model.CoffeeUserDTO
import com.synaptix.capetowncoffees.domain.model.CoffeeUser

// --- Mappers ---

// Extension functions to convert between User and UserDTO
fun CoffeeUser.toDTO(): CoffeeUserDTO = CoffeeUserDTO(
    id = id,
    email = email,
    fullName = fullName,
    photoBase64 = photoBase64,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastLoginAt = lastLoginAt
)

// Converts UserDTO to User
fun CoffeeUserDTO.toDomain(): CoffeeUser = CoffeeUser(
    id = id,
    email = email,
    fullName = fullName ?: "",
    photoBase64 = photoBase64,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastLoginAt = lastLoginAt
)