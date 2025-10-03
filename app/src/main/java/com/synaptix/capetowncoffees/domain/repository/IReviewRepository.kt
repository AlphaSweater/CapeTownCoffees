package com.synaptix.capetowncoffees.domain.repository

import com.synaptix.capetowncoffees.domain.model.Review
import com.synaptix.capetowncoffees.data.common.PaginatedResult
import com.google.firebase.firestore.Query

/**
 * IReviewRepository defines the contract for accessing and managing reviews in the domain layer.
 * It supports CRUD operations and paginated access for both place and user reviews.
 */
interface IReviewRepository {
    /**
     * Gets all reviews written by a user across all places.
     * @param reviewerId The user ID.
     * @param limit Optional max number of reviews to return.
     * @return List of domain Review objects.
     */
    suspend fun getReviewsForUser(reviewerId: String, limit: Int?): List<Review>

    /**
     * Gets all reviews for a specific place.
     * @param placeId The place ID.
     * @param limit Optional max number of reviews to return.
     * @return List of domain Review objects.
     */
    suspend fun getReviewsForPlace(placeId: String, limit: Int?): List<Review>

    /**
     * Adds a new review for a place. Only IN_APP reviews are allowed.
     * @param review The domain Review object.
     * @param placeId The place ID.
     * @return The Firestore document ID of the new review.
     * @throws IllegalArgumentException if review cannot be converted to DTO.
     */
    suspend fun addReview(review: Review, placeId: String): String

    /**
     * Deletes a review for a place by its document ID.
     * @param reviewId The review document ID.
     * @param placeId The place ID.
     */
    suspend fun deleteReview(reviewId: String, placeId: String)

    /**
     * Gets a single review for a place by its document ID.
     * @param reviewId The review document ID.
     * @param placeId The place ID.
     * @return The domain Review object, or null if not found.
     */
    suspend fun getReview(reviewId: String, placeId: String): Review?

    /**
     * Gets paginated reviews for a user from all coffee places.
     * @param reviewerId The user ID.
     * @param pageSize Number of reviews per page.
     * @param reset If true, resets pagination for this user.
     * @param orderBy Optional order by field and direction.
     * @param key Unique key for pagination context (default: "user_" + reviewerId).
     * @return PaginatedResult<Review> containing reviews and hasMore flag.
     */
    suspend fun getReviewsForUserPaginated(
        reviewerId: String,
        pageSize: Int,
        reset: Boolean = false,
        orderBy: Pair<String, Query.Direction>? = null,
        key: String = "user_$reviewerId"
    ): PaginatedResult<Review>

    /**
     * Gets paginated reviews for a specific coffee place.
     * @param placeId The place ID.
     * @param pageSize Number of reviews per page.
     * @param reset If true, resets pagination for this place.
     * @param orderBy Optional order by field and direction.
     * @param key Unique key for pagination context (default: "place_" + placeId).
     * @return PaginatedResult<Review> containing reviews and hasMore flag.
     */
    suspend fun getReviewsForPlacePaginated(
        placeId: String,
        pageSize: Int,
        reset: Boolean = false,
        orderBy: Pair<String, Query.Direction>? = null,
        key: String = "place_$placeId"
    ): PaginatedResult<Review>
}