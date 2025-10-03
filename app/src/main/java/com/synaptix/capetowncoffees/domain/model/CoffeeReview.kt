package com.synaptix.capetowncoffees.domain.model

import com.synaptix.capetowncoffees.data.mapper.ReviewMapper
import com.synaptix.capetowncoffees.data.model.AppReviewDTO
import com.google.android.libraries.places.api.model.Review as GoogleReview

/**
 * Unified domain model for reviews, supporting both in-app and Google sources.
 * Enables aggregation, sorting, and display of reviews from multiple sources.
 */
data class CoffeeReview(
    val source: ReviewSource,
    val id: String? = null,
    val reviewerId: String?,
    val placeId: String?,
    val author: String?,
    val profilePhotoUrl: String? = null,
    val rating: Double?,
    val text: String?,
    val publishTime: Long? = null,
    val textLanguageCode: String? = null
) {
    companion object {
        /**
         * Factory for a new in-app review (for submissions).
         */
        fun newReview(
            reviewerId: String,
            placeId: String,
            rating: Double,
            text: String
        ): CoffeeReview = CoffeeReview(
            source = ReviewSource.IN_APP,
            id = "",
            reviewerId = reviewerId,
            placeId = placeId,
            author = null,
            profilePhotoUrl = null,
            rating = rating,
            text = text,
            publishTime = null,
            textLanguageCode = null
        )
    }
}

/**
 * Converts this Review to a AppReviewDTO if source is IN_APP.
 */
fun CoffeeReview.toDTO(): AppReviewDTO? =
    if (source == ReviewSource.IN_APP) ReviewMapper.toAppReviewDTO(this) else null

/**
 * Converts this AppReviewDTO to a domain Review.
 */
fun AppReviewDTO.toDomain(): CoffeeReview = ReviewMapper.fromAppReviewDto(this, placeId) // placeId can be passed if known

/**
 * Converts this GoogleReview to a domain Review.
 */
fun GoogleReview.toDomain(placeId: String): CoffeeReview = ReviewMapper.fromGoogleReview(this, placeId)

/**
 * Source of a review.
 */
enum class ReviewSource {
    IN_APP, // Review from in-app user
    GOOGLE  // Review from Google Places
}
