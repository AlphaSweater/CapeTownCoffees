package com.synaptix.capetowncoffees.ui.common.viewmodel

import android.os.Bundle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

/* ──────────────────────────────────────────────────────────────────────────────
 * SIMPLE Primitives
 * ────────────────────────────────────────────────────────────────────────────── */

// ---------------- Errors ----------------

/**
 * UI-friendly error model used across the Simple architecture.
 *
 * Why not expose raw Throwable? We normalize message + optionally mark recoverability.
 *
 * Typical usage (NOTICE: call a use case, not a repository directly):
 * ```kotlin
 * try {
 *   saveCoffeeUseCase(coffee)
 * } catch (t: Throwable) {
 *   val ui = t.toUiError()
 *   main { send(Effect.Message(ui.message)) }
 * }
 * ```
 */
data class UiError(
    val message: String,
    val cause: Throwable? = null,
    val recoverable: Boolean = true
)

/**
 * Convert any [Throwable] into a [UiError].
 *
 * Rules:
 * - Re-throws [CancellationException] so structured cancellation stays intact.
 * - Falls back to [defaultMsg] if throwable message is blank/null.
 *
 * Example (use case centric):
 * ```kotlin
 * suspend fun load(vm: SimpleViewModel) = vm.io {
 *   try { refreshDataUseCase() } catch (t: Throwable) {
 *     val ui = t.toUiError("Refresh failed")
 *     main { send(Effect.Message(ui.message)) }
 *   }
 * }
 * ```
 */
fun Throwable.toUiError(defaultMsg: String = "Something went wrong"): UiError {
    if (this is CancellationException) throw this
    return UiError(message = message?.takeIf { it.isNotBlank() } ?: defaultMsg, cause = this)
}

// ---------------- Loadable ----------------

/**
 * Represents data that has a lightweight lifecycle: Uninitialized → Loading → Data OR Error.
 *
 * Preferred for secondary / sectional UI (lists, stats panels, related items) that can appear progressively.
 *
 * Rendering pattern (Fragment):
 * ```kotlin
 * collectLoadable(vm.reviews) { loadable ->
 *   when (loadable) {
 *     Loadable.Uninitialized, Loadable.Loading -> showReviewsSkeleton()
 *     is Loadable.Data  -> showReviews(loadable.value)
 *     is Loadable.Error -> showReviewsError(loadable.error.message)
 *   }
 * }
 * ```
 */
sealed class Loadable<out T> {
    /** Nothing requested yet (initial screen). */
    data object Uninitialized : Loadable<Nothing>()
    /** Active fetch or active subscription waiting for first emission. */
    data object Loading : Loadable<Nothing>()
    /** Successfully loaded value. */
    data class Data<T>(val value: T) : Loadable<T>()
    /** Terminal or latest error state (for Loadable flows / one-shots). */
    data class Error(val error: UiError) : Loadable<Nothing>()
}

// ---------------- Effects ----------------

/**
 * One-shot UI events. Collected via [SimpleViewModel.effects].
 * Keep the sealed class lean; add new subtypes sparingly.
 *
 * Common handling:
 * ```kotlin
 * collect(vm.effects) { eff ->
 *   when (eff) {
 *     is Effect.Message  -> showSnack(eff.text)
 *     is Effect.Navigate -> findNavController().navigate(eff.route, eff.args)
 *   }
 * }
 * ```
 */
sealed class Effect {
    /** Transient user message (snackbar / toast). */
    data class Message(val text: String) : Effect()
    /** Navigation request; route interpreted by the Fragment/Coordinator. */
    data class Navigate(val route: String, val args: Bundle? = null) : Effect()
}

// ---------------- State wrappers ----------------

/**
 * Thin wrapper around a [MutableStateFlow] providing a stable minimal API for ViewModels.
 *
 * Use for primary screen state where you DO NOT need Loading/Error semantics.
 * Combine with a separate `booleanState()` if you need a global spinner.
 *
 * Example in ViewModel (delegating to a use case):
 * ```kotlin
 * val coffee = state<Coffee?>(null)
 * fetchInto(coffee, showLoading = isLoading) { getCoffeeUseCase(id) }
 * ```
 */
open class StateVar<T> internal constructor(initial: T) {
    private val backing = MutableStateFlow(initial)
    /** Read-only state flow for UI collection. */
    val flow: StateFlow<T> = backing.asStateFlow()
    /** Direct value access (main thread) for convenience. */
    var value: T
        get() = backing.value
        set(v) { backing.value = v }

