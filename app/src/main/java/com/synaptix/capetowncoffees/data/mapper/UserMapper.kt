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

// ─────────── Mapper — User ⇄ DTO ───────────
// We convert between domain users and transport (DTO) users for persistence/network.
// Keep mapping flat and predictable; no I/O or side effects here.

// ─────────── Constants ───────────
private const val EMPTY_NAME: String = ""

// ─────────── Public API ───────────

// Domain → DTO. We mirror fields 1:1 so writes remain simple.
public fun CoffeeUser.toDTO(): CoffeeUserDTO = CoffeeUserDTO(
    id = id,
    email = email,
    fullName = fullName,
    photoBase64 = photoBase64,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastLoginAt = lastLoginAt
)

// DTO → Domain. We guard against a null fullName so UI logic can assume a string.
public fun CoffeeUserDTO.toDomain(): CoffeeUser = CoffeeUser(
    id = id,
    email = email,
    fullName = fullName ?: EMPTY_NAME,
    photoBase64 = photoBase64,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastLoginAt = lastLoginAt
)