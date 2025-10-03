package com.synaptix.capetowncoffees.util

import com.synaptix.capetowncoffees.domain.model.CoffeeReview
import com.synaptix.capetowncoffees.domain.model.GooglePlaceReview
import com.synaptix.capetowncoffees.domain.model.InAppReview

/* ──────────────────────────────────────────────────────────────────────────────
 * REVIEW ORDERING — MODES
 * ────────────────────────────────────────────────────────────────────────────── */

/**
 * Sort modes for `CoffeeReview` lists.
 *
 * Details:
 * - `DefaultSectioned` → In-app first, then Google. Each group uses the item’s **current** `order`.
 * - `GoogleFirstSectioned` → Google first, then in-app. Each group uses the item’s **current** `order`.
 * - `TimeAsc/TimeDesc` → Global time sort with **nulls last**; stable tie-breakers ensure determinism.
 * - `RatingAsc/RatingDesc` → Global rating sort with **nulls last**; stable tie-breakers ensure determinism.
 *
 * Gotchas:
 * - “Sectioned” modes produce two groups; the within-group ordering is stable by `order` → time → id.
 */
enum class OrderMode {
    DefaultSectioned,
    GoogleFirstSectioned,
    TimeAsc,
    TimeDesc,
    RatingAsc,
    RatingDesc,
}

/* ──────────────────────────────────────────────────────────────────────────────
 * REVIEW ORDERING — ENTRY POINT
 * ────────────────────────────────────────────────────────────────────────────── */

/**
 * Orders reviews according to the chosen [OrderMode].
 * Use this as the single entry point so sort rules stay consistent across the app.
 *
 * Details:
 * - Deterministic: ties are resolved consistently (time/rating → `order` → `id`).
 * - Sectioned modes preserve group separation (in-app vs Google) and stable group order.
 *
 * Edge cases:
 * - For time/rating sorts, `null` values are treated as **worst** and are placed last.
 *
 * ### Examples
 * ```kotlin
 * val ordered = reviews.myOrder(OrderMode.DefaultSectioned)
 * val newestFirst = reviews.myOrder(OrderMode.TimeDesc)
 * ```
 */
fun List<CoffeeReview>.myOrder(mode: OrderMode): List<CoffeeReview> = when (mode) {
    OrderMode.DefaultSectioned       -> this.sortedByDefaultSectioned(inAppFirst = true, useDefaultOrder = false)
    OrderMode.GoogleFirstSectioned   -> this.sortedByDefaultSectioned(inAppFirst = false, useDefaultOrder = false)
    OrderMode.TimeAsc                -> this.sortedByTimeAsc()
    OrderMode.TimeDesc               -> this.sortedByTimeDesc()
    OrderMode.RatingAsc              -> this.sortedByRatingAsc()
    OrderMode.RatingDesc             -> this.sortedByRatingDesc()
}

/* ──────────────────────────────────────────────────────────────────────────────
 * REVIEW ORDERING — SECTIONED (IN-APP VS GOOGLE)
 * ────────────────────────────────────────────────────────────────────────────── */

/**
 * Two-section ordering (in-app vs Google), with stable, deterministic rules within each section.
 *
 * Details:
 * - Group order: controlled by [inAppFirst].
 * - Within-group sort key: `order` (or `defaultOrder` when [useDefaultOrder]=true)
 *   → `publishTime` (desc; newer first) → `id` (asc; stable tiebreaker).
 * - Returns a **new list**; original list is not mutated.
 *
 * Edge cases:
 * - `publishTime == null` is treated as **oldest** (falls to `Long.MIN_VALUE` on tiebreak).
 *
 * Gotchas:
 * - Use `useDefaultOrder=true` if you need to restore baseline deterministic ordering.
 *
 * ### Examples
 * ```kotlin
 * // In-app first, use current per-item order
 * val sectioned = reviews.sortedByDefaultSectioned(inAppFirst = true)
 *
 * // Google first, but use each item’s defaultOrder
 * val baseline = reviews.sortedByDefaultSectioned(inAppFirst = false, useDefaultOrder = true)
 * ```
 */
