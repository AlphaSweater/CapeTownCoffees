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
//* ChatGPT was used to guide the creation of mapper classes responsible for converting
//between entities, DTOs, and domain models.
//* It also helped ensure consistent naming and mapping logic throughout the project.
//* It also helped generate useful comments
//======================================================================================

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