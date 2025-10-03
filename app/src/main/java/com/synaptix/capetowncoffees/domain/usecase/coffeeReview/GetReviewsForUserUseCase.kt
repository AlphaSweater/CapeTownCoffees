package com.synaptix.capetowncoffees.domain.usecase.coffeeReview

import com.google.firebase.firestore.Query
import com.synaptix.capetowncoffees.data.common.PaginatedResult
import com.synaptix.capetowncoffees.domain.model.CoffeeReview
import com.synaptix.capetowncoffees.domain.repository.ICoffeeReviewRepository
import javax.inject.Inject

/**
 * Use case for fetching coffee reviews for a user.
 *
 * Example usage:
 * ```kotlin
 * // Get all reviews for a user
 * val result = getUserReviewsUseCase(userId)
 *
 * // Get limited reviews for a user
 * val resultLimited = getUserReviewsUseCase(userId, limit = 10)
 *
 * // Get paginated reviews for a user
 * val paginated = getUserReviewsUseCase.getPaginated(
 *     userId,
 *     pageSize = 10,
 *     reset = true,
 *     orderBy = "date" to Query.Direction.DESCENDING,
 *     key = ""
 * )
 * ```
 */
class GetReviewsForUserUseCase @Inject constructor(
    private val repository: ICoffeeReviewRepository
) {
    /**
     * Fetch all reviews for a user (optionally limited).
     * @param reviewerId The user's ID.
     * @param limit Optional limit for number of reviews.
     * @return Result containing a list of CoffeeReview.
     *
     * Example:
     * ```kotlin
     * val result = getUserReviewsUseCase(userId)
     * val resultLimited = getUserReviewsUseCase(userId, limit = 10)
     * ```
     */
    suspend operator fun invoke(reviewerId: String, limit: Int? = null): Result<List<CoffeeReview>> {
        return repository.getReviewsForUser(reviewerId, limit)
    }

    /**
     * Fetch paginated reviews for a user.
     * @param reviewerId The user's ID.
     * @param pageSize Number of reviews per page.
     * @param reset Whether to reset pagination.
     * @param orderBy Optional ordering (field, direction).
     * @param key Pagination key (usually last document ID).
     * @return PaginatedResult containing CoffeeReview items and pagination info.
     *
     * Example:
     * ```kotlin
     * val paginated = getUserReviewsUseCase.getPaginated(
     *     userId,
     *     pageSize = 10,
     *     reset = true,
     *     orderBy = "date" to Query.Direction.DESCENDING,
     *     key = ""
     * )
     * ```
     */
    suspend fun getPaginated(
        reviewerId: String,
        pageSize: Int,
        reset: Boolean,
        orderBy: Pair<String, Query.Direction>? = null,
        key: String = ""
    ): PaginatedResult<CoffeeReview> {
        return repository.getReviewsForUserPaginated(reviewerId, pageSize, reset, orderBy, key)
    }
}
