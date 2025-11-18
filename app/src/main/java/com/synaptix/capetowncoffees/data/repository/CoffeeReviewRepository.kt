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
//* ChatGPT was used to clarify repository patterns, data source integration, and best
//* practices for separating data access logic from UI components.
//* It also provided suggestions to improve maintainability and consistency.
//* It also helped generate useful comments
//======================================================================================

package com.synaptix.capetowncoffees.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldValue
import com.synaptix.capetowncoffees.data.common.BaseRepository
import com.synaptix.capetowncoffees.data.common.PaginatedResult
import com.synaptix.capetowncoffees.data.mapper.toDTO
import com.synaptix.capetowncoffees.data.mapper.toDomain
import com.synaptix.capetowncoffees.data.mapper.toDomainListForSingleUser
import com.synaptix.capetowncoffees.data.mapper.toDomainListWithUsers
import com.synaptix.capetowncoffees.data.mapper.toDto
import com.synaptix.capetowncoffees.data.model.AppReviewDTO
import com.synaptix.capetowncoffees.data.model.CoffeeUserDTO
import com.synaptix.capetowncoffees.domain.model.CoffeeReview
import com.synaptix.capetowncoffees.domain.model.InAppReview
import com.synaptix.capetowncoffees.domain.repository.ICoffeeReviewRepository
import com.synaptix.capetowncoffees.domain.repository.IPlacesApiRepository
import com.synaptix.capetowncoffees.domain.usecase.coffeeUser.GetUserProfileUseCase
import com.synaptix.capetowncoffees.util.OrderMode
import com.synaptix.capetowncoffees.util.myOrder
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoffeeReviewRepository @Inject constructor(
    firestore: FirebaseFirestore,
    private val placesApiRepository: IPlacesApiRepository,
    private val getUserProfileUseCase: GetUserProfileUseCase
) : BaseRepository<AppReviewDTO>(
    firestore = firestore,
    parentCollection = "coffee_places",
    childCollection = "reviews"
), ICoffeeReviewRepository {

    override fun getType(): Class<AppReviewDTO> = AppReviewDTO::class.java

    override suspend fun getReviewsForUser(
        reviewerId: String,
        limit: Int?
    ): Result<List<CoffeeReview>> = runCatching {
        // Fallback strategy: scan each coffee place's reviews subcollection and
        // collect reviews for this userId. This avoids relying solely on
        // collectionGroup behavior and works without extra composite indexes.

        // 1) Load all coffee place ids
        val placesSnapshot = firestore.collection("coffee_places").get().await()
        val placeIds = placesSnapshot.documents.mapNotNull { it.id }

        Timber.d(
            "GamificationRepo: scanning %d coffee_places for userId=%s",
            placeIds.size,
            reviewerId
        )

        if (placeIds.isEmpty()) return@runCatching emptyList<CoffeeReview>()

        // 2) For each place, query reviews where userId == reviewerId
        val allDtos = mutableListOf<AppReviewDTO>()
        for (placeId in placeIds) {
            val col = firestore
                .collection("coffee_places")
                .document(placeId)
                .collection("reviews")
                .whereEqualTo("userId", reviewerId)

            val snapshot = if (limit != null) {
                col.limit(limit.toLong()).get().await()
            } else {
                col.get().await()
            }

            snapshot.documents.mapNotNullTo(allDtos) { doc ->
                runCatching { doc.toObject(AppReviewDTO::class.java) }.getOrNull()
            }

            if (limit != null && allDtos.size >= limit) break
        }

        Timber.d(
            "GamificationRepo: collected %d AppReviewDTOs for userId=%s",
            allDtos.size,
            reviewerId
        )

        // 3) Map DTOs to domain using a single user profile
        val userDto = getUserProfileUseCase(reviewerId).getOrNull()?.toDTO()
            ?: placeholderUser(reviewerId)
        allDtos.toDomainListForSingleUser(userDto)
    }

    override suspend fun getReviewsForPlace(
        placeId: String,
        userId: String,
        limit: Int?
    ): Result<List<CoffeeReview>> = coroutineScope {
        val dbDeferred = async { getAll(limit = limit, parentDocId = placeId) }
        val apiDeferred = async { placesApiRepository.getCoffeePlaceReviews(placeId) }

        val dbResult = dbDeferred.await()
        val apiResult = apiDeferred.await()

        // --- In-app reviews: only fetch users if there are any dtos ---
        val dtos: List<AppReviewDTO> = dbResult.getOrElse { emptyList() }

        val inApp: List<InAppReview> = if (dtos.isEmpty()) {
            // Nothing in DB => skip user fetch entirely
            emptyList()
        } else {
            // Build users map only when needed
            val userIds: List<String> = dtos.mapNotNull { it.userId }.distinct()
            val users =
                if (userIds.isEmpty()) emptyMap() else buildUserMapFromUseCase(
                    userIds,
                    strict = false
                )

            // --- New: fetch current user's reaction per review (parallel) ---
            val reviewIds: List<String> = dtos.mapNotNull { it.id }
            val reactionMap: Map<String, String?> = if (reviewIds.isEmpty()) {
                emptyMap()
            } else {
                try {
                    val reactions = reviewIds.map { rid ->
                        async {
                            val reactionDoc = firestore
                                .collection("coffee_places")
                                .document(placeId)
                                .collection("reviews")
                                .document(rid)
                                .collection("reactions")
                                .document(userId)
                                .get()
                                .await()

                            val type = if (reactionDoc.exists()) reactionDoc.getString("type") else null
                            rid to type
                        }
                    }.awaitAll()

                    reactions.toMap()
                } catch (e: Exception) {
                    Timber.w(e, "Failed to load reaction docs for user=%s on place=%s", userId, placeId)
                    emptyMap()
                }
            }

            dtos.toDomainListWithUsers(
                users = users,
                strict = false,
                reviewIdToReaction = reactionMap
            )
        }

        // --- Google reviews: failure -> empty list (don’t block in-app) ---
        val google: List<CoffeeReview> = apiResult.getOrElse { emptyList() }

        Result.success((inApp + google).myOrder(OrderMode.DefaultSectioned))
    }

    override suspend fun addReview(
        coffeeReview: InAppReview,
        placeId: String
    ): Result<String> = runCatching {
        val placeRef = firestore.collection("coffee_places").document(placeId)
        val reviewsCol = placeRef.collection("reviews")

        firestore.runTransaction { tx ->
            // 1) Generate / resolve review id + ref
            val existingIdFromDomain = coffeeReview.id?.takeIf { it.isNotBlank() }
            val reviewId = existingIdFromDomain ?: reviewsCol.document().id
            val reviewRef = reviewsCol.document(reviewId)

            // 2) Read current aggregate from place
            val placeSnap = tx.get(placeRef)
            val oldCount = (placeSnap.getLong("appRatingCount") ?: 0L).toInt()
            val oldAvg = placeSnap.getDouble("appRating") ?: 0.0

            val rating = coffeeReview.rating
            require(rating != null) {
                "InAppReview.rating must not be null when creating a review"
            }

            // 3) Compute new aggregate values
            val newCount = oldCount + 1
            val newAvg = if (newCount > 0) {
                (oldAvg * oldCount + rating) / newCount
            } else {
                rating.toDouble()
            }

            // 4) Write the review document
            val dto: AppReviewDTO = coffeeReview.toDto().copy(
                id = reviewId,
                placeId = placeId
            )
            tx.set(reviewRef, dto)

            // 5) Update place aggregates
            tx.update(
                placeRef,
                mapOf(
                    "appRating" to newAvg,
                    "appRatingCount" to newCount
                )
            )

            // Return the newly created review id
            reviewId
        }.await()
    }

    override suspend fun deleteReview(
        reviewId: String,
        placeId: String
    ): Result<Unit> = delete(reviewId, parentDocId = placeId)

    override suspend fun getReview(
        reviewId: String,
        placeId: String
    ): Result<CoffeeReview?> =
        getById(reviewId, parentDocId = placeId).map { dto ->
            val uid = dto?.userId.orEmpty()
            val userDto = getUserProfileUseCase(uid).getOrNull()?.toDTO()
                ?: placeholderUser(uid.ifBlank { "unknown" })
            dto?.toDomain(userDto)
        }

    override suspend fun reactToReview(
        placeId: String,
        reviewId: String,
        userId: String,
        isLike: Boolean
    ): Result<Unit> = runCatching {
        val placeRef = firestore.collection("coffee_places").document(placeId)
        val reviewRef = placeRef.collection("reviews").document(reviewId)
        val reactionRef = reviewRef.collection("reactions").document(userId)

        firestore.runTransaction { tx ->
            val reviewSnap = tx.get(reviewRef)
            val reactionSnap = tx.get(reactionRef)

            val currentType: String? =
                if (reactionSnap.exists()) reactionSnap.getString("type") else null
            val desiredType = if (isLike) "like" else "dislike"

            // Decide what the new type should be after toggling.
            val newType: String? = when (currentType) {
                desiredType -> null // same button twice -> clear reaction
                "like", "dislike" -> desiredType // opposite -> switch
                else -> desiredType // no existing reaction -> set
            }

            var likeCount = (reviewSnap.getLong("likeCount") ?: 0L).coerceAtLeast(0L)
            var dislikeCount = (reviewSnap.getLong("dislikeCount") ?: 0L).coerceAtLeast(0L)

            // Remove old reaction from counts
            when (currentType) {
                "like" -> if (likeCount > 0) likeCount--
                "dislike" -> if (dislikeCount > 0) dislikeCount--
            }

            // Add new reaction to counts
            when (newType) {
                "like" -> likeCount++
                "dislike" -> dislikeCount++
            }

            // Write / delete reaction document
            if (newType == null) {
                if (reactionSnap.exists()) {
                    tx.delete(reactionRef)
                }
            } else {
                tx.set(
                    reactionRef,
                    mapOf(
                        "userId" to userId,
                        "type" to newType,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            // Update aggregate counts on the review document
            tx.update(
                reviewRef,
                mapOf(
                    "likeCount" to likeCount,
                    "dislikeCount" to dislikeCount
                )
            )
        }.await()
    }

    // ----------------------------
    // Pagination
    // ----------------------------
    override suspend fun getReviewsForUserPaginated(
        reviewerId: String,
        pageSize: Int,
        reset: Boolean,
        orderBy: Pair<String, Query.Direction>?,
        key: String
    ): PaginatedResult<CoffeeReview> {
        val query = firestore.collectionGroup("reviews")
            .whereEqualTo("userId", reviewerId)

        val dtoPage = fetchPageFromCollectionGroup(
            childCollection = "reviews",
            pageSize = pageSize,
            reset = reset,
            query = query,
            orderBy = orderBy,
            key = key
        )

        val userDto = getUserProfileUseCase(reviewerId)
            .getOrNull()?.toDTO() ?: placeholderUser(reviewerId)

        return PaginatedResult(
            data = dtoPage.data.toDomainListForSingleUser(userDto),
            hasMore = dtoPage.hasMore
        )
    }

    override suspend fun getReviewsForPlacePaginated(
        placeId: String,
        pageSize: Int,
        reset: Boolean,
        orderBy: Pair<String, Query.Direction>?,
        key: String
    ): PaginatedResult<CoffeeReview> {
        val dtoPage = fetchPage(
            pageSize = pageSize,
            parentDocId = placeId,
            reset = reset,
            query = getCollection(placeId),
            orderBy = orderBy,
            key = key
        )

        val users = buildUserMapFromUseCase(
            dtoPage.data.mapNotNull { it.userId }.distinct(),
            strict = false
        )

        return PaginatedResult(
            data = dtoPage.data.toDomainListWithUsers(users = users, strict = false),
            hasMore = dtoPage.hasMore
        )
    }

    // ============================
    // Helpers
    // ============================
    private suspend fun buildUserMapFromUseCase(
        userIds: List<String>,
        strict: Boolean = false
    ): Map<String, CoffeeUserDTO> = supervisorScope {
        val distinct = userIds.distinct()
        if (distinct.isEmpty()) return@supervisorScope emptyMap()

        // launch all; one failure shouldn’t cancel others unless strict=true and we rethrow later
        val results = distinct.map { uid ->
            async {
                // Map success to DTO; if it fails and strict=false, fallback to placeholder.
                getUserProfileUseCase(uid)
                    .map { it.toDTO() }
                    .recoverCatching { e ->
                        if (strict) throw e
                        placeholderUser(uid)
                    }
                    .map { dto -> uid to dto }
                    .getOrThrow() // we want either (uid,dto) or throw if strict & failed
            }
        }.awaitAll()

        results.toMap()
    }

    private fun placeholderUser(userId: String) = CoffeeUserDTO(
        id = userId,
        fullName = "Unknown user",
        photoBase64 = null
    )
}