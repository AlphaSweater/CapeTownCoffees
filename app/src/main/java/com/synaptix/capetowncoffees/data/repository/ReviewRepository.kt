package com.synaptix.capetowncoffees.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.synaptix.capetowncoffees.data.common.BaseRepository
import com.synaptix.capetowncoffees.data.common.PaginatedResult
import com.synaptix.capetowncoffees.data.model.AppReviewDTO
import com.synaptix.capetowncoffees.domain.model.Review
import com.synaptix.capetowncoffees.domain.model.toDTO
import com.synaptix.capetowncoffees.domain.model.toDomain
import com.synaptix.capetowncoffees.domain.repository.IReviewRepository

/**
 * Firestore-backed implementation of IReviewRepository for reviews.
 * Supports CRUD and paginated access for place and user reviews.
 */
class ReviewRepository(
    firestore: FirebaseFirestore
) : BaseRepository<AppReviewDTO>(
    firestore = firestore,
    parentCollection = "coffee_places",
    childCollection = "reviews"
), IReviewRepository {

    override fun getType(): Class<AppReviewDTO> = AppReviewDTO::class.java

    // ----------------------------
    // CRUD
    // ----------------------------
    override suspend fun getReviewsForUser(reviewerId: String, limit: Int?): Result<List<Review>> {
        val dtoResult = getAllByFieldFromCollectionGroup("reviews", "reviewerId", reviewerId, limit)
        return if (dtoResult.isSuccess) {
            Result.success(dtoResult.getOrNull()?.map { it.toDomain() } ?: emptyList())
        } else {
            Result.failure(dtoResult.exceptionOrNull() ?: Exception("Error fetching user reviews"))
        }
    }

    override suspend fun getReviewsForPlace(placeId: String, limit: Int?): Result<List<Review>> {
        val dtoResult = getAll(limit = limit, parentDocId = placeId)
        return if (dtoResult.isSuccess) {
            Result.success(dtoResult.getOrNull()?.map { it.toDomain() } ?: emptyList())
        } else {
            Result.failure(dtoResult.exceptionOrNull() ?: Exception("Error fetching place reviews"))
        }
    }

    override suspend fun addReview(review: Review, placeId: String): Result<String> {
        val dto = review.toDTO()
        return try {
            requireNotNull(dto) { "Only IN_APP reviews can be added." }
            create(dto, parentDocId = placeId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteReview(reviewId: String, placeId: String): Result<Unit> {
        return delete(reviewId, parentDocId = placeId)
    }

    override suspend fun getReview(reviewId: String, placeId: String): Result<Review?> {
        val dtoResult = getById(reviewId, parentDocId = placeId)
        return if (dtoResult.isSuccess) {
            Result.success(dtoResult.getOrNull()?.toDomain())
        } else {
            Result.failure(dtoResult.exceptionOrNull() ?: Exception("Error fetching review"))
        }
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

    override suspend fun getReviewsForPlacePaginated(
        placeId: String,
        pageSize: Int,
        reset: Boolean,
        orderBy: Pair<String, Query.Direction>?,
        key: String
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