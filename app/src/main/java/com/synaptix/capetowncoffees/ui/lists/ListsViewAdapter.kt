package com.synaptix.capetowncoffees.ui.lists

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.domain.model.CoffeeList

// ─────────── Adapter ───────────
// Shows saved lists with name, count, and a visibility icon. Row click forwards the model.
public class ListsViewAdapter(
    private val onItemClick: (CoffeeList) -> Unit = {}
) : ListAdapter<CoffeeList, ListsViewAdapter.ViewHolder>(DiffCallback) {

    // ─────────── Lifecycle (Adapter) ───────────
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_saved_list, parent, false)
        return ViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // Keep a small façade to mirror existing call sites.
    public fun submit(items: kotlin.collections.List<CoffeeList>) {
        submitList(items)
    }

    // ─────────── ViewHolder ───────────
    // Caches view refs; bind() maps a CoffeeList to row UI fast.
    public class ViewHolder(
        itemView: View,
        private val onItemClick: (CoffeeList) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val name: TextView = itemView.findViewById(R.id.tvName)
        private val subtitle: TextView = itemView.findViewById(R.id.tvSubtitle)
        private val icon: ImageView = itemView.findViewById(R.id.ivIcon)

        fun bind(item: CoffeeList) {
            name.text = item.name

            val count = item.placeIds.size
            subtitle.text = if (count == 1) "1 place" else "$count places"

            icon.setImageResource(
                if (item.isPublic) R.drawable.ic_ctc_earth_public else R.drawable.ic_ctc_earth_private
            )

            itemView.setOnClickListener { onItemClick(item) }
        }
    }

    // ─────────── Diffing ───────────
    // Stable id equality + full content equality keeps animations correct and cheap.
    private object DiffCallback : DiffUtil.ItemCallback<CoffeeList>() {
        override fun areItemsTheSame(oldItem: CoffeeList, newItem: CoffeeList): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: CoffeeList, newItem: CoffeeList): Boolean =
            oldItem == newItem
    }
}
