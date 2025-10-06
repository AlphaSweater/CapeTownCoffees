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
    @Assisted private val onItemClicked: (CoffeePlaceFull) -> Unit,
    private val photoResolver: ListDetailsPhotoResolver
) : ListAdapter<CoffeePlaceFull, ListDetailsPlacesAdapter.VH>(Diff()) {

    @AssistedFactory
    interface Factory {
        fun create(
            coroutineScope: CoroutineScope,
            onItemClicked: (CoffeePlaceFull) -> Unit
        ): ListDetailsPlacesAdapter
    }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long =
        getItem(position).id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_near_me, parent, false)
        return VH(view, coroutineScope, photoResolver, onItemClicked)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    fun submit(items: List<CoffeePlaceFull>) = submitList(items)

    class VH(
        itemView: View,
        private val scope: CoroutineScope,
        private val photoResolver: ListDetailsPhotoResolver,
        private val onItemClicked: (CoffeePlaceFull) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvName: TextView = itemView.findViewById(R.id.tvCafeName)
        private val tvAddress: TextView = itemView.findViewById(R.id.tvDistance) // your layout uses this id for address
        private val tvRating: TextView = itemView.findViewById(R.id.tvCafeRating)
        private val ivImage: ImageView = itemView.findViewById(R.id.ivImage)

        fun bind(item: CoffeePlaceFull) {
            tvName.text = item.name ?: "Unknown"
            tvAddress.text = item.address.orEmpty()

            val rating = item.rating ?: 0.0
            val count = item.ratingCount ?: 0
            tvRating.text = String.format("%.1f (%d)", rating, count)

            // Placeholder first
            ivImage.setImageResource(R.drawable.featured_placeholder)

            // Resolve Places photo → load with Glide
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

            itemView.setOnClickListener { onItemClicked(item) }
            ivImage.setOnClickListener  { onItemClicked(item) }
        }
    }

    private class Diff : DiffUtil.ItemCallback<CoffeePlaceFull>() {
        override fun areItemsTheSame(o: CoffeePlaceFull, n: CoffeePlaceFull) = o.id == n.id
        override fun areContentsTheSame(o: CoffeePlaceFull, n: CoffeePlaceFull) = o == n
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
