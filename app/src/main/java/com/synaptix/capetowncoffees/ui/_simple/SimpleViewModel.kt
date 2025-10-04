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

/**
 * Simple, no-ceremony ViewModel base.
 *
 * You only:
 * 1) Declare fields with `state()/loadableState()/booleanState()`
 * 2) Override [start] and kick off `fetchInto`/`observeInto`
 *
 * Built-ins:
 * - Effects for one-shot messages (snackbar/nav)
 * - IO/Main launch helpers with error mapping
 * - Timber logs for fetch/observe/effects (debug-friendly)
 */
abstract class SimpleViewModel : ViewModel() {

    // Effects (one-shot)
    private val _effects = Channel<Effect>(Channel.BUFFERED)
    /** Observe from the view to handle nav/snackbar, etc. */
    val effects: Flow<Effect> = _effects.receiveAsFlow()

    // Debug state
    internal val _debugState = VmDebugState()

    /** Send a one-shot effect. */
    suspend fun send(effect: Effect) {
        SimpleVmDebug.logD(this) { "sendEffect: $effect" }
        _effects.send(effect)
    }

    /**
     * Entry point — call from Fragment (prefer `start(vm)` helper).
     * Parse args, then kick off fetch/observe work.
     */
    open fun start(args: Bundle?) {
        _debugMarkStarted()
        SimpleVmDebug.logD(this) { "start(args=${args?.keySet()?.joinToString() ?: "none"})" }
    }

    // ------- Coroutine helpers -------

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

    // ------- Fetch (suspend -> state) -------

    /**
     * Run suspend [task] and write result into a plain [StateVar].
     *
     * @param showLoading Optional boolean flag to toggle during the call.
     * @param label Optional log label (e.g., "place", "title")
     *
     * ### Example
     * ```
     * fetchInto(title, showLoading = isLoading, label = "title") { repo.getTitle() }
     * ```
     */
    fun <T> fetchInto(
        target: StateVar<T>,
        showLoading: StateVar<Boolean>? = null,
        task: suspend () -> T,
        label: String? = null
    ) = io(onError = { err ->
        showLoading?.set(false)
        SimpleVmDebug.logE(this, { "fetchInto(StateVar) FAILED${label?.let { " [$it]" } ?: ""}: ${err.message}" })
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
     * Run suspend [task] and write result into a [LoadableVar] (Loading/Data/Error).
     *
     * @param keepOldOnError Preserve previous Data on error (UI stability).
     * @param label Optional log label (e.g., "reviews", "stats")
     *
     * ### Example
     * ```
     * fetchInto(reviews, label = "reviews") { repo.getReviews(id) }
     * ```
     */
    fun <T> fetchInto(
        target: LoadableVar<T>,
        keepOldOnError: Boolean = true,
        task: suspend () -> T,
        label: String? = null
    ) = io(onError = { err ->
        val current = target.value
        if (keepOldOnError && current is Loadable.Data) {
            SimpleVmDebug.logE(this, { "fetchInto(Loadable) ERROR keepOld${label?.let { " [$it]" } ?: ""}: ${err.message}" })
            main { send(Effect.Message(err.message)) }
        } else {
            target.error(err)
            SimpleVmDebug.logE(this, { "fetchInto(Loadable) FAILED${label?.let { " [$it]" } ?: ""}: ${err.message}" })
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

    // ------- Observe (Flow -> state) -------

    /**
     * Collect a Flow into a [StateVar]. Optionally toggles a loading flag.
     *
     * ### Example
     * ```
     * observeInto(count, repo.observeCount(), showLoading = isLoading, label = "count")
     * ```
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
                SimpleVmDebug.logE(this@SimpleViewModel, { "observeInto(StateVar) FAILED${label?.let { " [$it]" } ?: ""}: ${err.message}" }, e)
                send(Effect.Message(err.message))
            }
            .collect { v ->
                target.set(v)
                showLoading?.set(false)
            }
    }

    /**
     * Collect a Flow into a [LoadableVar] with Loading/Data/Error handling.
     *
     * ### Example
     * ```
     * observeInto(stats, repo.observeStats(id), label = "stats")
     * ```
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
                SimpleVmDebug.logE(this@SimpleViewModel, { "observeInto(Loadable) FAILED${label?.let { " [$it]" } ?: ""}: ${err.message}" }, e)
            }
            .collect { v -> target.data(v) }
    }

    // ------- Debug support -------

    /** Mark VM as started. Called internally & by Fragment.start(vm). */
    internal fun _debugMarkStarted() { _debugState.startInvoked = true }
}