fun List<CoffeeReview>.sortedByDefaultSectioned(
    inAppFirst: Boolean = true,
    useDefaultOrder: Boolean = false
): List<CoffeeReview> {
    val (firstIsInApp, secondIsInApp) = if (inAppFirst) true to false else false to true
    fun withinGroupKey(r: CoffeeReview): Int = if (useDefaultOrder) r.defaultOrder else r.order

    val firstGroup = this
        .filter { it.isInApp == firstIsInApp }
        .sortedWith(
            compareBy<CoffeeReview> { withinGroupKey(it) }
                .thenByDescending { it.publishTime ?: Long.MIN_VALUE }
                .thenBy { it.id.orEmpty() }
        )

    val secondGroup = this
        .filter { it.isInApp == secondIsInApp }
        .sortedWith(
            compareBy<CoffeeReview> { withinGroupKey(it) }
                .thenByDescending { it.publishTime ?: Long.MIN_VALUE }
                .thenBy { it.id.orEmpty() }
        )

    return firstGroup + secondGroup
}

/* ──────────────────────────────────────────────────────────────────────────────
 * REVIEW ORDERING — GLOBAL (TIME/RATING)
 * ────────────────────────────────────────────────────────────────────────────── */

/**
 * Global time sort: **oldest first** with **nulls last**.
 * Stable tie-breakers: in-app first → `order` → `id`.
 */
fun List<CoffeeReview>.sortedByTimeAsc(): List<CoffeeReview> =
    this.sortedWith(
        compareBy<CoffeeReview> { it.publishTime == null }
            .thenBy { it.publishTime ?: Long.MAX_VALUE }
            .thenByDescending { if (it.isInApp) 1 else 0 }
            .thenBy { it.order }
            .thenBy { it.id.orEmpty() }
    )

/**
 * Global time sort: **newest first** with **nulls last**.
 * Stable tie-breakers: in-app first → `order` → `id`.
 */
fun List<CoffeeReview>.sortedByTimeDesc(): List<CoffeeReview> =
    this.sortedWith(
        compareBy<CoffeeReview> { it.publishTime == null }
            .thenByDescending { it.publishTime ?: Long.MIN_VALUE }
            .thenByDescending { if (it.isInApp) 1 else 0 }
            .thenBy { it.order }
            .thenBy { it.id.orEmpty() }
    )

/**
 * Global rating sort: **lowest first** with **nulls last**.
 * Stable tie-breakers: in-app first → time desc → `id`.
 */
fun List<CoffeeReview>.sortedByRatingAsc(): List<CoffeeReview> =
    this.sortedWith(
        compareBy<CoffeeReview> { it.rating == null }
            .thenBy { it.rating ?: Double.MAX_VALUE }
            .thenByDescending { if (it.isInApp) 1 else 0 }
            .thenByDescending { it.publishTime ?: Long.MIN_VALUE }
            .thenBy { it.id.orEmpty() }
    )

/**
 * Global rating sort: **highest first** with **nulls last**.
 * Stable tie-breakers: in-app first → time desc → `id`.
 */
fun List<CoffeeReview>.sortedByRatingDesc(): List<CoffeeReview> =
    this.sortedWith(
        compareBy<CoffeeReview> { it.rating == null }
            .thenByDescending { it.rating ?: Double.NEGATIVE_INFINITY }
            .thenByDescending { if (it.isInApp) 1 else 0 }
            .thenByDescending { it.publishTime ?: Long.MIN_VALUE }
            .thenBy { it.id.orEmpty() }
    )

/* ──────────────────────────────────────────────────────────────────────────────
 * REVIEW ORDERING — RESET & WITHIN-GROUP SORTS
 * ────────────────────────────────────────────────────────────────────────────── */

/**
 * Resets `order` to each item’s `defaultOrder` within its own type (in-app/Google).
 * Returns a new list with updated items; immutable for other properties.
 *
 * Gotchas:
 * - Assumes `InAppReview`/`GooglePlaceReview` implement `resetToDefaultOrder()`.
 */
fun List<CoffeeReview>.resetGroupOrdersToDefault(): List<CoffeeReview> =
    map {
        when (it) {
            is InAppReview -> it.resetToDefaultOrder()
            is GooglePlaceReview -> it.resetToDefaultOrder()
        }
    }

/**
 * Sorts **within each group** by current `order`; group concatenation preserves **first-seen group**.
 * Example: if a Google item appears before any in-app item in the input, Google comes first.
 */
