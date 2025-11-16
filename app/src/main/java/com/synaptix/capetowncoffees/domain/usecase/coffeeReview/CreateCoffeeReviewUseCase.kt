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
import com.synaptix.capetowncoffees.domain.model.InAppReview
import com.synaptix.capetowncoffees.domain.model.CoffeeReview
import javax.inject.Inject

/**
 * Use case for creating a coffee review for a place.
 *
 * Example usage:
 * ```kotlin
 * // Create a new review for a place
 * val result = createCoffeeReviewUseCase(coffeeReview, placeId)
 * ```
 */
class CreateCoffeeReviewUseCase @Inject constructor(
    private val coffeeReviewRepository: ICoffeeReviewRepository,
    private val coffeePlaceUtilsUseCase: CoffeePlaceUtilsUseCase,
) {
    /**
     * Create a new review for a place.
     * @param coffeeReview The domain review to add. Only InAppReview instances are accepted for creation.
     * @param placeId The place's ID.
     * @return Result containing the new review's ID (or error).
     *
     * Example:
     * ```kotlin
     * val result = createCoffeeReviewUseCase(coffeeReview, placeId)
     * ```
     */
    suspend operator fun invoke(coffeeReview: CoffeeReview, placeId: String): Result<String> {
        // Only in-app reviews can be created via this use case. If callers pass other
        // CoffeeReview subtypes (e.g. GooglePlaceReview) they must map/convert them first.
        if (coffeeReview !is InAppReview) {
            return Result.failure(Exception("CreateCoffeeReviewUseCase only accepts InAppReview; convert other CoffeeReview types before calling"))
        }

        val placeExists = coffeePlaceUtilsUseCase.checkIfPlaceExists(placeId)
        if (!placeExists) {
            return Result.failure(Exception("Failed find place"))
        }

        // Safe cast to InAppReview guaranteed by the instanceof check above
        return coffeeReviewRepository.addReview(coffeeReview, placeId)
    }
}