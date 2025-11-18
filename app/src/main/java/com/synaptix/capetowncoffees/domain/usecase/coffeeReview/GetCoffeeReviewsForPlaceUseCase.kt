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

package com.synaptix.capetowncoffees.domain.usecase.coffeeReview

import com.google.firebase.firestore.Query
import com.synaptix.capetowncoffees.data.common.PaginatedResult
import com.synaptix.capetowncoffees.domain.model.CoffeeReview
import com.synaptix.capetowncoffees.domain.repository.ICoffeeReviewRepository
import com.synaptix.capetowncoffees.domain.usecase.coffeeUser.GetUserProfileUseCase
import javax.inject.Inject

/* ──────────────────────────────────────────────────────────────────────────────
 * USE CASE — GET PLACE REVIEWS
 * ────────────────────────────────────────────────────────────────────────────── */

/**
 * Fetches coffee reviews for a given place (all/limited or paginated).
 * Use when UI/domain needs read-only access to reviews by `placeId`. No writes, no caching here.
 *
 * Details:
 * - Pure orchestrator: delegates to [ICoffeeReviewRepository].
 * - Threading: `suspend` functions; call from a coroutine. No dispatcher assumptions.
 * - Error model: `invoke(...)` returns `Result<List<CoffeeReview>>`; pagination returns
 *   `PaginatedResult<CoffeeReview>` (no `Result` wrapper—empty page ≠ error).
 *
 * Edge cases:
 * - `limit == null` → repository decides “all”; pass a number to cap results.
 * - Pagination: `reset=true` clears the cursor for the given `key`; keep `key` stable per tab/filter.
 *
 * Gotchas:
 * - Choose a **stable** `key` per independent pagination stream; reusing keys across different
 *   queries mixes cursors and yields confusing pages.
 *
 * ### Examples
 * ```kotlin
 * // Example 1: fetch all (or limited) in a ViewModel
 * viewModelScope.launch {
 *   val result = getPlaceReviewsUseCase(placeId = "abc", limit = 20)
 *   val items = result.getOrElse { emptyList() }
 *   _uiState.update { it.copy(reviews = items) }
 * }
 *
 * // Example 2: paginated flow in a screen model
 * viewModelScope.launch {
 *   val page1 = getPlaceReviewsUseCase.getPaginated(
 *     placeId = "abc",
 *     pageSize = 10,
 *     reset = true,
 *     orderBy = "publishTime" to Query.Direction.DESCENDING,
 *     key = "reviews-abc"
 *   )
 *   if (page1.hasMore) {
 *     val page2 = getPlaceReviewsUseCase.getPaginated(
 *       placeId = "abc",
 *       pageSize = 10,
 *       reset = false,
 *       orderBy = "publishTime" to Query.Direction.DESCENDING,
 *       key = "reviews-abc"
 *     )
 *   }
 * }
 * ```
 */
class GetCoffeeReviewsForPlaceUseCase @Inject constructor(
    private val coffeeReviewRepository: ICoffeeReviewRepository,
    private val getUserProfileUseCase: GetUserProfileUseCase
) {

    /**
     * Returns all (or up to [limit]) reviews for a place as a `Result`.
     *
     * @param placeId Target place id.
     * @param limit Optional cap on result size; `null` lets the repository fetch “all”.
     * @return `Result<List<CoffeeReview>>` (errors are wrapped; success may be empty).
     */
    suspend operator fun invoke(placeId: String, userId: String? = null, limit: Int? = null): Result<List<CoffeeReview>> {

        // Resolve user id: use provided id or fetch current user via GetUserProfileUseCase
        val resolvedUserId = userId ?: run {
            val userResult = getUserProfileUseCase()
            val user = userResult.getOrElse { return Result.failure(it) }
            user.id
        }

        return coffeeReviewRepository.getReviewsForPlace(placeId, resolvedUserId, limit)
    }

    /**
     * Returns a page of reviews for a place with a per-[key] cursor.
     *
     * Details:
     * - Set [reset] to `true` to start from the first page for that [key].
     * - [orderBy] `(field to direction)` should match an indexed field in Firestore.
     *
     * Edge cases:
     * - Empty page returns `PaginatedResult(emptyList(), hasMore=false)`.
     *
     * Gotchas:
     * - Keep [key] stable for each logical stream (e.g., tab/filter) to avoid cursor mix-ups.
     *
     * @param placeId Target place id.
     * @param pageSize Items per page (must be > 0).
     * @param reset Whether to reset the cursor for this [key].
     * @param orderBy Optional order (field, direction).
     * @param key Stable identifier for the pagination stream (e.g., `"reviews-$placeId"`).
     * @return `PaginatedResult<CoffeeReview>` with page data and `hasMore` flag.
     */
    suspend fun getPaginated(
        placeId: String,
        pageSize: Int,
        reset: Boolean,
        orderBy: Pair<String, Query.Direction>? = null,
        key: String = ""
    ): PaginatedResult<CoffeeReview> {
        return coffeeReviewRepository.getReviewsForPlacePaginated(placeId, pageSize, reset, orderBy, key)
    }
}
