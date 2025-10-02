package com.synaptix.capetowncoffees.domain.model

/**
 * Domain model representing a unified review for a place, supporting both in-app and Google reviews.
 *
 * This Review class abstracts away the source of the review, allowing the app to treat all reviews uniformly in UI and business logic.
 * - For in-app reviews, reviewerId is populated with the user's ID; for Google reviews, reviewerId is null.
 * - The author, profilePhotoUrl, rating, text, publishTime, and language fields are populated for both review types.
 * - The source field indicates whether the review originated from the app (IN_APP) or Google (GOOGLE).
 * - The relativePublishTimeText provides a user-friendly time string for display purposes.
 * - If the review text was translated, originalText and originalTextLanguageCode hold the original content and language.
 *
 * Conversion from DTOs (e.g., AppReviewDTO for in-app, GoogleReviewDTO for Google) to this domain model is handled by a mapper class,
 * which ensures all necessary fields are mapped and unified for downstream usage.
 *
 * This design enables seamless aggregation, sorting, and display of reviews from multiple sources, while hiding source-specific details from the user.
 */
data class Review(
    val source: ReviewSource, // IN_APP or GOOGLE
    val id: String? = null, // Unique ID for the review; null for Google reviews
    val reviewerId: String?, // In-app user ID; null for Google reviews
    val author: String?, // Display name
    val profilePhotoUrl: String? = null, // Profile photo for both in-app and Google reviews
    val rating: Double?,
    val text: String?,
    val publishTime: Long? = null, // Publish time UTC seconds for both
    val relativePublishTimeText: String? = null, // Relative time formatted text
    val textLanguageCode: String? = null, // Language code for review text
    val originalText: String? = null, // Original text (if translated)
    val originalTextLanguageCode: String? = null // Language code for original text
)

/**
 * Enum representing the source of a review: either in-app or Google.
 */
enum class ReviewSource {
    IN_APP, GOOGLE
}
