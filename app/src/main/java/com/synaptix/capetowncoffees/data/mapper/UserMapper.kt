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