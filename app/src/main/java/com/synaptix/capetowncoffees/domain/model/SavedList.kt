package com.synaptix.capetowncoffees.domain.model

data class SavedList(
    val id: String,
    val name: String,
    val description: String?,
    val isPublic: Boolean,
    val placeId: String
)