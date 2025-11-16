package com.synaptix.capetowncoffees.domain.usecase.coffeeUser

import com.synaptix.capetowncoffees.domain.repository.ICoffeeUserRepository
import javax.inject.Inject

/**
 * Use case to increment the current user's review count by 1.
 *
 * This is used by the review submission flow so that gamification can
 * rely on the value stored on the user document instead of querying
 * all review documents.
 */
class IncrementUserReviewCountUseCase @Inject constructor(
    private val coffeeUserRepository: ICoffeeUserRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        val current = coffeeUserRepository.getCurrentUserProfile().getOrElse {
            return Result.failure(it)
        } ?: return Result.failure(IllegalStateException("No current user profile found"))

        if (current.id.isBlank()) {
            return Result.failure(IllegalStateException("User ID is missing"))
        }

        val updated = current.copy(reviewCount = current.reviewCount + 1)
        return coffeeUserRepository.updateUserProfile(current.id, updated)
    }
}
