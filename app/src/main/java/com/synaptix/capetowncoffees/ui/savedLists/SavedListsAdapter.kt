package com.synaptix.capetowncoffees.ui.savedLists

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.domain.model.UserList

class SavedListsAdapter(
    private val onItemClick: (UserList) -> Unit = {}
) : ListAdapter<UserList, SavedListsAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_saved_list, parent, false)
        return ViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun submit(items: kotlin.collections.List<UserList>) {
        submitList(items)
    }

    class ViewHolder(
        itemView: View,
        private val onItemClick: (UserList) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.tvName)
        private val subtitle: TextView = itemView.findViewById(R.id.tvSubtitle)
        private val icon: ImageView = itemView.findViewById(R.id.ivIcon)

        fun bind(item: UserList) {
            name.text = item.name
            val placeCount = item.placeIds.size
            subtitle.text = if (placeCount == 1) "1 place" else "$placeCount places"
            
            // Set different icon based on list type
            icon.setImageResource(
                if (item.isPublic) R.drawable.ic_explore 
                else R.drawable.ic_lock
            )
            
            itemView.setOnClickListener { onItemClick(item) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<UserList>() {
        override fun areItemsTheSame(oldItem: UserList, newItem: UserList): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: UserList, newItem: UserList): Boolean {
            return oldItem == newItem
        }
    }
}
