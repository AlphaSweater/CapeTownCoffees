package com.synaptix.capetowncoffees.domain.model

import kotlin.collections.List

data class CoffeeList(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val isPublic: Boolean = false,
    val placeIds: List<String> = emptyList()
)