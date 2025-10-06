package com.synaptix.capetowncoffees.ui.savedLists

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceFull
import com.synaptix.capetowncoffees.domain.usecase.coffeePlace.CoffeePlaceUtilsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class ListDetailsPlacesAdapter(
    private val onItemClicked: (CoffeePlaceFull) -> Unit
) : ListAdapter<CoffeePlaceFull, ListDetailsPlacesAdapter.VH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_near_me, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.itemView.setOnClickListener {
            onItemClicked(item)
        }
        holder.bind(item)
    }

    fun submit(items: List<CoffeePlaceFull>) {
        submitList(items)
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvCafeName)
        private val tvDistance: TextView = itemView.findViewById(R.id.tvDistance)
        private val tvRating: TextView = itemView.findViewById(R.id.tvCafeRating)
        private val ivImage: ImageView = itemView.findViewById(R.id.ivImage)

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

//helper for images
class ListDetailsPhotoResolver @Inject constructor(
    private val coffeePlaceUtils: CoffeePlaceUtilsUseCase
) {
    suspend fun url(place: CoffeePlaceFull): String? {
        val meta = place.images?.firstOrNull() ?: return null
        return coffeePlaceUtils.getPhotoUriFromMetadata(meta, maxWidthDp = 500)?.toString()
    }
}
