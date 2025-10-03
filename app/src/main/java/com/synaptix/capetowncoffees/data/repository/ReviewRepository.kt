package com.synaptix.capetowncoffees.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.synaptix.capetowncoffees.data.common.BaseRepository
import com.synaptix.capetowncoffees.data.common.PaginatedResult
import com.synaptix.capetowncoffees.data.model.AppReviewDTO
import com.synaptix.capetowncoffees.domain.model.Review
import com.synaptix.capetowncoffees.domain.model.toDTO
import com.synaptix.capetowncoffees.domain.model.toDomain
import com.synaptix.capetowncoffees.domain.repository.IReviewRepository

class ReviewRepository(
    firestore: FirebaseFirestore
) : BaseRepository<AppReviewDTO>(
    firestore = firestore,
    parentCollection = "coffee_places",
    childCollection = "reviews"
), IReviewRepository {

    override fun getType(): Class<AppReviewDTO> = AppReviewDTO::class.java

    override suspend fun getReviewsForUser(reviewerId: String, limit: Int?): List<Review> {
        val dtoResult = getAllByFieldFromCollectionGroup("reviews", "reviewerId", reviewerId, limit)
        return dtoResult.getOrElse { emptyList() }.map { it.toDomain() }
    }

    override suspend fun getReviewsForPlace(placeId: String, limit: Int?): List<Review> {
        val dtoResult = getAll(limit = limit, parentDocId = placeId)
        return dtoResult.getOrElse { emptyList() }.map { it.toDomain() }
    }

    override suspend fun addReview(review: Review, placeId: String): String {
        val dto = review.toDTO()
        requireNotNull(dto) { "Only IN_APP reviews can be added." }
        return create(dto, parentDocId = placeId).getOrThrow()
    }

    override suspend fun deleteReview(reviewId: String, placeId: String) {
        delete(reviewId, parentDocId = placeId).getOrThrow()
    }

    override suspend fun getReview(reviewId: String, placeId: String): Review? {
        val dtoResult = getById(reviewId, parentDocId = placeId)
        return dtoResult.getOrNull()?.toDomain()
    }

    /**
     * Gets paginated reviews for a user from all places (collection group query).
     * @param reviewerId The user ID.
     * @param pageSize Number of reviews per page.
     * @param reset If true, resets pagination for this user.
     * @param orderBy Optional order by field and direction.
     * @param key Unique key for pagination context (default: "user_" + reviewerId).
     * @return PaginatedResult<Review> containing reviews and hasMore flag.
     */
    suspend fun getReviewsForUserPaginated(
        reviewerId: String,
        pageSize: Int,
        reset: Boolean = false,
        orderBy: Pair<String, com.google.firebase.firestore.Query.Direction>? = null,
        key: String = "user_$reviewerId"
    ): PaginatedResult<Review> {
        val query = firestore.collectionGroup("reviews")
            .whereEqualTo("reviewerId", reviewerId)
        val dtoResult = fetchPageFromCollectionGroup(
            childCollection = "reviews",
            pageSize = pageSize,
            reset = reset,
            query = query,
            orderBy = orderBy,
            key = key
        )
        return PaginatedResult(
            data = dtoResult.data.map { it.toDomain() },
            hasMore = dtoResult.hasMore
        )
    }

    /**
     * Gets paginated reviews for a specific place.
     * @param placeId The place ID.
     * @param pageSize Number of reviews per page.
     * @param reset If true, resets pagination for this place.
     * @param orderBy Optional order by field and direction.
     * @param key Unique key for pagination context (default: "place_" + placeId).
     * @return PaginatedResult<Review> containing reviews and hasMore flag.
     */
    suspend fun getReviewsForPlacePaginated(
        placeId: String,
        pageSize: Int,
        reset: Boolean = false,
        orderBy: Pair<String, com.google.firebase.firestore.Query.Direction>? = null,
        key: String = "place_" + placeId
    ): PaginatedResult<Review> {
        val query = getCollection(placeId)
        val dtoResult = fetchPage(
            pageSize = pageSize,
            parentDocId = placeId,
            reset = reset,
            query = query,
            orderBy = orderBy,
            key = key
        )
        return PaginatedResult(
            data = dtoResult.data.map { it.toDomain() },
            hasMore = dtoResult.hasMore
        )
    }
}