    /** Set new value. */
    fun set(v: T) { backing.value = v }
    /** Functional update without exposing MutableStateFlow. */
    fun update(block: (T) -> T) = backing.update(block)
}

/**
 * Convenience specialization for list mutation helpers.
 * Avoid using for large paging; prefer the Paging library then push snapshots into a plain [StateVar].
 */
class ListStateVar<T> internal constructor(initial: List<T>) : StateVar<List<T>>(initial) {
    /** Append a single item. */
    fun add(item: T) = update { it + item }
    /** Append many items. */
    fun addAll(items: Iterable<T>) = update { it + items }
    /** Remove first element matching [predicate] (no-op if none). */
    fun removeFirst(predicate: (T) -> Boolean) = update {
        val m = it.toMutableList()
        val i = m.indexOfFirst(predicate); if (i >= 0) m.removeAt(i)
        m
    }
    /** Replace whole list. */
    fun replaceAll(items: List<T>) = set(items)
    /** Clear list. */
    fun clear() = set(emptyList())
}

/**
 * Stateful holder for [Loadable] values.
 * Mutating helpers produce state transitions consumed by UI skeleton / error surfaces.
 *
 * Example manual usage (rare — usually use `fetchInto/observeInto`):
 * ```kotlin
 * reviews.loading()
 * try { reviews.data(getReviewsUseCase(id)) } catch (t: Throwable) { reviews.error(t.toUiError()) }
 * ```
 */
class LoadableVar<T> internal constructor(initial: Loadable<T> = Loadable.Uninitialized) {
    private val backing = MutableStateFlow(initial)
    /** Stream for collection in the Fragment via `collectLoadable(var)` */
    val flow: StateFlow<Loadable<T>> = backing.asStateFlow()
    /** Current snapshot (Data/Error/Loading/etc.). */
    val value: Loadable<T> get() = backing.value

    fun loading()  { backing.value = Loadable.Loading }
    fun data(v: T) { backing.value = Loadable.Data(v) }
    fun error(e: UiError) { backing.value = Loadable.Error(e) }
    fun set(v: Loadable<T>) { backing.value = v }
}

// Factory helpers

/** Create a [StateVar] for arbitrary state. */
fun <T> state(initial: T): StateVar<T> = StateVar(initial)
/** Create a boolean loading / toggle flag. */
fun booleanState(initial: Boolean = false): StateVar<Boolean> = StateVar(initial)
/** Create a list state holder with mutation helpers. */
fun <T> listState(initial: List<T> = emptyList()): ListStateVar<T> = ListStateVar(initial)
/** Create a lazily-transitioned loadable holder (starts Uninitialized). */
fun <T> loadableState(): LoadableVar<T> = LoadableVar()

// ---------------- Result adapters ----------------

/**
 * Helper to execute a suspend block returning [Result] and route outcomes.
 *
 * Typical pattern when integrating existing Result-returning use cases:
 * ```kotlin
 * runResult(
 *   call = { loginUseCase(username, pass) },
 *   onSuccess = { user -> main { send(Effect.Navigate("home")) } },
 *   onFailure = { err -> main { send(Effect.Message(err.message)) } }
 * )
 * ```
 * If [onFailure] omitted → default snackbar Effect.
 */
suspend fun <R> SimpleViewModel.runResult(
    call: suspend () -> Result<R>,
    onSuccess: (R) -> Unit,
    onFailure: (UiError) -> Unit = { err ->
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
 * Bridge for legacy Result-based one-shot into a [LoadableVar].
 * Wraps `fetchInto(target)` but understands Kotlin [Result].
 *
 * Example 1:
 * ```kotlin
 * val profile = loadableState<User>()
 * fetchResultInto(profile, suspend { getUserResultUseCase(id) })
 * ```
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
 * Bridge for a Flow<Result<T>> into a [LoadableVar].
 * Converts internal failures into upstream exceptions so the normal observe path handles them.
 *
 * Example:
 * ```kotlin
 * observeResultInto(stats, observeStatsResultUseCase(id))
 * ```
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

// ---------------- Background helper ----------------

/**
 * Lightweight dispatcher hop for quick off-main computation (sorting, mapping etc.).
 * Prefer structured helpers (fetchInto/observeInto) for main data access; use this for small pure transforms.
 *
 * Example:
 * ```kotlin
 * val sorted = onBg { unsorted.sortedBy { it.name } }
 * ```
 */
suspend fun <R> onBg(block: () -> R): R = withContext(Dispatchers.Default) { block() }
