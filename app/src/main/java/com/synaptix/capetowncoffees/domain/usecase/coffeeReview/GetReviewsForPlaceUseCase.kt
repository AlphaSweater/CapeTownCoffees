package com.synaptix.capetowncoffees.domain.usecase.coffeeReview

import com.google.firebase.firestore.Query
import com.synaptix.capetowncoffees.data.common.PaginatedResult
import com.synaptix.capetowncoffees.domain.model.CoffeeReview
import com.synaptix.capetowncoffees.domain.repository.ICoffeeReviewRepository
import javax.inject.Inject

/**
 * Use case for fetching coffee reviews for a place.
 *
 * Example usage:
 * ```kotlin
 * // Get all reviews for a place
 * val result = getPlaceReviewsUseCase(placeId)
 *
 * // Get limited reviews for a place
 * val resultLimited = getPlaceReviewsUseCase(placeId, limit = 10)
 *
 * // Get paginated reviews for a place
 * val paginated = getPlaceReviewsUseCase.getPaginated(
 *     placeId,
 *     pageSize = 10,
 *     reset = true,
 *     orderBy = "date" to Query.Direction.DESCENDING,
 *     key = ""
 * )
 * ```
 */
class GetPlaceReviewsUseCase @Inject constructor(
    private val repository: ICoffeeReviewRepository
) {
    /**
     * Fetch all reviews for a place (optionally limited).
     * @param placeId The place's ID.
     * @param limit Optional limit for number of reviews.
     * @return Result containing a list of CoffeeReview.
     *
     * Example:
     * ```kotlin
     * val result = getPlaceReviewsUseCase(placeId)
     * val resultLimited = getPlaceReviewsUseCase(placeId, limit = 10)
     * ```
     */
    suspend operator fun invoke(placeId: String, limit: Int? = null): Result<List<CoffeeReview>> {
        return repository.getReviewsForPlace(placeId, limit)
    }

    /**
     * Fetch paginated reviews for a place.
     * @param placeId The place's ID.
     * @param pageSize Number of reviews per page.
     * @param reset Whether to reset pagination.
     * @param orderBy Optional ordering (field, direction).
     * @param key Pagination key (usually last document ID).
     * @return PaginatedResult containing CoffeeReview items and pagination info.
     *
     * Example:
     * ```kotlin
     * val paginated = getPlaceReviewsUseCase.getPaginated(
     *     placeId,
     *     pageSize = 10,
     *     reset = true,
     *     orderBy = "date" to Query.Direction.DESCENDING,
     *     key = ""
     * )
     * ```
     */
    suspend fun getPaginated(
        placeId: String,
        pageSize: Int,
        reset: Boolean,
        orderBy: Pair<String, Query.Direction>? = null,
        key: String = ""
    ): PaginatedResult<CoffeeReview> {
        return repository.getReviewsForPlacePaginated(placeId, pageSize, reset, orderBy, key)
    }
}
