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
import com.synaptix.capetowncoffees.domain.model.CoffeeList

data class UserListDTO(
    @DocumentId
    val id: String = "",  // Firestore document ID
    val name: String? = null,
    val description: String? = null,
    @field:JvmField
    val isPublic: Boolean = false,
    val placeIds: List<String>? = null,
    val createdAt: Long = CoffeeTimeUtils.nowSeconds(),
    val updatedAt: Long = CoffeeTimeUtils.nowSeconds(),
) {
    companion object {
        fun fromDomain(list: CoffeeList): UserListDTO = UserListDTO(
            id = list.id,
            name = list.name,
            description = list.description,
            isPublic = list.isPublic,
            placeIds = list.placeIds,
            createdAt = CoffeeTimeUtils.nowSeconds(),
            updatedAt = CoffeeTimeUtils.nowSeconds()
        )
    }
}

fun UserListDTO.toDomain(): CoffeeList = CoffeeList(
    id = id,
    name = name ?: "",
    description = description,
    isPublic = isPublic,
    placeIds = placeIds ?: emptyList()
)

fun CoffeeList.toDTO(): UserListDTO = UserListDTO.fromDomain(this)
