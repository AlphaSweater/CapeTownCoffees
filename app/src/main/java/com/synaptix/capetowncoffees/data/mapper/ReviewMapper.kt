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
//* ChatGPT was used to guide the creation of mapper classes responsible for converting
//between entities, DTOs, and domain models.
//* It also helped ensure consistent naming and mapping logic throughout the project.
//* It also helped generate useful comments
//======================================================================================

package com.synaptix.capetowncoffees.data.mapper

import com.google.android.libraries.places.api.model.Review as GoogleReview
import com.synaptix.capetowncoffees.data.model.AppReviewDTO
import com.synaptix.capetowncoffees.data.model.CoffeeUserDTO
import com.synaptix.capetowncoffees.domain.model.GooglePlaceReview
import com.synaptix.capetowncoffees.domain.model.InAppReview
import com.synaptix.capetowncoffees.util.CoffeeTimeUtils

/**
 * Mapper for the CoffeeReview sealed hierarchy.
 * - Preserves *per-source* default order (in-app and Google independently).
 * - Provides list mapping for Google to capture Google’s incoming order (1..n).
 */
object ReviewMapper {

    // -----------------------------
    // IN-APP (DTO <-> Domain)
    // -----------------------------

    fun fromAppReviewDto(
        dto: AppReviewDTO,
        userDto: CoffeeUserDTO,
        placeIdOverride: String? = null,
        defaultOrder: Int = 1,
    ): InAppReview {
        return InAppReview(
            id = dto.id,
            reviewerId = dto.userId,
            placeId = placeIdOverride ?: dto.placeId,
            authorName = userDto.fullName,
            profilePhotoBase64 = userDto.photoBase64,
            rating = dto.rating,
            text = dto.text,
            publishTime = dto.createdAt,   // epoch seconds expected
            textLanguageCode = dto.textLanguageCode,
            defaultOrder = defaultOrder,
            order = defaultOrder,
            isEdited = false,
            helpfulCount = 0,
        )
    }

    fun fromAppReviewList(
        dtoList: List<AppReviewDTO>,
        userDto: CoffeeUserDTO,
        placeIdOverride: String? = null,
        startIndex: Int = 1,
    ): List<InAppReview> =
        dtoList.mapIndexed { index, dto ->
            fromAppReviewDto(
                dto = dto,
                userDto = userDto,
                placeIdOverride = placeIdOverride,
                defaultOrder = startIndex + index
            )
        }

    fun fromAppReviewList(
        dtoList: List<AppReviewDTO>,
        userDtosMap: Map<String, CoffeeUserDTO>,
        placeIdOverride: String? = null,
        startIndex: Int = 1,
        strict: Boolean = true
    ): List<InAppReview> {
        require(userDtosMap.isNotEmpty()) { "Users map must not be empty." }

        if (strict) {
            val required = dtoList.asSequence().map { it.userId }.toSet()
            val missing = required - userDtosMap.keys
            require(missing.isEmpty()) {
                "User map is missing profiles for userIds=$missing"
            }
        }

        return dtoList.mapIndexed { index, dto ->
            val userDto = userDtosMap[dto.userId] ?: error("Missing CoffeeUserDTO for userId=${dto.userId}")
            fromAppReviewDto(
                dto = dto,
                userDto = userDto,
                placeIdOverride = placeIdOverride,
                defaultOrder = startIndex + index
            )
        }
    }

    fun toAppReviewDTO(review: InAppReview): AppReviewDTO {
        return AppReviewDTO(
            id = review.id,
            userId = review.reviewerId,
            placeId = review.placeId,
            rating = review.rating,
            createdAt = review.publishTime,
            text = review.text,
            textLanguageCode = review.textLanguageCode
        )
    }

    /** For quick in-app submission creation via the model’s companion. */
    fun newInAppSubmission(
        reviewerId: String,
        placeId: String,
        rating: Double,
        text: String,
        defaultOrderStart: Int = 1
    ): InAppReview = InAppReview(
        id = null,
        reviewerId = reviewerId,
        placeId = placeId,
        rating = rating,
        text = text,
        publishTime = CoffeeTimeUtils.nowSeconds(),
        textLanguageCode = null,
        defaultOrder = defaultOrderStart,
        order = defaultOrderStart
    )

    // -----------------------------
    // GOOGLE (SDK -> Domain)
    // -----------------------------

    /** Map a single Google review. Prefer fromGoogleList(...) for preserved ordering. */
    fun fromGoogleReview(
        googleReview: GoogleReview,
        placeId: String,
        defaultOrder: Int = 1
    ): GooglePlaceReview {
        val attr = googleReview.authorAttribution
        val iso = googleReview.publishTime // ISO-8601 string or null

        val epochSeconds = try {
            iso?.let { CoffeeTimeUtils.parseIsoToSeconds(it) }
        } catch (_: Exception) {
            null
        }

        return GooglePlaceReview(
            id = null, // capture an id if the SDK exposes one in your version
            reviewerId = null,
            placeId = placeId,
            authorName = attr.name,
            profilePhotoUrl = attr.photoUri,
            rating = googleReview.rating,
            text = googleReview.text,
            publishTime = epochSeconds,
            textLanguageCode = googleReview.textLanguageCode,
            defaultOrder = defaultOrder,
            order = defaultOrder
        )
    }

    /**
     * Preserve Google’s incoming order: index + 1 (1-based) becomes both defaultOrder and order.
     * This lets you “reset to default” later and get back Google’s original ranking.
     */
    fun fromGoogleList(
        googleReviews: List<GoogleReview>,
        placeId: String
    ): List<GooglePlaceReview> =
        googleReviews.mapIndexed { index, gr ->
            fromGoogleReview(gr, placeId, defaultOrder = index + 1)
        }
}

// ==============================================================================
// Extension Mappers
// ==============================================================================

fun AppReviewDTO.toDomain(userDto: CoffeeUserDTO, defaultOrder: Int = 1): InAppReview =
    ReviewMapper.fromAppReviewDto(this, userDto, defaultOrder = defaultOrder)

/** All belong to one user. */
fun List<AppReviewDTO>.toDomainListForSingleUser(
    user: CoffeeUserDTO,
    placeIdOverride: String? = null,
    startIndex: Int = 1
): List<InAppReview> =
    ReviewMapper.fromAppReviewList(
        dtoList = this,
        userDto = user,
        placeIdOverride = placeIdOverride,
        startIndex = startIndex
    )

/** Many users: pass a non-empty user map. */
fun List<AppReviewDTO>.toDomainListWithUsers(
    users: Map<String, CoffeeUserDTO>,
    placeIdOverride: String? = null,
    startIndex: Int = 1,
    strict: Boolean = true
): List<InAppReview> =
    ReviewMapper.fromAppReviewList(
        dtoList = this,
        userDtosMap = users,
        placeIdOverride = placeIdOverride,
        startIndex = startIndex,
        strict = strict
    )

fun InAppReview.toDto(): AppReviewDTO =
    ReviewMapper.toAppReviewDTO(this)

fun GoogleReview.toDomain(placeId: String, defaultOrder: Int = 1): GooglePlaceReview =
    ReviewMapper.fromGoogleReview(this, placeId, defaultOrder)

fun List<GoogleReview>.toDomainList(placeId: String): List<GooglePlaceReview> =
    ReviewMapper.fromGoogleList(this, placeId)