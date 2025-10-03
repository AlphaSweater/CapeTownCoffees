package com.synaptix.capetowncoffees.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.synaptix.capetowncoffees.data.common.BaseRepository
import com.synaptix.capetowncoffees.data.common.PaginatedResult
import com.synaptix.capetowncoffees.data.mapper.toDTO
import com.synaptix.capetowncoffees.data.mapper.toDomain
import com.synaptix.capetowncoffees.data.mapper.toDomainListForSingleUser
import com.synaptix.capetowncoffees.data.mapper.toDomainListWithUsers
import com.synaptix.capetowncoffees.data.mapper.toDto
import com.synaptix.capetowncoffees.data.model.AppReviewDTO
import com.synaptix.capetowncoffees.data.model.CoffeeUserDTO
import com.synaptix.capetowncoffees.domain.model.CoffeeReview
import com.synaptix.capetowncoffees.domain.model.InAppReview
import com.synaptix.capetowncoffees.domain.repository.ICoffeeReviewRepository
import com.synaptix.capetowncoffees.domain.repository.IPlacesApiRepository
import com.synaptix.capetowncoffees.domain.usecase.coffeeUser.GetUserProfileUseCase
import com.synaptix.capetowncoffees.util.OrderMode
import com.synaptix.capetowncoffees.util.myOrder
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.awaitAll
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoffeeReviewRepository @Inject constructor(
    firestore: FirebaseFirestore,
    private val placesApiRepository: IPlacesApiRepository,
    private val getUserProfileUseCase: GetUserProfileUseCase
) : BaseRepository<AppReviewDTO>(
    firestore = firestore,
    parentCollection = "coffee_places",
    childCollection = "reviews"
), ICoffeeReviewRepository {

    override fun getType(): Class<AppReviewDTO> = AppReviewDTO::class.java

    override suspend fun getReviewsForUser(
        reviewerId: String,
        limit: Int?
    ): Result<List<CoffeeReview>> =
        getAllByFieldFromCollectionGroup(
            childCollection = "reviews",
            fieldName = "reviewerId",
            value = reviewerId,
            limit = limit
        ).map { dtos ->
            val userDto = getUserProfileUseCase(reviewerId)
                .getOrNull()?.toDTO() ?: placeholderUser(reviewerId)
            dtos.toDomainListForSingleUser(userDto)
        }

    override suspend fun getReviewsForPlace(
        placeId: String,
        limit: Int?
    ): Result<List<CoffeeReview>> = coroutineScope {
        val dbDeferred = async { getAll(limit = limit, parentDocId = placeId) }
        val apiDeferred = async { placesApiRepository.getCoffeePlaceReviews(placeId) }

        val dbResult = dbDeferred.await()
        val apiResult = apiDeferred.await()

        val inApp: List<InAppReview> = dbResult
            .map { it.orEmpty() }
            .map { dtos ->
                val userIds = dtos.mapNotNull { it.userId }.distinct()
                val users = buildUserMapFromUseCase(userIds, strict = false)
                dtos.toDomainListWithUsers(users = users, strict = false)
            }
            .getOrElse { emptyList() } // DB failure shouldn't nuke Google reviews

        val google: List<CoffeeReview> = apiResult.getOrElse { emptyList() }

        Result.success((inApp + google).myOrder(OrderMode.DefaultSectioned))
    }

    override suspend fun addReview(
        coffeeReview: InAppReview,
        placeId: String
    ): Result<String> = create(coffeeReview.toDto(), parentDocId = placeId)

    override suspend fun deleteReview(
        reviewId: String,
        placeId: String
    ): Result<Unit> = delete(reviewId, parentDocId = placeId)

    override suspend fun getReview(
        reviewId: String,
        placeId: String
    ): Result<CoffeeReview?> =
        getById(reviewId, parentDocId = placeId).map { dto ->
            val uid = dto?.userId.orEmpty()
            val userDto = getUserProfileUseCase(uid).getOrNull()?.toDTO()
                ?: placeholderUser(uid.ifBlank { "unknown" })
            dto?.toDomain(userDto)
        }

    // ----------------------------
    // Pagination
    // ----------------------------
    override suspend fun getReviewsForUserPaginated(
        reviewerId: String,
        pageSize: Int,
        reset: Boolean,
        orderBy: Pair<String, Query.Direction>?,
        key: String
    ): PaginatedResult<CoffeeReview> {
        val query = firestore.collectionGroup("reviews")
            .whereEqualTo("reviewerId", reviewerId)

        val dtoPage = fetchPageFromCollectionGroup(
            childCollection = "reviews",
            pageSize = pageSize,
            reset = reset,
            query = query,
            orderBy = orderBy,
            key = key
        )

        val userDto = getUserProfileUseCase(reviewerId)
            .getOrNull()?.toDTO() ?: placeholderUser(reviewerId)

        return PaginatedResult(
            data = dtoPage.data.toDomainListForSingleUser(userDto),
            hasMore = dtoPage.hasMore
        )
    }

    override suspend fun getReviewsForPlacePaginated(
        placeId: String,
        pageSize: Int,
        reset: Boolean,
        orderBy: Pair<String, Query.Direction>?,
        key: String
    ): PaginatedResult<CoffeeReview> {
        val dtoPage = fetchPage(
            pageSize = pageSize,
            parentDocId = placeId,
            reset = reset,
            query = getCollection(placeId),
            orderBy = orderBy,
            key = key
        )

        val users = buildUserMapFromUseCase(
            dtoPage.data.mapNotNull { it.userId }.distinct(),
            strict = false
        )

        return PaginatedResult(
            data = dtoPage.data.toDomainListWithUsers(users = users, strict = false),
            hasMore = dtoPage.hasMore
        )
    }

    // ============================
    // Helpers
    // ============================
    private suspend fun buildUserMapFromUseCase(
        userIds: List<String>,
        strict: Boolean = false
    ): Map<String, CoffeeUserDTO> = supervisorScope {
        val distinct = userIds.distinct()
        if (distinct.isEmpty()) return@supervisorScope emptyMap()

        // launch all; one failure shouldn’t cancel others unless strict=true and we rethrow later
        val results = distinct.map { uid ->
            async {
                // Map success to DTO; if it fails and strict=false, fallback to placeholder.
                getUserProfileUseCase(uid)
                    .map { it.toDTO() }
                    .recoverCatching { e ->
                        if (strict) throw e
                        placeholderUser(uid)
                    }
                    .map { dto -> uid to dto }
                    .getOrThrow() // we want either (uid,dto) or throw if strict & failed
            }
        }.awaitAll()

        results.toMap()
    }

    private fun placeholderUser(userId: String) = CoffeeUserDTO(
        id = userId,
        fullName = "Unknown user",
        photoBase64 = null
    )
}
