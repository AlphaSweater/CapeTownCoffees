package com.synaptix.capetowncoffees.ui._simple

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

// Consolidated primitives (from Core.kt, StateVars.kt, UseCaseAdapters.kt)
// - UiError + toUiError
// - Loadable
// - Effect
// - StateVar / ListStateVar / LoadableVar + factories
// - Result/use-case adapters + onBg helper
// Keep this focused; avoid UI / ViewModel code here.

// ---------------- Errors ----------------

data class UiError(
    val message: String,
    val cause: Throwable? = null,
    val recoverable: Boolean = true
)

fun Throwable.toUiError(defaultMsg: String = "Something went wrong"): UiError {
    if (this is CancellationException) throw this
    return UiError(message = message?.takeIf { it.isNotBlank() } ?: defaultMsg, cause = this)
}

// ---------------- Loadable ----------------

sealed class Loadable<out T> {
    data object Uninitialized : Loadable<Nothing>()
    data object Loading : Loadable<Nothing>()
    data class Data<T>(val value: T) : Loadable<T>()
    data class Error(val error: UiError) : Loadable<Nothing>()
}

// ---------------- Effects ----------------

sealed class Effect {
    data class Message(val text: String) : Effect()
    data class Navigate(val route: String, val args: android.os.Bundle? = null) : Effect()
}

// ---------------- State wrappers ----------------

open class StateVar<T> internal constructor(initial: T) {
    private val backing = MutableStateFlow(initial)
    val flow: StateFlow<T> = backing.asStateFlow()
    var value: T
        get() = backing.value
        set(v) { backing.value = v }

    fun set(v: T) { backing.value = v }
    fun update(block: (T) -> T) = backing.update(block)
}

class ListStateVar<T> internal constructor(initial: List<T>) : StateVar<List<T>>(initial) {
    fun add(item: T) = update { it + item }
    fun addAll(items: Iterable<T>) = update { it + items }
    fun removeFirst(predicate: (T) -> Boolean) = update {
        val m = it.toMutableList()
        val i = m.indexOfFirst(predicate); if (i >= 0) m.removeAt(i)
        m
    }
    fun replaceAll(items: List<T>) = set(items)
    fun clear() = set(emptyList())
}

class LoadableVar<T> internal constructor(initial: Loadable<T> = Loadable.Uninitialized) {
    private val backing = MutableStateFlow(initial)
    val flow: StateFlow<Loadable<T>> = backing.asStateFlow()
    val value: Loadable<T> get() = backing.value

    fun loading()  { backing.value = Loadable.Loading }
    fun data(v: T) { backing.value = Loadable.Data(v) }
    fun error(e: UiError) { backing.value = Loadable.Error(e) }
    fun set(v: Loadable<T>) { backing.value = v }
}

// Factory helpers
fun <T> state(initial: T): StateVar<T> = StateVar(initial)
fun booleanState(initial: Boolean = false): StateVar<Boolean> = StateVar(initial)
fun <T> listState(initial: List<T> = emptyList()): ListStateVar<T> = ListStateVar(initial)
fun <T> loadableState(): LoadableVar<T> = LoadableVar()

// ---------------- Result adapters ----------------

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

suspend fun <R> onBg(block: () -> R): R = withContext(Dispatchers.Default) { block() }
