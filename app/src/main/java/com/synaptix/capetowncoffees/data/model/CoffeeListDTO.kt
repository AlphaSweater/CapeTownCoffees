package com.synaptix.capetowncoffees.data.model

import com.google.firebase.firestore.DocumentId
import com.synaptix.capetowncoffees.util.TimeUtils
import com.synaptix.capetowncoffees.domain.model.CoffeeList as DomainList

data class UserListDTO(
    @DocumentId
    val id: String = "",  // Firestore document ID
    val name: String? = null,
    val description: String? = null,
    @field:JvmField
    val isPublic: Boolean = false,
    val placeIds: List<String>? = null,
    val createdAt: Long = TimeUtils.nowSeconds(),
    val updatedAt: Long = TimeUtils.nowSeconds(),
) {
    companion object {
        fun fromDomain(list: DomainList): UserListDTO = UserListDTO(
            id = list.id,
            name = list.name,
            description = list.description,
            isPublic = list.isPublic,
            placeIds = list.placeIds,
            createdAt = TimeUtils.nowSeconds(),
            updatedAt = TimeUtils.nowSeconds()
        )
    }
}

fun UserListDTO.toDomain(): DomainList = DomainList(
    id = id,
    name = name ?: "",
    description = description,
    isPublic = isPublic,
    placeIds = placeIds ?: emptyList()
)

fun DomainList.toDTO(): UserListDTO = UserListDTO.fromDomain(this)