fun List<CoffeeReview>.sortedWithinEachGroupByCurrentOrder(): List<CoffeeReview> {
    val inApp = filter { it.isInApp }.sortedBy { it.order }
    val google = filter { it.isGoogle }.sortedBy { it.order }
    return if (indexOfFirst { it.isGoogle } > indexOfFirst { it.isInApp }) {
        inApp + google
    } else {
        google + inApp
    }
}

/**
 * Sorts **within each group** by `defaultOrder`; group concatenation preserves **first-seen group**.
 */
fun List<CoffeeReview>.sortedWithinEachGroupByDefaultOrder(): List<CoffeeReview> {
    val inApp = filter { it.isInApp }.sortedBy { it.defaultOrder }
    val google = filter { it.isGoogle }.sortedBy { it.defaultOrder }
    return if (indexOfFirst { it.isGoogle } > indexOfFirst { it.isInApp }) {
        inApp + google
    } else {
        google + inApp
    }
}

/**
 * Sorts **within each group** by rating **desc** with **nulls last**, then time desc, then id.
 * Group concatenation preserves **first-seen group**.
 */
fun List<CoffeeReview>.sortedWithinEachGroupByRatingDesc(): List<CoffeeReview> {
    val cmp = compareBy<CoffeeReview> { it.rating == null }
        .thenByDescending { it.rating ?: Double.NEGATIVE_INFINITY }
        .thenByDescending { it.publishTime ?: Long.MIN_VALUE }
        .thenBy { it.id.orEmpty() }
    val inApp = filter { it.isInApp }.sortedWith(cmp)
    val google = filter { it.isGoogle }.sortedWith(cmp)
    return if (indexOfFirst { it.isGoogle } > indexOfFirst { it.isInApp }) {
        inApp + google
    } else {
        google + inApp
    }
}

/**
 * Sorts **within each group** by time **desc** with **nulls last**, then in-app first, `order`, `id`.
 * Group concatenation preserves **first-seen group**.
 */
fun List<CoffeeReview>.sortedWithinEachGroupByTimeDesc(): List<CoffeeReview> {
    val cmp = compareBy<CoffeeReview> { it.publishTime == null }
        .thenByDescending { it.publishTime ?: Long.MIN_VALUE }
        .thenByDescending { if (it.isInApp) 1 else 0 }
        .thenBy { it.order }
        .thenBy { it.id.orEmpty() }
    val inApp = filter { it.isInApp }.sortedWith(cmp)
    val google = filter { it.isGoogle }.sortedWith(cmp)
    return if (indexOfFirst { it.isGoogle } > indexOfFirst { it.isInApp }) {
        inApp + google
    } else {
        google + inApp
    }
}

/* ──────────────────────────────────────────────────────────────────────────────
 * REVIEW ORDERING — RE-INDEX HELPERS
 * ────────────────────────────────────────────────────────────────────────────── */

/**
 * Re-numbers `order` for **in-app** reviews only, starting at `1`, scanning in input order.
 * Use after sorting to “bake in” the current order for persistence.
 */
fun List<CoffeeReview>.reindexInAppOrders(): List<CoffeeReview> {
    var next = 1
    return map { if (it is InAppReview) it.withOrder(next++) else it }
}

/**
 * Re-numbers `order` for **Google** reviews only, starting at `1`, scanning in input order.
 * Use after sorting to “bake in” the current order for persistence.
 */
fun List<CoffeeReview>.reindexGoogleOrders(): List<CoffeeReview> {
    var next = 1
    return map { if (it is GooglePlaceReview) it.withOrder(next++) else it }
}

/**
 * Re-numbers `order` for **both** groups independently, each starting at `1`, scanning input order.
 * Use after a sectioned sort to persist the visible within-group order.
 */
fun List<CoffeeReview>.reindexAllGroupOrders(): List<CoffeeReview> {
    var nextInApp = 1
    var nextGoogle = 1
    return map {
        when (it) {
            is InAppReview -> it.withOrder(nextInApp++)
            is GooglePlaceReview -> it.withOrder(nextGoogle++)
        }
    }
}

/* ──────────────────────────────────────────────────────────────────────────────
 * REVIEW ORDERING — TYPE GUARDS (ONE-LINERS)
 * ────────────────────────────────────────────────────────────────────────────── */

/** True if this review is an in-app review. */
val CoffeeReview.isInApp get() = this is InAppReview

/** True if this review is a Google Place review. */
val CoffeeReview.isGoogle get() = this is GooglePlaceReview
