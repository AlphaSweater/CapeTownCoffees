package com.synaptix.capetowncoffees.data.repository

import com.synaptix.capetowncoffees.data.common.BaseRepository
import com.synaptix.capetowncoffees.domain.model.Review
import com.synaptix.capetowncoffees.domain.repository.IReviewRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.synaptix.capetowncoffees.data.model.AppReviewDTO
import com.synaptix.capetowncoffees.domain.model.toDTO
import com.synaptix.capetowncoffees.domain.model.toDomain
import kotlinx.coroutines.tasks.await

class ReviewRepository(
    firestore: FirebaseFirestore
) : BaseRepository<AppReviewDTO>(
    firestore = firestore,
    parentCollection = "coffee_places",
    childCollection = "reviews"
), IReviewRepository {

    override fun getType(): Class<AppReviewDTO> = AppReviewDTO::class.java

    override suspend fun getReviewsForUser(reviewerId: String, limit: Int?): List<Review> {
        val dtoResult = getAllByFieldFromCollectionGroup("reviews", "reviewerId", reviewerId, limit)
        return dtoResult.getOrElse { emptyList() }.map { it.toDomain() }
    }

    override suspend fun getReviewsForPlace(placeId: String, limit: Int?): List<Review> {
        val dtoResult = getAll(limit = limit, parentDocId = placeId)
        return dtoResult.getOrElse { emptyList() }.map { it.toDomain() }
    }

    override suspend fun addReview(review: Review, placeId: String): String {
        val dto = review.toDTO()
        requireNotNull(dto) { "Only IN_APP reviews can be added." }
        return create(dto, parentDocId = placeId).getOrThrow()
    }

    override suspend fun deleteReview(reviewId: String, placeId: String) {
        delete(reviewId, parentDocId = placeId).getOrThrow()
    }

    override suspend fun getReview(reviewId: String, placeId: String): Review? {
        val dtoResult = getById(reviewId, parentDocId = placeId)
        return dtoResult.getOrNull()?.toDomain()
    }
}