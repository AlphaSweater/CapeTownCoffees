package com.synaptix.capetowncoffees.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding


/** Base ViewHolder with optional payload binding. */
abstract class BaseViewHolder<T, VB : ViewBinding>(val vb: VB) : RecyclerView.ViewHolder(vb.root) {
    open fun bind(item: T) {}
    open fun bind(item: T, payloads: List<Any>) { bind(item) }
}


/** Minimal generic ListAdapter to kill boilerplate. */
abstract class BaseAdapter<T : Any, VB : ViewBinding>(
    diff: DiffUtil.ItemCallback<T>,
    private val idProvider: ((T) -> Long)? = null,
) : androidx.recyclerview.widget.ListAdapter<T, BaseViewHolder<T, VB>>(diff) {


    init { setHasStableIds(idProvider != null) }


    abstract fun onCreateBinding(inflater: LayoutInflater, parent: ViewGroup): VB
    abstract fun onCreateVH(binding: VB): BaseViewHolder<T, VB>


    final override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder<T, VB> {
        val vb = onCreateBinding(LayoutInflater.from(parent.context), parent)
        return onCreateVH(vb)
    }


    final override fun onBindViewHolder(holder: BaseViewHolder<T, VB>, position: Int) =
        holder.bind(getItem(position))


    final override fun onBindViewHolder(holder: BaseViewHolder<T, VB>, position: Int, payloads: MutableList<Any>) =
        if (payloads.isNotEmpty()) holder.bind(getItem(position), payloads) else holder.bind(getItem(position))


    final override fun getItemId(position: Int): Long =
        idProvider?.invoke(getItem(position)) ?: super.getItemId(position)
}


/** Super-tiny DiffUtil helper so you don't have to write a class each time. */
fun <T : Any> simpleDiff(
    sameItem: (old: T, new: T) -> Boolean,
    sameContent: (old: T, new: T) -> Boolean = { o, n -> o == n },
    payload: (old: T, new: T) -> Any? = { _, _ -> null },
) = object : DiffUtil.ItemCallback<T>() {
    override fun areItemsTheSame(oldItem: T, newItem: T) = sameItem(oldItem, newItem)
    override fun areContentsTheSame(oldItem: T, newItem: T) = sameContent(oldItem, newItem)
    override fun getChangePayload(oldItem: T, newItem: T): Any? = payload(oldItem, newItem)
}