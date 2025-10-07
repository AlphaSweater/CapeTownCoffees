//======================================================================================
//Group 2 - Group Members:
//======================================================================================
//* Chad Fairlie ST10269509
//* Dhiren Ruthenavelu ST10256859
//* Kayla Ferreira ST10259527
//* Nathan Teixeira ST10249266
//======================================================================================
//References:
//======================================================================================
//* ChatGPT was used to assist with the development, design, and debugging of this file.
//* AI support was used for learning purposes, improving clarity and resolving issues.
//* It also helped generate useful comments
//======================================================================================

package com.synaptix.capetowncoffees.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import java.util.concurrent.Executors

/** Base ViewHolder with optional payload & lifecycle hooks. */
abstract class BaseViewHolder<T, VB : ViewBinding>(val vb: VB) : RecyclerView.ViewHolder(vb.root) {
    open fun bind(item: T) {}
    open fun bind(item: T, payloads: List<Any>) { bind(item) }
    open fun onRecycled() {}
    open fun onAttached() {}
    open fun onDetached() {}
}

/**
 * Minimal generic ListAdapter with:
 * - unique fallback viewType (Concat-safe),
 * - optional stable IDs,
 * - viewType-aware binding (back-compat),
 * - distinct-submit helper,
 * - payload coalescing,
 * - safe item accessors.
 */
abstract class BaseAdapter<T : Any, VB : ViewBinding>(
    diff: DiffUtil.ItemCallback<T>,
    private val idProvider: ((T) -> Long)? = null,
    differConfig: AsyncDifferConfig<T>? = null,
) : ListAdapter<T, BaseViewHolder<T, VB>>(differConfig ?: AsyncDifferConfig.Builder(diff)
    // Optional: move diffing off main onto a small pool; default is fine too.
    .setBackgroundThreadExecutor(Executors.newSingleThreadExecutor())
    .build()
) {

    init { setHasStableIds(idProvider != null) }

    // Unique fallback viewType per adapter instance (single-viewtype case).
    private val defaultViewType: Int by lazy {
        (System.identityHashCode(this) and 0x00FFFFFF) or 0x7F000000
    }

    /** Override if you have multiple types or want to return a layout id. */
    open fun itemViewTypeFor(position: Int): Int = defaultViewType
    final override fun getItemViewType(position: Int): Int = itemViewTypeFor(position)

    /** Old signature (single-type); still supported. */
    open fun onCreateBinding(inflater: LayoutInflater, parent: ViewGroup): VB =
        throw NotImplementedError("Provide onCreateBinding(inflater,parent,viewType) or override this")

    /** New signature (multi-type friendly). Default calls the old one for back-compat. */
    open fun onCreateBinding(inflater: LayoutInflater, parent: ViewGroup, viewType: Int): VB =
        onCreateBinding(inflater, parent)

    abstract fun onCreateVH(binding: VB): BaseViewHolder<T, VB>

    final override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder<T, VB> {
        val vb = onCreateBinding(LayoutInflater.from(parent.context), parent, viewType)
        return onCreateVH(vb)
    }

    final override fun onBindViewHolder(holder: BaseViewHolder<T, VB>, position: Int) =
        holder.bind(getItem(position))

    final override fun onBindViewHolder(
        holder: BaseViewHolder<T, VB>,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            holder.bind(getItem(position))
        } else {
            // Coalesce payloads to avoid duplicate work (esp. with sealed payloads).
            val merged = if (payloads.size == 1) payloads else payloads.toSet().toList()
            holder.bind(getItem(position), merged)
        }
    }

    final override fun getItemId(position: Int): Long =
        idProvider?.invoke(getItem(position)) ?: RecyclerView.NO_ID

    // Nice-to-haves
    fun getItemOrNull(position: Int): T? =
        if (position in 0 until itemCount) getItem(position) else null

    val itemCountFast: Int get() = super.getItemCount()

    /** Avoids doing a diff when the same list instance is already displayed. */
    fun submitListDistinct(list: List<T>?, commitCallback: (() -> Unit)? = null) {
        if (list === currentList) {
            commitCallback?.invoke()
            return
        }
        submitList(list, commitCallback)
    }

    final override fun onViewRecycled(holder: BaseViewHolder<T, VB>) {
        holder.onRecycled()
        super.onViewRecycled(holder)
    }

    final override fun onViewAttachedToWindow(holder: BaseViewHolder<T, VB>) {
        super.onViewAttachedToWindow(holder); holder.onAttached()
    }

    final override fun onViewDetachedFromWindow(holder: BaseViewHolder<T, VB>) {
        holder.onDetached(); super.onViewDetachedFromWindow(holder)
    }
}

/** Super-tiny DiffUtil helper. */
fun <T : Any> simpleDiff(
    sameItem: (old: T, new: T) -> Boolean,
    sameContent: (old: T, new: T) -> Boolean = { o, n -> o == n },
    payload: (old: T, new: T) -> Any? = { _, _ -> null },
) = object : DiffUtil.ItemCallback<T>() {
    override fun areItemsTheSame(oldItem: T, newItem: T) = sameItem(oldItem, newItem)
    override fun areContentsTheSame(oldItem: T, newItem: T) = sameContent(oldItem, newItem)
    override fun getChangePayload(oldItem: T, newItem: T): Any? = payload(oldItem, newItem)
}
