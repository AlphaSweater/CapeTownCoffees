package com.synaptix.capetowncoffees.data.mapper

import com.synaptix.capetowncoffees.data.model.UserDTO
import com.synaptix.capetowncoffees.domain.model.User

// --- Mappers ---

// Extension functions to convert between User and UserDTO
fun User.toDTO(): UserDTO = UserDTO(
    id = id,
    email = email,
    firstName = firstName,
    lastName = lastName,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastLoginAt = lastLoginAt
)

// Converts UserDTO to User
fun UserDTO.toDomain(): User = User(
    id = id,
    email = email,
    firstName = firstName,
    lastName = lastName,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastLoginAt = lastLoginAt
)