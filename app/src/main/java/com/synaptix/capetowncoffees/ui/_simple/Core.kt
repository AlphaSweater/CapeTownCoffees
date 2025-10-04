package com.synaptix.capetowncoffees.ui._simple

import kotlinx.coroutines.CancellationException

/**
 * Lightweight, UI-friendly error wrapper.
 *
 * Keep raw Throwables out of UI state. Map your domain/infra errors to UiError
 * so the view layer can show friendly messages consistently.
 *
 * ### Example
 * ```
 * try { ... }
 * catch (t: Throwable) {
 *   val err = t.toUiError()
 *   vm.main { vm.send(Effect.Message(err.message)) }
 * }
 * ```
 */
data class UiError(
    val message: String,
    val cause: Throwable? = null,
    val recoverable: Boolean = true
)

/** Normalize any Throwable for UI. Cancellation keeps propagating. */
fun Throwable.toUiError(defaultMsg: String = "Something went wrong"): UiError {
    if (this is CancellationException) throw this
    return UiError(message = message?.takeIf { it.isNotBlank() } ?: defaultMsg, cause = this)
}

/**
 * Generic "async piece of UI" state.
 * Use for sub-data that loads after screen shows (reviews, stats, etc.).
 *
 * ### Example
 * ```
 * val reviews = loadableState<List<Review>>()
 * fetchInto(reviews) { repo.getReviews(id) }
 * ```
 */
sealed class Loadable<out T> {
    /** Not started yet. */ data object Uninitialized : Loadable<Nothing>()
    /** In progress.     */ data object Loading : Loadable<Nothing>()
    /** Success.         */ data class Data<T>(val value: T) : Loadable<T>()
    /** Failed.          */ data class Error(val error: UiError) : Loadable<Nothing>()
}

/**
 * One-shot messages from VM to View (don’t replay on config changes).
 *
 * ### Example
 * ```
 * vm.main { vm.send(Effect.Navigate("placeDetails", ScreenArgs.placeDetails("123"))) }
 * ```
 */
sealed class Effect {
    data class Message(val text: String) : Effect()
    data class Navigate(val route: String, val args: android.os.Bundle? = null) : Effect()
}