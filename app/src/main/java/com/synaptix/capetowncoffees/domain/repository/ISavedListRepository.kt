package com.synaptix.capetowncoffees.domain.repository

interface ISavedListRepository {
    suspend fun createList(name: String, description: String?, isPublic: Boolean, placeId: String): String
}