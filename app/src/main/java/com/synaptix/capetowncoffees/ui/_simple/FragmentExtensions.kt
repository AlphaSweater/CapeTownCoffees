package com.synaptix.capetowncoffees.ui._simple

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/* ──────────────────────────────────────────────────────────────────────────────
 * SIMPLE Fragment Extensions
 * ────────────────────────────────────────────────────────────────────────────── */

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

fun <T> Fragment.collectLoadable(
    varRef: LoadableVar<T>,
    minState: Lifecycle.State = Lifecycle.State.STARTED,
    block: (Loadable<T>) -> Unit
) = collect(varRef.flow, minState, block)

fun Fragment.start(vm: SimpleViewModel, args: Bundle? = this.arguments) {
    vm._debugMarkStarted()
    vm.start(args)
}

