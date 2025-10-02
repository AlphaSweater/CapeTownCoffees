package com.synaptix.capetowncoffees.domain.repository

import com.synaptix.capetowncoffees.domain.model.Review

interface IReviewRepository {
    suspend fun getReviewsForUser(userId: String, limit: Int?): List<Review>
    suspend fun getReviewsForPlace(placeId: String, limit: Int?): List<Review>
    suspend fun addReview(review: Review): String
    suspend fun deleteReview(reviewId: String)
    suspend fun getReview(reviewId: String): Review?
    // Add more as needed
}