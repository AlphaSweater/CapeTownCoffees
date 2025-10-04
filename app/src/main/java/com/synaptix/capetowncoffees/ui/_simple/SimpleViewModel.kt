package com.synaptix.capetowncoffees.ui._simple

import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow

/* ──────────────────────────────────────────────────────────────────────────────
 * SIMPLE VIEWMODEL — TEAM GUIDE (READ ME)
 * ────────────────────────────────────────────────────────────────────────────── */

/**
 * Lightweight ViewModel base with the helpers your screen code needs.
 *
 * Prefer injecting focused USE CASES (GetCoffee, ObserveReviews, SaveFavorite, etc.)
 * rather than calling repositories directly inside the ViewModel. This keeps orchestration
 * separate from domain/data access and simplifies testing.
 *
 * • [start] – your entry point from the Fragment (read args, kick off work)
 * • [fetchInto] – run a suspend call and push the result into state
 * • [observeInto] – collect a Flow into state (with built-in loading/error for Loadable)
 * • [effects]/[send] – one-shot UI events (snackbar, navigation)
 *
 * You almost never need to touch anything else here.
 */
abstract class SimpleViewModel : ViewModel() {

    /* ──────────────────────────────────────────────────────────────────────────
     * EFFECTS (ONE-SHOT UI SIGNALS)
     * ────────────────────────────────────────────────────────────────────────── */

    private val _effects = Channel<Effect>(Channel.BUFFERED)

    /**
     * One-shot UI events (snackbar, navigation).
     *
     * In your Fragment:
     * ```
     * collect(vm.effects) { eff ->
     *   when (eff) {
     *     is Effect.Message  -> snackbar(eff.text)
     *     is Effect.Navigate -> findNavController().navigate(R.id.placeDetailsFragment, eff.args)
     *   }
     * }
     * ```
     */
    val effects: Flow<Effect> = _effects.receiveAsFlow()

    /** Internal debug: tracks whether start() ran and last error. */
    internal val _debugState = VmDebugState()

    /**
     * Emit a one-shot [Effect].
     *
     * Use inside `main { … }` or any suspend block in your VM:
     * ```
     * main { send(Effect.Message("Saved")) }
     * ```
     */
    suspend fun send(effect: Effect) {
        SimpleVmDebug.logD(this) { "sendEffect: $effect" }
        _effects.send(effect)
    }

    /**
     * Entry point from the UI — **always call this** in your Fragment:
     * ```
     * start(vm) // or vm.start(arguments)
     * ```
     * Read arguments and kick off your initial `fetchInto` / `observeInto` calls.
     */
    open fun start(args: Bundle?) {
        _debugMarkStarted()
        SimpleVmDebug.logD(this) { "start(args=${args?.keySet()?.joinToString() ?: "none"})" }
    }

    /* ──────────────────────────────────────────────────────────────────────────
     * COROUTINE HELPERS (YOU RARELY NEED THESE DIRECTLY)
     * ────────────────────────────────────────────────────────────────────────── */

    /** Run work on IO with unified error handling. Prefer [fetchInto]/[observeInto]. */
    fun io(
        onError: (UiError) -> Unit = {},
        block: suspend CoroutineScope.() -> Unit
    ) = viewModelScope.launch(Dispatchers.IO) {
        try {
            if (SimpleVmDebug.warnIfBeforeStart && !_debugState.startInvoked) {
                SimpleVmDebug.logD(this@SimpleViewModel) { "WARN: io{} before start()" }
            }
            block()
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            val err = t.toUiError()
            _debugState.lastError = err
            withContext(Dispatchers.Main) { onError(err) }
            SimpleVmDebug.logE(this@SimpleViewModel, { "io{} error: ${err.message}" }, t)
        }
    }

    /** Run work on Main with unified error handling. Prefer [fetchInto]/[observeInto]. */
    fun main(
        onError: (UiError) -> Unit = {},
        block: suspend CoroutineScope.() -> Unit
    ) = viewModelScope.launch(Dispatchers.Main) {
        try {
            if (SimpleVmDebug.warnIfBeforeStart && !_debugState.startInvoked) {
                SimpleVmDebug.logD(this@SimpleViewModel) { "WARN: main{} before start()" }
            }
            block()
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            val err = t.toUiError()
            _debugState.lastError = err
            onError(err)
            SimpleVmDebug.logE(this@SimpleViewModel, { "main{} error: ${err.message}" }, t)
        }
    }

    /* ──────────────────────────────────────────────────────────────────────────
     * FETCH (SUSPEND → STATE)
     * ────────────────────────────────────────────────────────────────────────── */

    /**
     * One-shot load into a plain [StateVar].
     *
     * Use this for primary/atomic data (title, profile, main DTO, etc.).
     *
     * Example (using a suspend use case):
     * ```kotlin
     * val isLoading = booleanState()
     * val place = state<Place?>(null)
     * fetchInto(place, showLoading = isLoading, label = "place") { getPlace(id) }
     * ```
     * (where `getPlace: GetPlace` is injected use case)
     *
     * - Automatically toggles [showLoading]
     * - Writes state on Main
     * - On error: logs + `Effect.Message(errorText)`
     */
    fun <T> fetchInto(
        target: StateVar<T>,
        showLoading: StateVar<Boolean>? = null,
        task: suspend () -> T,
        label: String? = null
    ) = io(onError = { err ->
        showLoading?.set(false)
        SimpleVmDebug.logE(
            this,
            { "fetchInto(StateVar) FAILED${label?.let { " [$it]" } ?: ""}: ${err.message}" }
        )
        main { send(Effect.Message(err.message)) }
    }) {
        withContext(Dispatchers.Main) {
            SimpleVmDebug.logD(this@SimpleViewModel) { "fetchInto(StateVar) START${label?.let { " [$it]" } ?: ""}" }
            showLoading?.set(true)
        }
        val result = task()
        withContext(Dispatchers.Main) {
            target.set(result)
            showLoading?.set(false)
            SimpleVmDebug.logD(this@SimpleViewModel) { "fetchInto(StateVar) OK${label?.let { " [$it]" } ?: ""}" }
        }
    }

