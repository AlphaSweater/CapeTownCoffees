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
