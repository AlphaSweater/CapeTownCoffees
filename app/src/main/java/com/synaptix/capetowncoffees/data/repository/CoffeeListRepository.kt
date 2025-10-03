package com.synaptix.capetowncoffees.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.synaptix.capetowncoffees.data.common.BaseRepository
import com.synaptix.capetowncoffees.data.model.UserListDTO
import com.synaptix.capetowncoffees.data.model.toDomain
import com.synaptix.capetowncoffees.data.model.toDTO
import com.synaptix.capetowncoffees.domain.model.CoffeeList
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite
import com.synaptix.capetowncoffees.domain.repository.ICoffeeListRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoffeeListRepository @Inject constructor(
    private val auth: FirebaseAuth,
    firestore: FirebaseFirestore
) : BaseRepository<UserListDTO>(
    firestore = firestore,
    parentCollection = "users",
    parentDocumentId = auth.currentUser?.uid,
    childCollection = "saved_lists"
), ICoffeeListRepository {

    override fun getType(): Class<UserListDTO> = UserListDTO::class.java

    override suspend fun getLists(): List<CoffeeList> {
        return getAll().getOrElse { emptyList() }
            .map { it.toDomain() }
    }

    override suspend fun createList(newCoffeeList: CoffeeList): String {
        val dto = newCoffeeList.toDTO()
        return create(dto).getOrThrow()
    }

    override suspend fun deleteList(id: String) {
        delete(id).getOrThrow()
    }

    override fun observeLists(): Flow<List<CoffeeList>> =
        observeCollection().let { flow ->
            flow.map { list -> list.map { it.toDomain() } }
        }

    override fun observeList(id: String): Flow<CoffeeList?> =
        observeDocument(id).let { flow ->
            flow.map { it?.toDomain() }
        }
}
