package com.synaptix.capetowncoffees.data.model

import com.google.firebase.firestore.DocumentId
import com.synaptix.capetowncoffees.util.TimeUtils

data class ListDTO(
    @DocumentId
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val isPublic: Boolean? = null,
    val placeIds: List<String>? = null,
    val createdAt: Long = TimeUtils.nowSeconds(),
    val updatedAt: Long = TimeUtils.nowSeconds(),
)