    /**
     * One-shot load into a [LoadableVar] with Loading/Data/Error transitions.
     *
     * Use this for **sub-data** (lists/sections that appear after the main content).
     *
     * Example (using a suspend use case):
     * ```kotlin
     * val reviews = loadableState<List<Review>>()
     * fetchInto(reviews, label = "reviews") { getReviews(id) }
     * ```
     * (where `getReviews: GetReviews` is injected use case)
     *
     * - Starts at `Loading`
     * - On success → `Data(value)`
     * - On failure → either `Error(...)` OR keep old data if [keepOldOnError] is true
     */
    fun <T> fetchInto(
        target: LoadableVar<T>,
        keepOldOnError: Boolean = true,
        task: suspend () -> T,
        label: String? = null
    ) = io(onError = { err ->
        val current = target.value
        if (keepOldOnError && current is Loadable.Data) {
            SimpleVmDebug.logE(
                this,
                { "fetchInto(Loadable) ERROR keepOld${label?.let { " [$it]" } ?: ""}: ${err.message}" }
            )
            main { send(Effect.Message(err.message)) }
        } else {
            target.error(err)
            SimpleVmDebug.logE(
                this,
                { "fetchInto(Loadable) FAILED${label?.let { " [$it]" } ?: ""}: ${err.message}" }
            )
        }
    }) {
        withContext(Dispatchers.Main) {
            target.loading()
            SimpleVmDebug.logD(this@SimpleViewModel) { "fetchInto(Loadable) START${label?.let { " [$it]" } ?: ""}" }
        }
        val value = task()
        withContext(Dispatchers.Main) {
            target.data(value)
            SimpleVmDebug.logD(this@SimpleViewModel) { "fetchInto(Loadable) OK${label?.let { " [$it]" } ?: ""}" }
        }
    }

    /* ──────────────────────────────────────────────────────────────────────────
     * OBSERVE (FLOW → STATE)
     * ────────────────────────────────────────────────────────────────────────── */

    /**
     * Collect a **Flow** into a **plain state**.
     *
     * Use for streams where you don’t need Loading/Error UI (e.g., counters, small flags).
     *
     * Example (using a Flow use case):
     * ```kotlin
     * observeInto(count, observeCount(), showLoading = isLoading, label = "count")
     * ```
     * (where `observeCount: ObserveCount` is injected and returns Flow<Int>)
     *
     * - Sets [showLoading]=true on subscribe, false on each value/error
     * - On error: logs + `Effect.Message(errorText)`
     */
    fun <T> observeInto(
        target: StateVar<T>,
        flow: Flow<T>,
        showLoading: StateVar<Boolean>? = null,
        label: String? = null
    ) = viewModelScope.launch {
        SimpleVmDebug.logD(this@SimpleViewModel) { "observeInto(StateVar) SUBSCRIBE${label?.let { " [$it]" } ?: ""}" }
        flow
            .onStart { showLoading?.set(true) }
            .catch { e ->
                showLoading?.set(false)
                val err = e.toUiError()
                _debugState.lastError = err
                SimpleVmDebug.logE(
                    this@SimpleViewModel,
                    { "observeInto(StateVar) FAILED${label?.let { " [$it]" } ?: ""}: ${err.message}" },
                    e
                )
                send(Effect.Message(err.message))
            }
            .collect { v ->
                target.set(v)
                showLoading?.set(false)
            }
    }

    /**
     * Collect a **Flow** into a **Loadable** (built-in Loading/Data/Error).
     *
     * Use when the UI needs a skeleton/progress per emission (lists, charts, etc.).
     *
     * Example (using a Flow use case):
     * ```kotlin
     * observeInto(stats, observeStats(id), label = "stats")
     * ```
     * (where `observeStats: ObserveStats` is injected and returns Flow<Stats>)
     */
    fun <T> observeInto(
        target: LoadableVar<T>,
        flow: Flow<T>,
        label: String? = null
    ) = viewModelScope.launch {
        SimpleVmDebug.logD(this@SimpleViewModel) { "observeInto(Loadable) SUBSCRIBE${label?.let { " [$it]" } ?: ""}" }
        target.loading()
        flow
            .catch { e ->
                val err = e.toUiError()
                _debugState.lastError = err
                target.error(err)
                SimpleVmDebug.logE(
                    this@SimpleViewModel,
                    { "observeInto(Loadable) FAILED${label?.let { " [$it]" } ?: ""}: ${err.message}" },
                    e
                )
            }
            .collect { v -> target.data(v) }
    }

    /* ────────────────────────────────────────────────────────────────────────────
     * DEBUG SUPPORT (auto; no action needed)
     * ────────────────────────────────────────────────────────────────────────── */

    /** Marks VM as started (used by logs/guardrails). Called internally & by Fragment.start(vm). */
    internal fun _debugMarkStarted() { _debugState.startInvoked = true }
}
