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

class ListDetailsPlacesAdapter @AssistedInject constructor(
    @Assisted private val coroutineScope: CoroutineScope,
    private val photoResolver: ListDetailsPhotoResolver
) : ListAdapter<CoffeePlaceFull, ListDetailsPlacesAdapter.VH>(Diff()) {

    @AssistedFactory
    interface Factory {
        fun create(coroutineScope: CoroutineScope): ListDetailsPlacesAdapter
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        // Keep your existing item layout. Ensure it contains ivImage, tvCafeName, tvDistance, tvCafeRating.
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_near_me, parent, false)
        return VH(view, coroutineScope, photoResolver)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    fun submit(items: List<CoffeePlaceFull>) = submitList(items)

    class VH(
        itemView: View,
        private val scope: CoroutineScope,
        private val photoResolver: ListDetailsPhotoResolver
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvName: TextView = itemView.findViewById(R.id.tvCafeName)
        private val tvDistance: TextView = itemView.findViewById(R.id.tvDistance)
        private val tvRating: TextView = itemView.findViewById(R.id.tvCafeRating)
        private val ivImage: ImageView = itemView.findViewById(R.id.ivImage)

        fun bind(item: CoffeePlaceFull) {
            tvName.text = item.name ?: "Unknown"
            tvDistance.text = item.address.orEmpty()
            val rating = item.rating ?: 0.0
            val count = item.ratingCount ?: 0
            tvRating.text = String.format("%.1f (%d)", rating, count)

            ivImage.setImageResource(R.drawable.featured_placeholder)

            scope.launch {
                val url = photoResolver.url(item)
                if (url.isNullOrBlank()) {
                    ivImage.setImageResource(R.drawable.featured_placeholder)
                } else {
                    Glide.with(ivImage)
                        .load(url)
                        .thumbnail(0.25f)
                        .placeholder(R.drawable.featured_placeholder)
                        .error(R.drawable.featured_placeholder)
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .into(ivImage)
                }
            }
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
