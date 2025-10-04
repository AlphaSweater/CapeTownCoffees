package com.synaptix.capetowncoffees.domain.usecase.coffeeUser

import com.synaptix.capetowncoffees.domain.model.CoffeeUser
import com.synaptix.capetowncoffees.domain.repository.ICoffeeUserRepository
import javax.inject.Inject

/**
 * Use case for retrieving a user profile.
 */
class GetUserProfileUseCase @Inject constructor(
    private val coffeeUserRepository: ICoffeeUserRepository
) {
    /**
     * Retrieves a user profile.
     *
     * @param userId The ID of the user to fetch. If null, fetches the current user.
     */
    suspend operator fun invoke(userId: String? = null): Result<CoffeeUser> {
        val id = userId ?: coffeeUserRepository.getCurrentUserId()
        ?: return Result.failure(IllegalStateException("No authenticated user"))

        val user = coffeeUserRepository.getUserProfile(id).getOrElse {
            return Result.failure(it)
        }

        return if (user == null) {
            Result.failure(NoSuchElementException("User not found with id: $id"))
        } else {
            Result.success(user)
        }
    }
}
