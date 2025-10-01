package com.synaptix.capetowncoffees.data.repository

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.synaptix.capetowncoffees.data.common.BaseRepository
import com.synaptix.capetowncoffees.domain.model.SavedList
import com.synaptix.capetowncoffees.domain.repository.ISavedListRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavedListRepositoryImpl @Inject constructor(
    private val db: FirebaseFirestore
) : BaseRepository<SavedList>(db), ISavedListRepository {

    override val collection: CollectionReference
        get() = firestore.collection("saved_lists")

    override fun getType(): Class<SavedList> = SavedList::class.java

    override suspend fun createList(
        name: String,
        description: String?,
        isPublic: Boolean
    ): String {
        val id = collection.document().id
        val item = SavedList(
            id = id,
            name = name.trim(),
            description = description?.trim()?.takeIf { it.isNotBlank() },
            isPublic = isPublic,
            placeIds = emptyList(),
        )
        return create(item, id).getOrElse { throw it }
    }
}