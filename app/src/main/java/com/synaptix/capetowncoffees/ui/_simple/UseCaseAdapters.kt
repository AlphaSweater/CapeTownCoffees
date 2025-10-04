package com.synaptix.capetowncoffees.ui._simple

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Run a suspend use-case that returns Kotlin Result and handle both paths.
 *
 * Usage:
 * io {
 *   runResult(
 *     call = { postReview(review) },
 *     onSuccess = { main { send(Effect.Message("Thanks!")) } },
 *     onFailure = { err -> main { send(Effect.Message(err.message)) } }
 *   )
 * }
 */
suspend fun <R> SimpleViewModel.runResult(
    call: suspend () -> Result<R>,
    onSuccess: (R) -> Unit,
    onFailure: (UiError) -> Unit = { err ->
        // default: toast/snackbar
        main { send(Effect.Message(err.message)) }
    }
) {
    try {
        val r = call()
        if (r.isSuccess) onSuccess(r.getOrThrow())
        else onFailure(r.exceptionOrNull()?.toUiError() ?: UiError("Unknown error"))
    } catch (t: Throwable) {
        onFailure(t.toUiError())
    }
}

/**
 * Feed a suspend Result use-case into a LoadableVar using the existing fetch path.
 *
 * NOTE: explicit `this.` and `suspend { ... }` avoid overload ambiguity.
 */
fun <R> SimpleViewModel.fetchResultInto(
    target: LoadableVar<R>,
    call: suspend () -> Result<R>,
    keepOldOnError: Boolean = true,
    label: String? = null
): Job = this.fetchInto(target, keepOldOnError, task = suspend {
    val r = call()
    if (r.isSuccess) r.getOrThrow()
    else throw r.exceptionOrNull() ?: IllegalStateException("Unknown failure")
}, label = label)

/**
 * Observe a Flow<Result<R>> into a LoadableVar, unwrapping successes.
 *
 * NOTE: explicit `this.` avoids picking the wrong overload.
 */
fun <R> SimpleViewModel.observeResultInto(
    target: LoadableVar<R>,
    flow: Flow<Result<R>>,
    label: String? = null
): Job = this.observeInto(
    target,
    flow
        .catch { e -> target.error(e.toUiError()) }
        .map { r -> r.getOrElse { throw it } },
    label = label
)

/** Run CPU-heavy work off main thread (use in your suspend blocks). */
suspend fun <R> onBg(block: () -> R): R =
    withContext(Dispatchers.Default) { block() }
