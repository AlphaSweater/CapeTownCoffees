package com.synaptix.capetowncoffees.data.model

import com.google.firebase.firestore.DocumentId
import com.synaptix.capetowncoffees.util.TimeUtils

data class CoffeeShopDTO(
    @DocumentId
    val id: String = "",      // Firestore document ID and Places API Place ID
    val name: String,
    val addedAt: Long = TimeUtils.nowSeconds()
)