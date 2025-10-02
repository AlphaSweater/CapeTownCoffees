package com.synaptix.capetowncoffees.data.model

import com.google.firebase.firestore.DocumentId
import com.synaptix.capetowncoffees.util.TimeUtils
import com.synaptix.capetowncoffees.domain.model.UserList as DomainList

data class ListDTO(
    @DocumentId
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val isPublic: Boolean? = null,
    val placeIds: kotlin.collections.List<String>? = null,
    val createdAt: Long = TimeUtils.nowSeconds(),
    val updatedAt: Long = TimeUtils.nowSeconds(),
) {
    companion object {
        fun fromDomain(list: DomainList): ListDTO = ListDTO(
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

fun ListDTO.toDomain(): DomainList = DomainList(
    id = id ?: "",
    name = name ?: "",
    description = description,
    isPublic = isPublic ?: false,
    placeIds = placeIds ?: emptyList()
)

fun DomainList.toDTO(): ListDTO = ListDTO.fromDomain(this)
