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