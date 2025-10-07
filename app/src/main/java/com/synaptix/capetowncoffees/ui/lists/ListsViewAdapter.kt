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

class ListsViewAdapter(
    private val onItemClick: (CoffeeList) -> Unit = {}
) : ListAdapter<CoffeeList, ListsViewAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_saved_list, parent, false)
        return ViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun submit(items: kotlin.collections.List<CoffeeList>) {
        submitList(items)
    }

    class ViewHolder(
        itemView: View,
        private val onItemClick: (CoffeeList) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.tvName)
        private val subtitle: TextView = itemView.findViewById(R.id.tvSubtitle)
        private val icon: ImageView = itemView.findViewById(R.id.ivIcon)

        fun bind(item: CoffeeList) {
            name.text = item.name
            val placeCount = item.placeIds.size
            subtitle.text = if (placeCount == 1) "1 place" else "$placeCount places"
            
            // Set different icon based on list type
            icon.setImageResource(
                if (item.isPublic) R.drawable.ic_ctc_earth_public
                else R.drawable.ic_ctc_earth_private
            )
            
            itemView.setOnClickListener { onItemClick(item) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<CoffeeList>() {
        override fun areItemsTheSame(oldItem: CoffeeList, newItem: CoffeeList): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CoffeeList, newItem: CoffeeList): Boolean {
            return oldItem == newItem
        }
    }
}
