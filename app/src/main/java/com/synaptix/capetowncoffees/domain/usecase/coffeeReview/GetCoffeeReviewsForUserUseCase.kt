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
import javax.inject.Inject

/* ──────────────────────────────────────────────────────────────────────────────
 * USE CASE — GET USER REVIEWS
 * ────────────────────────────────────────────────────────────────────────────── */

/**
 * Fetches coffee reviews written by a specific user (all/limited or paginated).
 * Use when UI/domain needs read-only access to reviews by `reviewerId`. No writes, no caching.
 *
 * Details:
 * - Orchestrator only: delegates to [ICoffeeReviewRepository].
 * - Threading: `suspend` functions; call from a coroutine. No dispatcher assumptions here.
 * - Error model: `invoke(...)` returns `Result<List<CoffeeReview>>`; pagination returns
 *   `PaginatedResult<CoffeeReview>` (no `Result` wrapper—empty page ≠ error).
 *
 * Edge cases:
 * - `limit == null` lets the repository decide how many to return (often “all”).
 * - Pagination: `reset=true` clears the cursor for the supplied `key`.
 *
 * Gotchas:
 * - Keep the pagination [key] stable per logical stream (tab/filter). Reusing keys across
 *   different queries mixes cursors and produces confusing pages.
 *
 * ### Examples
 * ```kotlin
 * // Example 1: fetch all (or limited) in a ViewModel
 * viewModelScope.launch {
 *   val result = getUserReviewsUseCase(reviewerId = "user-123", limit = 20)
 *   val items = result.getOrElse { emptyList() }
 *   _uiState.update { it.copy(reviews = items) }
 * }
 *
 * // Example 2: paginated fetch
 * viewModelScope.launch {
 *   val page1 = getUserReviewsUseCase.getPaginated(
 *     reviewerId = "user-123",
 *     pageSize = 10,
 *     reset = true,
 *     orderBy = "publishTime" to Query.Direction.DESCENDING,
 *     key = "user-123-reviews"
 *   )
 *   if (page1.hasMore) {
 *     val page2 = getUserReviewsUseCase.getPaginated(
 *       reviewerId = "user-123",
 *       pageSize = 10,
 *       reset = false,
 *       orderBy = "publishTime" to Query.Direction.DESCENDING,
 *       key = "user-123-reviews"
 *     )
 *   }
 * }
 * ```
 */
class GetCoffeeReviewsForUserUseCase @Inject constructor(
    private val coffeeReviewRepository: ICoffeeReviewRepository
) {

    /**
     * Returns all (or up to [limit]) reviews created by [reviewerId] as a `Result`.
     *
     * Details:
     * - Errors are wrapped in `Result.failure`; success may be an empty list.
     *
     * @param reviewerId Author’s user id.
     * @param limit Optional cap on result size; `null` allows repo default (often “all”).
     * @return `Result<List<CoffeeReview>>` with reviews or an error.
     */
    suspend operator fun invoke(reviewerId: String, limit: Int? = null): Result<List<CoffeeReview>> {
        return coffeeReviewRepository.getReviewsForUser(reviewerId, limit)
    }

    /**
     * Returns a page of reviews for [reviewerId] using a per-[key] cursor.
     *
     * Details:
     * - Call with [reset] = `true` to start from the first page for that [key].
     * - [orderBy] is `(field to direction)` and should match an indexed field in Firestore.
     *
     * Edge cases:
     * - Empty page returns `PaginatedResult(emptyList(), hasMore=false)`.
     *
     * Gotchas:
     * - Keep [key] stable per stream (e.g., `"reviews-$reviewerId"`). Don’t reuse across filters.
     *
     * @param reviewerId Author’s user id.
     * @param pageSize Items per page (must be > 0).
     * @param reset Whether to reset the cursor for this [key].
     * @param orderBy Optional order (field, direction), e.g. `"publishTime" to DESCENDING`.
     * @param key Stable identifier for the pagination stream.
     * @return `PaginatedResult<CoffeeReview>` with page data and `hasMore` flag.
     */
    suspend fun getPaginated(
        reviewerId: String,
        pageSize: Int,
        reset: Boolean,
        orderBy: Pair<String, Query.Direction>? = null,
        key: String = ""
    ): PaginatedResult<CoffeeReview> {
        return coffeeReviewRepository.getReviewsForUserPaginated(reviewerId, pageSize, reset, orderBy, key)
    }
}
