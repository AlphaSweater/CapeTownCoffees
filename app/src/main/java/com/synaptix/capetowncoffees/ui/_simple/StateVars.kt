package com.synaptix.capetowncoffees.ui._simple

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Tiny Flow-backed state holder.
 *
 * ### Example
 * ```
 * // VM
 * val title = state("Loading…")
 * fetchInto(title) { repo.getTitle() }
 *
 * // Fragment
 * collect(vm.title.flow) { binding.title.text = it }
 * ```
 */
open class StateVar<T> internal constructor(initial: T) {
    private val backing = MutableStateFlow(initial)
    /** Observe from the view layer. */
    val flow: StateFlow<T> = backing.asStateFlow()
    /** Synchronous access from the VM. */
    var value: T
        get() = backing.value
        set(v) { backing.value = v }

    fun set(v: T) { backing.value = v }
    fun update(block: (T) -> T) = backing.update(block)
}

/**
 * List-flavoured state with handy mutators.
 *
 * ### Example
 * ```
 * val items = listState<Int>()
 * items.add(1); items.addAll(listOf(2,3)); items.clear()
 * ```
 */
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

/**
 * Loadable state holder (Loading/Data/Error).
 *
 * ### Example
 * ```
 * val reviews = loadableState<List<Review>>()
 * fetchInto(reviews) { repo.getReviews(placeId) }
 * ```
 */
class LoadableVar<T> internal constructor(initial: Loadable<T> = Loadable.Uninitialized) {
    private val backing = MutableStateFlow(initial)
    val flow: StateFlow<Loadable<T>> = backing.asStateFlow()
    val value: Loadable<T> get() = backing.value

    fun loading()  { backing.value = Loadable.Loading }
    fun data(v: T) { backing.value = Loadable.Data(v) }
    fun error(e: UiError) { backing.value = Loadable.Error(e) }
    fun set(v: Loadable<T>) { backing.value = v }
}

/** Factory helpers for VMs */
fun <T> state(initial: T): StateVar<T> = StateVar(initial)
fun booleanState(initial: Boolean = false): StateVar<Boolean> = StateVar(initial)
fun <T> listState(initial: List<T> = emptyList()): ListStateVar<T> = ListStateVar(initial)
fun <T> loadableState(): LoadableVar<T> = LoadableVar()