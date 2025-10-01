package com.synaptix.capetowncoffees.domain.model

data class SavedList(
    val id: String,
    val name: String,
    val description: String?,
    val placeId: String,
    val isPublic: Boolean
)