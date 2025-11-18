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

package com.synaptix.capetowncoffees.domain.model

import android.os.Parcelable
import android.util.Base64
import kotlinx.parcelize.Parcelize
import com.synaptix.capetowncoffees.data.mapper.ReviewMapper

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
    abstract val authorName: String?
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
    override val authorName: String? = null,
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
    // per-current-user reaction (nullable). null == not reacted; "like"/"dislike" etc. when set.
    val userReactionType: String? = null,
) : CoffeeReview() {
    // derived boolean for convenience (not stored separately)
    val isReacted: Boolean get() = userReactionType != null

    override fun withOrder(order: Int): InAppReview = copy(order = order)
    override fun resetToDefaultOrder(): InAppReview = copy(order = defaultOrder)
}

@Parcelize
data class GooglePlaceReview(
    override val id: String? = null,
    override val reviewerId: String? = null,
    override val placeId: String?,
    override val authorName: String?,
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
fun CoffeeReview.avatarModelOrNull(): Any? = when (this) {
    is InAppReview       -> profilePhotoBase64?.decodeBase64OrNull()
    is GooglePlaceReview -> profilePhotoUrl
}

/** Per-review photos (empty for now). */
fun CoffeeReview.photoUrlsOrEmpty(): List<String> = emptyList()
//fun CoffeeReview.photoUrlsOrEmpty(): List<String> = when (this) {
//    is InAppReview       -> thisPhotoUrls ?: emptyList()
//    is GooglePlaceReview -> thisPhotoUrls ?: emptyList()
//}

/** Stable Long for RecyclerView even if id is null. */
fun CoffeeReview.stableId(): Long {
    val key = when (this) {
        is InAppReview ->
            "INAPP:${id ?: ""}:${reviewerId ?: ""}:${placeId ?: ""}:${publishTime ?: 0}:${rating ?: -1.0}"
        is GooglePlaceReview ->
            "GOOG:${id ?: ""}:${reviewerId ?: ""}:${placeId ?: ""}:${publishTime ?: 0}:${rating ?: -1.0}"
    }
    return key.hashCode().toLong()
}

/** Non-empty identifier for click payloads when id is null. */
fun CoffeeReview.safeReviewKey(): String =
    id ?: "${if (this is InAppReview) "inapp" else "google"}:${reviewerId ?: "anon"}:${publishTime ?: 0}"

private fun String.decodeBase64OrNull(): ByteArray? = try {
    Base64.decode(this, Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
} catch (_: IllegalArgumentException) {
    null
}

// Tiny convenience flags
val CoffeeReview.isInApp get() = this is InAppReview
val CoffeeReview.isGoogle get() = this is GooglePlaceReview