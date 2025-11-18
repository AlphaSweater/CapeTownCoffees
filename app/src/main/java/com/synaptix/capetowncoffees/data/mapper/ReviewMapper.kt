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

// ─────────── Mapper — Reviews (DTO/SDK ⇄ Domain) ───────────
// We normalize review sources (in-app vs Google) into consistent domain models.
// Ordering notes: we keep the default order per source so lists can be merged predictably.
public object ReviewMapper {

    // ─────────── In-app reviews (DTO → Domain) ───────────
    // Single DTO + user profile into an InAppReview. We allow placeId override for subcollection writes.
    public fun fromAppReviewDto(
        dto: AppReviewDTO,
        userDto: CoffeeUserDTO,
        placeIdOverride: String? = null,
        defaultOrder: Int = 1,
        userReactionType: String? = null
    ): InAppReview {
        return InAppReview(
            id = dto.id,
            reviewerId = dto.userId,
            placeId = placeIdOverride ?: dto.placeId,
            authorName = userDto.fullName,
            profilePhotoBase64 = userDto.photoBase64,
            rating = dto.rating,
            text = dto.text,
            publishTime = dto.createdAt,
            textLanguageCode = dto.textLanguageCode,
            defaultOrder = defaultOrder,
            order = defaultOrder,
            isEdited = false,
            helpfulCount = 0,
            userReactionType = userReactionType,
            likeCount = (dto.likeCount ?: 0L).toInt(),
            dislikeCount = (dto.dislikeCount ?: 0L).toInt()
        )
    }

    // Map a list for a single known user; we assign 1-based increasing order.
    public fun fromAppReviewList(
        dtoList: List<AppReviewDTO>,
        userDto: CoffeeUserDTO,
        placeIdOverride: String? = null,
        startIndex: Int = 1,
        reviewIdToReaction: Map<String, String?>? = null
    ): List<InAppReview> = dtoList.mapIndexed { index, dto ->
        fromAppReviewDto(
            dto = dto,
            userDto = userDto,
            placeIdOverride = placeIdOverride,
            defaultOrder = startIndex + index,
            userReactionType = reviewIdToReaction?.get(dto.id)
        )
    }

    // Map a list for many users; strict=true enforces that every userId has a profile.
    public fun fromAppReviewList(
        dtoList: List<AppReviewDTO>,
        userDtosMap: Map<String, CoffeeUserDTO>,
        placeIdOverride: String? = null,
        startIndex: Int = 1,
        strict: Boolean = true,
        reviewIdToReaction: Map<String, String?>? = null
    ): List<InAppReview> {
        require(userDtosMap.isNotEmpty()) { "Users map must not be empty." }

        if (strict) {
            val required = dtoList.asSequence().map { it.userId }.toSet()
            val missing = required - userDtosMap.keys
            require(missing.isEmpty()) { "User map is missing profiles for userIds=$missing" }
        }

        return dtoList.mapIndexed { index, dto ->
            val userDto = userDtosMap[dto.userId] ?: error("Missing CoffeeUserDTO for userId=${dto.userId}")
            fromAppReviewDto(
                dto = dto,
                userDto = userDto,
                placeIdOverride = placeIdOverride,
                defaultOrder = startIndex + index,
                userReactionType = reviewIdToReaction?.get(dto.id)
            )
        }
    }

    // Domain → DTO for persistence.
    public fun toAppReviewDTO(review: InAppReview): AppReviewDTO {
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

    // Helper to create a new in-app submission with current time and initial order.
    public fun newInAppSubmission(
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

    // ─────────── Google reviews (SDK → Domain) ───────────
    // Map a single Google review. Parsing publish time is best-effort.
    public fun fromGoogleReview(
        googleReview: GoogleReview,
        placeId: String,
        defaultOrder: Int = 1
    ): GooglePlaceReview {
        val attr = googleReview.authorAttribution
        val iso = googleReview.publishTime

        val epochSeconds = try {
            iso?.let { CoffeeTimeUtils.parseIsoToSeconds(it) }
        } catch (_: Exception) {
            null
        }

        return GooglePlaceReview(
            id = null,
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

    // Preserve incoming Google order (1-based). Useful for resetting to source ranking.
    public fun fromGoogleList(
        googleReviews: List<GoogleReview>,
        placeId: String
    ): List<GooglePlaceReview> = googleReviews.mapIndexed { index, gr ->
        fromGoogleReview(gr, placeId, defaultOrder = index + 1)
    }
}

// ─────────── Extensions — Convenience mappers ───────────
// These keep call sites clean while delegating to ReviewMapper.

public fun AppReviewDTO.toDomain(userDto: CoffeeUserDTO, defaultOrder: Int = 1): InAppReview =
    ReviewMapper.fromAppReviewDto(this, userDto, defaultOrder = defaultOrder)

public fun List<AppReviewDTO>.toDomainListForSingleUser(
    user: CoffeeUserDTO,
    placeIdOverride: String? = null,
    startIndex: Int = 1,
    reviewIdToReaction: Map<String, String?>? = null
): List<InAppReview> =
    ReviewMapper.fromAppReviewList(
        dtoList = this,
        userDto = user,
        placeIdOverride = placeIdOverride,
        startIndex = startIndex,
        reviewIdToReaction = reviewIdToReaction
    )

public fun List<AppReviewDTO>.toDomainListWithUsers(
    users: Map<String, CoffeeUserDTO>,
    placeIdOverride: String? = null,
    startIndex: Int = 1,
    strict: Boolean = true,
    reviewIdToReaction: Map<String, String?>? = null
): List<InAppReview> =
    ReviewMapper.fromAppReviewList(
        dtoList = this,
        userDtosMap = users,
        placeIdOverride = placeIdOverride,
        startIndex = startIndex,
        strict = strict,
        reviewIdToReaction = reviewIdToReaction
    )

public fun InAppReview.toDto(): AppReviewDTO =
    ReviewMapper.toAppReviewDTO(this)

public fun GoogleReview.toDomain(placeId: String, defaultOrder: Int = 1): GooglePlaceReview =
    ReviewMapper.fromGoogleReview(this, placeId, defaultOrder)

public fun List<GoogleReview>.toDomainList(placeId: String): List<GooglePlaceReview> =
    ReviewMapper.fromGoogleList(this, placeId)