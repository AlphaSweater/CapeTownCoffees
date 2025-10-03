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

/**
 * Firestore-backed implementation of IReviewRepository for reviews.
 * Supports CRUD and paginated access for place and user reviews.
 */
class CoffeeReviewRepository(
    firestore: FirebaseFirestore,
    private val placesApiRepository: IPlacesApiRepository,
    private val getUserProfileUseCase: GetUserProfileUseCase
) : BaseRepository<AppReviewDTO>(
    firestore = firestore,
    parentCollection = "coffee_places",
    childCollection = "reviews"
), ICoffeeReviewRepository {

    override fun getType(): Class<AppReviewDTO> = AppReviewDTO::class.java

    // ----------------------------
    // CRUD
    // ----------------------------
    override suspend fun getReviewsForUser(
        reviewerId: String,
        limit: Int?
    ): Result<List<CoffeeReview>> {
        val dtoResult = getAllByFieldFromCollectionGroup(
            childCollection = "reviews",
            fieldName = "reviewerId",
            value = reviewerId,
            limit = limit
        )

        return dtoResult.fold(
            onSuccess = { dtos ->
                val userDto = getUserProfileUseCase(reviewerId)
                    .getOrNull()
                    ?.toDTO() ?: placeholderUser(reviewerId)

                // All user reviews are in-app; map to domain and return as List<CoffeeReview>
                val inApp: List<InAppReview> = dtos.toDomainListForSingleUser(userDto) // dto.placeId is used
                Result.success(inApp) // covariant to List<CoffeeReview>
            },
            onFailure = { e -> Result.failure(e) }
        )
    }

    override suspend fun getReviewsForPlace(
        placeId: String,
        limit: Int?
    ): Result<List<CoffeeReview>> = coroutineScope {
        val dbDeferred = async { getAll(limit = limit, parentDocId = placeId) }
        val apiDeferred = async { placesApiRepository.getCoffeePlaceReviews(placeId) }

        val dbResult = dbDeferred.await()
        val apiResult = apiDeferred.await()

        val dtos: List<AppReviewDTO> = dbResult.getOrNull().orEmpty()

        // 1) Build user map for these DTOs (parallel fetch)
        val userIds: List<String> = dtos.mapNotNull { it.userId }.distinct()
        val usersMap: Map<String, CoffeeUserDTO> = buildUserMapFromUseCase(userIds, strict = false)


        // 2) Map in-app reviews using extension to convert to domain models with user info
        val inApp: List<InAppReview> =
            if (usersMap.isNotEmpty()) dtos.toDomainListWithUsers(
                users = usersMap,
                strict = false
            ) else emptyList()

        // 3) Google reviews already come as domain models
        val google: List<CoffeeReview> = apiResult.getOrNull().orEmpty()

        // 4) Merge and apply default sectioned ordering
        val mergedReviews = (inApp + google).myOrder(OrderMode.DefaultSectioned)

        Result.success(mergedReviews)
    }

    override suspend fun addReview(
        coffeeReview: InAppReview,
        placeId: String
    ): Result<String> {
        // New extension: domain -> DTO
        val dto = coffeeReview.toDto()
        return create(dto, parentDocId = placeId)
    }

    override suspend fun deleteReview(
        reviewId: String,
        placeId: String
    ): Result<Unit> = delete(reviewId, parentDocId = placeId)

    override suspend fun getReview(
        reviewId: String,
        placeId: String
    ): Result<CoffeeReview?> {
        val dtoResult = getById(reviewId, parentDocId = placeId)
        return dtoResult.fold(
            onSuccess = { dto ->
                // Single DTO -> domain (in-app)
                val userDto = getUserProfileUseCase(dto?.userId ?: "")
                    .getOrNull()
                    ?.toDTO() ?: placeholderUser(dto?.userId ?: "unknown")

                Result.success(dto?.toDomain(userDto))
            },
            onFailure = { e -> Result.failure(e) }
        )
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
            .getOrNull()
            ?.toDTO() ?: placeholderUser(reviewerId)

        // Page of DTOs -> domain
        val inApp: List<InAppReview> = dtoPage.data.toDomainListForSingleUser(userDto)

        return PaginatedResult(
            data = inApp, // covariant
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
        val query = getCollection(placeId)

        val dtoPage = fetchPage(
            pageSize = pageSize,
            parentDocId = placeId,
            reset = reset,
            query = query,
            orderBy = orderBy,
            key = key
        )

        val dtos: List<AppReviewDTO> = dtoPage.data

        val userIds: List<String> = dtos.mapNotNull { it.userId }.distinct()
        val usersMap: Map<String, CoffeeUserDTO> = buildUserMapFromUseCase(userIds, strict = false)


        // 2) Map in-app reviews using extension to convert to domain models with user info
        val inApp: List<InAppReview> =
            if (usersMap.isNotEmpty()) dtos.toDomainListWithUsers(
                users = usersMap,
                strict = false
            ) else emptyList()

        return PaginatedResult(
            data = inApp, // covariant
            hasMore = dtoPage.hasMore
        )
    }

    // ============================
    // Helpers
    // ============================

    /**
     * Fetch profiles for distinct userIds in parallel and convert to CoffeeUserDTO map.
     * If 'strict' is true, throws if any profile is missing; otherwise, uses a placeholder.
     */
    private suspend fun buildUserMapFromUseCase(
        userIds: List<String>,
        strict: Boolean = false
    ): Map<String, CoffeeUserDTO> = coroutineScope {
        val distinct = userIds.distinct()
        if (distinct.isEmpty()) return@coroutineScope emptyMap()

        // kick off all fetches
        val jobs = distinct.associateWith { uid ->
            async {
                getUserProfileUseCase(uid)
            }
        }

        // gather results
        val pairs: List<Pair<String, CoffeeUserDTO>?> = jobs.map { (uid, deferred) ->
            val res = deferred.await()
            res.fold(
                onSuccess = { profile -> uid to profile.toDTO() },
                onFailure = {
                    if (strict) null else uid to placeholderUser(uid)
                }
            )
        }

        val present = pairs.filterNotNull().toMap()
        if (strict && present.size != distinct.size) {
            val missing = distinct - present.keys
            error("Missing user profiles for userIds=$missing")
        }
        present
    }

    private fun placeholderUser(userId: String) = CoffeeUserDTO(
        id = userId,
        fullName = "Unknown user",
        photoBase64 = null
    )
}