package com.synaptix.capetowncoffees.domain.usecase.coffeeReview

import com.synaptix.capetowncoffees.domain.model.CoffeeReview
import com.synaptix.capetowncoffees.domain.repository.ICoffeeReviewRepository
import com.synaptix.capetowncoffees.domain.usecase.coffeePlace.CoffeePlaceUtilsUseCase
import com.synaptix.capetowncoffees.domain.usecase.coffeePlace.CreateCoffeePlaceUseCase
import com.synaptix.capetowncoffees.data.model.CoffeePlaceDTO
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
    private val repository: ICoffeeReviewRepository,
    private val coffeePlaceUtilsUseCase: CoffeePlaceUtilsUseCase,
    private val createCoffeePlaceUseCase: CreateCoffeePlaceUseCase
) {
    /**
     * Create a new review for a place.
     * @param coffeeReview The review to add.
     * @param placeId The place's ID.
     * @return Result containing the new review's ID (or error).
     *
     * Example:
     * ```kotlin
     * val result = createCoffeeReviewUseCase(coffeeReview, placeId)
     * ```
     */
    suspend operator fun invoke(coffeeReview: CoffeeReview, placeId: String): Result<String> {
        val placeExists = coffeePlaceUtilsUseCase.checkIfPlaceExists(placeId)
        if (!placeExists) {
            val newPlace = CoffeePlaceDTO.createNew(placeId)
            val placeResult = createCoffeePlaceUseCase(newPlace, placeId)
            if (placeResult.isFailure) {
                return Result.failure(placeResult.exceptionOrNull() ?: Exception("Failed to create place"))
            }
        }
        return repository.addReview(coffeeReview, placeId)
    }
}