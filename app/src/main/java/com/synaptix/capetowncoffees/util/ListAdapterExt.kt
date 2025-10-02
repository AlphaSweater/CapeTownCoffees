package com.synaptix.capetowncoffees.util

import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * Extension function to update items in a ListAdapter
 */
fun <T, VH : RecyclerView.ViewHolder> ListAdapter<T, VH>.updateItems(newItems: List<T>) {
    submitList(newItems.toList()) // Create a new list to ensure diffing works correctly
}

/**
 * Extension function to update items in a RecyclerView.Adapter
 */
fun <T> RecyclerView.Adapter<*>.updateItems(
    newItems: List<T>,
    updateCallback: (List<T>) -> Unit
) {
    updateCallback(newItems)
    notifyDataSetChanged()
}
