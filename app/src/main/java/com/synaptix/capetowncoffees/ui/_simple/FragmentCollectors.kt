package com.synaptix.capetowncoffees.ui._simple

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Lifecycle-safe Flow collector for Fragments.
 *
 * Collects while the view lifecycle is at least [minState] (default STARTED).
 * Stops automatically on view destroy — no leaks.
 *
 * ### Example
 * ```
 * collect(vm.effects) { eff -> handleEffect(eff) }
 * collect(vm.title.flow) { title -> binding.title.text = title }
 * ```
 */
inline fun <T> Fragment.collect(
    flow: Flow<T>,
    minState: Lifecycle.State = Lifecycle.State.STARTED,
    crossinline block: (T) -> Unit
) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.lifecycle.repeatOnLifecycle(minState) {
            flow.collect { block(it) }
        }
    }
}

/** Convenience collector for LoadableVar to keep Fragments tidy. */
fun <T> Fragment.collectLoadable(
    varRef: LoadableVar<T>,
    minState: Lifecycle.State = Lifecycle.State.STARTED,
    block: (Loadable<T>) -> Unit
) = collect(varRef.flow, minState, block)