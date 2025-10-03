package com.synaptix.capetowncoffees.util

import com.synaptix.capetowncoffees.domain.model.CoffeeReview
import com.synaptix.capetowncoffees.domain.model.GooglePlaceReview
import com.synaptix.capetowncoffees.domain.model.InAppReview
import com.synaptix.capetowncoffees.domain.model.sortedByDefaultSectioned
import com.synaptix.capetowncoffees.domain.model.sortedByTimeAsc
import com.synaptix.capetowncoffees.domain.model.sortedByTimeDesc

/**
 * Ordering modes for mixed CoffeeReview lists.
 *
 * Usage example:
 * ```kotlin
 * val ordered = reviews.myOrder(OrderMode.TimeDesc)
 * ```
 *
 * - DefaultSectioned: All InApp (by order) first, then all Google (by order).
 * - TimeAsc/TimeDesc: Combined chronological across both sources (ignores 'order').
 * - RatingAsc/RatingDesc: Combined across both sources by rating (nulls pushed to the end).
 */
enum class OrderMode {
    DefaultSectioned,
    TimeAsc,
    TimeDesc,
    RatingAsc,
    RatingDesc,
}

/**
 * Orders a list of CoffeeReview items according to the specified [OrderMode].
 *
 * Usage example:
 * ```kotlin
 * val ordered = reviews.myOrder(OrderMode.RatingDesc)
 * ```
 *
 * @param mode The ordering mode to apply.
 * @return A new list of CoffeeReview items ordered as specified.
 */
fun List<CoffeeReview>.myOrder(mode: OrderMode): List<CoffeeReview> = when (mode) {
    OrderMode.DefaultSectioned -> this.resetGroupOrdersToDefault().sortedByDefaultSectioned()
    OrderMode.TimeAsc          -> this.sortedByTimeAsc()
    OrderMode.TimeDesc         -> this.sortedByTimeDesc()
    OrderMode.RatingAsc        -> this.sortedByRatingAsc()
    OrderMode.RatingDesc       -> this.sortedByRatingDesc()
}

/**
 * Resets both InAppReview and GooglePlaceReview items in the list to their default order.
 * Non-destructive: returns new instances.
 *
 * Usage example:
 * ```kotlin
 * val reset = reviews.resetGroupOrdersToDefault()
 * ```
 *
 * @return A new list with each review reset to its default order.
 */
fun List<CoffeeReview>.resetGroupOrdersToDefault(): List<CoffeeReview> =
    map {
        when (it) {
            is InAppReview -> it.resetToDefaultOrder()
            is GooglePlaceReview -> it.resetToDefaultOrder()
        }
    }

/**
 * Sorts CoffeeReview items by rating in ascending order (null ratings last).
 *
 * Usage example:
 * ```kotlin
 * val sorted = reviews.sortedByRatingAsc()
 * ```
 *
 * @return A new list sorted by rating ascending, nulls last.
 */
fun List<CoffeeReview>.sortedByRatingAsc(): List<CoffeeReview> =
    sortedWith(compareBy<CoffeeReview> { it.rating == null }
        .thenBy { it.rating ?: Double.MAX_VALUE })

/**
 * Sorts CoffeeReview items by rating in descending order (null ratings last).
 *
 * Usage example:
 * ```kotlin
 * val sorted = reviews.sortedByRatingDesc()
 * ```
 *
 * @return A new list sorted by rating descending, nulls last.
 */
fun List<CoffeeReview>.sortedByRatingDesc(): List<CoffeeReview> =
    sortedWith(compareBy<CoffeeReview> { it.rating == null }
        .thenByDescending { it.rating ?: Double.NEGATIVE_INFINITY })

// ----------------------------------------------------------------------
// Optional: helpers to reindex 'order' within each group after drag-drop
// ----------------------------------------------------------------------

/**
 * Reassigns order = index+1 for all InAppReview items; leaves GooglePlaceReview unchanged.
 *
 * Usage example:
 * ```kotlin
 * val re-indexed = reviews.reindexInAppOrders()
 * ```
 *
 * @return A new list with InAppReview items re-indexed by order.
 */
fun List<CoffeeReview>.reindexInAppOrders(): List<CoffeeReview> {
    var next = 1
    return map {
        if (it is InAppReview) it.withOrder(next++) else it
    }
}

/**
 * Reassigns order = index+1 for all GooglePlaceReview items; leaves InAppReview unchanged.
 *
 * Usage example:
 * ```kotlin
 * val re-indexed = reviews.reindexGoogleOrders()
 * ```
 *
 * @return A new list with GooglePlaceReview items re-indexed by order.
 */
fun List<CoffeeReview>.reindexGoogleOrders(): List<CoffeeReview> {
    var next = 1
    return map {
        if (it is GooglePlaceReview) it.withOrder(next++) else it
    }
}

/**
 * Reassigns order = index+1 separately for each group (InAppReview and GooglePlaceReview)
 * based on current relative positions.
 *
 * Usage example:
 * ```kotlin
 * val re-indexed = reviews.reindexAllGroupOrders()
 * ```
 *
 * @return A new list with both groups re-indexed by order.
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