package com.synaptix.capetowncoffees.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import com.google.android.libraries.places.api.model.Review as GReview
import com.synaptix.capetowncoffees.data.model.AppReviewDTO
import com.synaptix.capetowncoffees.data.mapper.ReviewMapper
import kotlin.io.encoding.Base64

/**
 * Sealed parent: shared fields across all review sources.
 *
 * Ordering model:
 *  - defaultOrder: position assigned at load time within its source-group (IN_APP or GOOGLE).
 *  - order:        current position within its source-group (can change via UI/filters).
 * Use the ReviewOrdering.kt helpers to display or reset ordering across a mixed list.
 */
sealed class CoffeeReview : Parcelable {
    abstract val id: String?
    abstract val reviewerId: String?
    abstract val placeId: String?
    abstract val author: String?
    abstract val rating: Double?
    abstract val text: String?
    abstract val publishTime: Long?          // epoch seconds (null-safe)
    abstract val textLanguageCode: String?

    abstract val defaultOrder: Int           // 1-based
    abstract val order: Int                  // 1-based

    /** Convenience for time sorts. */
    val timeSortKey: Long get() = publishTime ?: Long.MIN_VALUE

    /** Copy with an updated order (within the same source group). */
    abstract fun withOrder(order: Int): CoffeeReview

    /** Copy with order reset to defaultOrder. */
    abstract fun resetToDefaultOrder(): CoffeeReview

    companion object {
        /** App submission factory (id assigned later by persistence). */
        fun newInAppSubmission(
            reviewerId: String,
            placeId: String,
            rating: Double,
            text: String,
            defaultOrderStart: Int = 1
        ) = ReviewMapper.newInAppSubmission(
            reviewerId, placeId, rating, text, defaultOrderStart
        )
    }
}

@Parcelize
data class InAppReview(
    override val id: String? = null,
    override val reviewerId: String?,
    override val placeId: String?,
    override val author: String? = null,
    val profilePhotoBase64: String? = null,
    override val rating: Double?,
    override val text: String?,
    override val publishTime: Long? = null,
    override val textLanguageCode: String? = null,

    // ordering within IN_APP group
    override val defaultOrder: Int,
    override val order: Int = defaultOrder,

    // in-app specific metadata (extend freely)
    val isEdited: Boolean = false,
    val helpfulCount: Int = 0,
) : CoffeeReview() {
    override fun withOrder(order: Int): InAppReview = copy(order = order)
    override fun resetToDefaultOrder(): InAppReview = copy(order = defaultOrder)
}

@Parcelize
data class GooglePlaceReview(
    override val id: String? = null,
    override val reviewerId: String? = null,
    override val placeId: String?,
    override val author: String?,
    val profilePhotoUrl: String? = null,
    override val rating: Double?,
    override val text: String?,
    override val publishTime: Long? = null,
    override val textLanguageCode: String? = null,

    // ordering within GOOGLE group
    override val defaultOrder: Int,
    override val order: Int = defaultOrder,

    // google-only metadata optionally extendable
) : CoffeeReview() {
    override fun withOrder(order: Int): GooglePlaceReview = copy(order = order)
    override fun resetToDefaultOrder(): GooglePlaceReview = copy(order = defaultOrder)
}

/**
 * Returns the profile photo for this review.
 * For InAppReview, returns the Base64 image string.
 * For GooglePlaceReview, returns profilePhotoUrl.
 */
fun CoffeeReview.getProfilePhoto(): String? = when (this) {
    is InAppReview -> profilePhotoBase64
    is GooglePlaceReview -> profilePhotoUrl
}

// Tiny convenience flags
val CoffeeReview.isInApp get() = this is InAppReview
val CoffeeReview.isGoogle get() = this is GooglePlaceReview