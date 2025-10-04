package com.synaptix.capetowncoffees.domain.usecase.coffeeReview

import com.synaptix.capetowncoffees.domain.model.CoffeeReview
import com.synaptix.capetowncoffees.domain.repository.ICoffeeReviewRepository
import javax.inject.Inject

/**
 * Use case for fetching a single coffee review for a place.
 *
 * Example usage:
 * ```kotlin
 * // Get a review for a place
 * val result = getCoffeeReviewUseCase(reviewId, placeId)
 * ```
 */
class GetCoffeeReviewUseCase @Inject constructor(
    private val repository: ICoffeeReviewRepository
) {
    /**
     * Fetch a single review for a place.
     * @param reviewId The review's ID.
     * @param placeId The place's ID.
     * @return Result containing the CoffeeReview or null if not found.
     *
     * Example:
     * ```kotlin
     * val result = getCoffeeReviewUseCase(reviewId, placeId)
     * ```
     */
    suspend operator fun invoke(reviewId: String, placeId: String): Result<CoffeeReview?> {
        return repository.getReview(reviewId, placeId)
    }
}
