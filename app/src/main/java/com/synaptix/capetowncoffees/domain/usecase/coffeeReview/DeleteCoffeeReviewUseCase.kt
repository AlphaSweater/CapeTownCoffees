//======================================================================================
//Group 2 - Group Members:
//======================================================================================
//* Chad Fairlie ST10269509
//* Dhiren Ruthenavelu ST10256859
//* Kayla Ferreira ST10259527
//* Nathan Teixeira ST10249266
//======================================================================================
//References:
//======================================================================================
//* ChatGPT was used to assist with the development, design, and debugging of this file.
//* AI support was used for learning purposes, improving clarity and resolving issues.
//* It also helped generate useful comments
//======================================================================================

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
