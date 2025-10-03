package com.synaptix.capetowncoffees.domain.repository

import com.synaptix.capetowncoffees.domain.model.Review

interface IReviewRepository {
    suspend fun getReviewsForUser(reviewerId: String, limit: Int?): List<Review>
    suspend fun getReviewsForPlace(placeId: String, limit: Int?): List<Review>
    suspend fun addReview(review: Review, placeId: String): String
    suspend fun deleteReview(reviewId: String, placeId: String)
    suspend fun getReview(reviewId: String, placeId: String): Review?
    // Add more as needed
}