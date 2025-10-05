package com.synaptix.capetowncoffees.domain.usecase.coffeeReview

import com.synaptix.capetowncoffees.domain.repository.ICoffeeReviewRepository
import javax.inject.Inject

/**
 * Use case for deleting a coffee review from a place.
 *
 * Example usage:
 * ```kotlin
 * // Delete a review from a place
 * val result = deleteCoffeeReviewUseCase(reviewId, placeId)
 * ```
 */
class DeleteCoffeeReviewUseCase @Inject constructor(
    private val coffeeReviewRepository: ICoffeeReviewRepository
) {
    /**
     * Delete a review from a place.
     * @param reviewId The review's ID.
     * @param placeId The place's ID.
     * @return Result indicating success or error.
     *
     * Example:
     * ```kotlin
     * val result = deleteCoffeeReviewUseCase(reviewId, placeId)
     * ```
     */
    suspend operator fun invoke(reviewId: String, placeId: String): Result<Unit> {
        return coffeeReviewRepository.deleteReview(reviewId, placeId)
    }
}
