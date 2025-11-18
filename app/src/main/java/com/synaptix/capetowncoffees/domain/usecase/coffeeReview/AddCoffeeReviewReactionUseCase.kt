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
import com.synaptix.capetowncoffees.domain.usecase.coffeePlace.CoffeePlaceUtilsUseCase
import com.synaptix.capetowncoffees.domain.usecase.coffeeUser.GetUserProfileUseCase
import javax.inject.Inject

/**
 * Use case for adding/toggling a reaction (like or dislike) on an in-app review.
 *
 * Example:
 * ```kotlin
 * addCoffeeReviewReactionUseCase(
 *      placeId = "abc123",
 *      reviewId = "rev456",
 *      userId = "user789",
 *      isLike = true   // like
 * )
 * ```
 */
class AddCoffeeReviewReactionUseCase @Inject constructor(
    private val coffeeReviewRepository: ICoffeeReviewRepository,
    private val coffeePlaceUtilsUseCase: CoffeePlaceUtilsUseCase,
    private val getUserProfileUseCase: GetUserProfileUseCase
) {
    /**
     * Add, remove, or switch a reaction on a review.
     *
     * Logic is fully handled inside the repository:
     *  - If user previously had no reaction → adds the new one.
     *  - If user presses the same reaction → removes it.
     *  - If user had the opposite one → switches it.
     *
     * @param placeId The place the review belongs to.
     * @param reviewId The ID of the review being reacted to.
     * @param userId The user performing the reaction.
     * @param isLike True = Like, False = Dislike.
     * @return Result<Unit> representing success or an error.
     */
    suspend operator fun invoke(
        placeId: String,
        reviewId: String,
        isLike: Boolean,
        userId: String? = null
    ): Result<Unit> {

        // Ensure the place exists before writing into nested subcollections
        val placeExists = coffeePlaceUtilsUseCase.checkIfPlaceExists(placeId)
        if (!placeExists) {
            return Result.failure(Exception("Place not found"))
        }

        // Resolve user id: use provided id or fetch current user via GetUserProfileUseCase
        val resolvedUserId = userId ?: run {
            val userResult = getUserProfileUseCase()
            val user = userResult.getOrElse { return Result.failure(it) }
            user.id
        }

        return coffeeReviewRepository.reactToReview(
            placeId = placeId,
            reviewId = reviewId,
            userId = resolvedUserId,
            isLike = isLike
        )
    }
}
