package com.synaptix.capetowncoffees.domain.model

import kotlin.collections.List

data class UserList(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val isPublic: Boolean = false,
    val placeIds: List<String> = emptyList()
) {
    // Required empty constructor for Firestore
    constructor() : this("", "", null, false, emptyList())
}