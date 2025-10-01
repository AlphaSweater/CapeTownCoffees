package com.synaptix.capetowncoffees.data.mapper

import com.synaptix.capetowncoffees.domain.model.Review as MyReview
import com.synaptix.capetowncoffees.data.model.AppReviewDTO
import com.google.android.libraries.places.api.model.Review as GoogleReview
import com.synaptix.capetowncoffees.domain.model.ReviewSource
import com.synaptix.capetowncoffees.util.TimeUtils

/**
 * Mapper for converting between review DTOs and the unified domain Review model.
 * Handles mapping from both Google reviews and in-app reviews, and can map back from domain Review to AppReviewDTO for IN_APP reviews.
 */
object ReviewMapper {
    /**
     * Maps a GoogleReview to the unified Review model.
     * Safely parses publishTime (ISO8601 string) to Long (seconds since epoch).
     */
    fun fromGoogleReview(googleReview: GoogleReview): MyReview {
        val authorAttribution = googleReview.authorAttribution
        val googleTime = googleReview.publishTime // ISO8601 string
        val utcSeconds = try {
            TimeUtils.parseIsoToSeconds(googleTime)
        } catch (e: Exception) {
            null
        }
        return MyReview(
            source = ReviewSource.GOOGLE,
            id = null, // Google reviews don't have app review IDs
            reviewerId = null, // Google reviews don't have app user IDs
            author = authorAttribution.name,
            profilePhotoUrl = authorAttribution.photoUri?.toString(),
            rating = googleReview.rating,
            text = googleReview.text,
            publishTime = utcSeconds,
            relativePublishTimeText = if (utcSeconds != null) TimeUtils.formatRelativeTime(utcSeconds) else null,
            textLanguageCode = googleReview.textLanguageCode,
            originalText = googleReview.originalText,
            originalTextLanguageCode = googleReview.originalTextLanguageCode
        )
    }

    /**
     * Maps an AppReviewDTO to the unified Review model.
     */
    fun fromAppReviewDto(dto: AppReviewDTO): MyReview {
        return MyReview(
            source = ReviewSource.IN_APP,
            id = dto.id,
            reviewerId = dto.userId,
            author = null, // Can be populated by fetching user details if needed
            profilePhotoUrl = null, // Can be populated by fetching user details if needed
            rating = dto.rating,
            text = dto.text,
            publishTime = dto.createdAt,
            relativePublishTimeText = if (dto.createdAt != null) TimeUtils.formatRelativeTime(dto.createdAt) else null,
            textLanguageCode = dto.textLanguageCode,
            originalText = dto.originalText,
            originalTextLanguageCode = dto.originalTextLanguageCode
        )
    }

    /**
     * Maps a domain Review (with source IN_APP) back to AppReviewDTO.
     * Throws IllegalArgumentException if the review source is not IN_APP.
     */
    fun toAppReviewDTO(review: MyReview, placeId: String): AppReviewDTO {
        if (review.source != ReviewSource.IN_APP) {
            throw IllegalArgumentException("Can only map IN_APP reviews to AppReviewDTO")
        }
        return AppReviewDTO(
            id = review.id,
            userId = review.reviewerId,
            placeId = placeId,
            rating = review.rating,
            createdAt = review.publishTime,
            text = review.text,
            textLanguageCode = review.textLanguageCode,
            originalText = review.originalText,
            originalTextLanguageCode = review.originalTextLanguageCode
        )
    }
}