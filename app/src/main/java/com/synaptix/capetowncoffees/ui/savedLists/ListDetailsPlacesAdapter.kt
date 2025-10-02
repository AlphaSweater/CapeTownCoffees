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
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceFull

class ListDetailsPlacesAdapter : ListAdapter<CoffeePlaceFull, ListDetailsPlacesAdapter.VH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_near_me, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    fun submit(items: List<CoffeePlaceFull>) {
        submitList(items)
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvCafeName)
        private val tvDistance: TextView = itemView.findViewById(R.id.tvCafeDistance)
        private val tvRating: TextView = itemView.findViewById(R.id.tvCafeRating)
        private val ivImage: ImageView = itemView.findViewById(R.id.ivCafeImage)

        fun bind(item: CoffeePlaceFull) {
            tvName.text = item.name ?: "Unknown"
            tvDistance.text = item.address ?: ""
            val rating = item.rating ?: 0.0
            val count = item.ratingCount ?: 0
            tvRating.text = String.format("%.1f (%d)", rating, count)
            ivImage.setImageResource(R.drawable.cafe_placeholder)
        }
    }

    private class Diff : DiffUtil.ItemCallback<CoffeePlaceFull>() {
        override fun areItemsTheSame(oldItem: CoffeePlaceFull, newItem: CoffeePlaceFull): Boolean =
            oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: CoffeePlaceFull, newItem: CoffeePlaceFull): Boolean =
            oldItem == newItem
    }
}
