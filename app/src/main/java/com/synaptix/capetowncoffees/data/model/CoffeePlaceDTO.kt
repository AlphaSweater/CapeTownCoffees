package com.synaptix.capetowncoffees.data.model

import com.google.firebase.firestore.DocumentId
import com.synaptix.capetowncoffees.util.CoffeeTimeUtils

data class CoffeePlaceDTO(
    @DocumentId
    val id: String = "",      // Firestore document ID and Places API Place ID
    val addedAt: Long,
) {
    companion object {
        fun createNew(id: String): CoffeePlaceDTO {
            return CoffeePlaceDTO(
                id = id,
                addedAt = CoffeeTimeUtils.nowSeconds()
            )
        }
    }
}