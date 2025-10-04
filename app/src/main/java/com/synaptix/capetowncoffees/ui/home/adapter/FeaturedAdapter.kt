package com.synaptix.capetowncoffees.ui.home.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.maps.model.LatLng
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.synaptix.capetowncoffees.domain.usecase.coffeePlace.CoffeePlaceUtilsUseCase
import com.synaptix.capetowncoffees.util.LocationUtil
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
class FeaturedAdapter @AssistedInject constructor(
    @Assisted private var currentLocation: LatLng? = null,
    @Assisted private val coroutineScope: CoroutineScope,
    @Assisted private val onItemClick: (CoffeePlaceLite) -> Unit = {},
    private val coffeePlaceUtilsUseCase: CoffeePlaceUtilsUseCase,
    private val locationUtil: LocationUtil
) : ListAdapter<CoffeePlaceLite, FeaturedAdapter.ViewHolder>(DiffCallback()) {

    @AssistedFactory
    interface Factory {
        fun create(
            currentLocation: LatLng?,
            coroutineScope: CoroutineScope,
            onItemClick: (CoffeePlaceLite) -> Unit
        ): FeaturedAdapter
    }

    private class DiffCallback : DiffUtil.ItemCallback<CoffeePlaceLite>() {
        override fun areItemsTheSame(oldItem: CoffeePlaceLite, newItem: CoffeePlaceLite): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CoffeePlaceLite, newItem: CoffeePlaceLite): Boolean {
            return oldItem == newItem
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.ivImage)
        val title: TextView = view.findViewById(R.id.tvCafeName)
        val rating: TextView = view.findViewById(R.id.tvCafeRating)
        val distance: TextView = view.findViewById(R.id.tvDistance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_featured, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.image.setImageResource(R.drawable.cafe_placeholder)
        item.images?.firstOrNull()?.let { photoMetadata ->
            coroutineScope.launch {
                val uri = coffeePlaceUtilsUseCase.getPhotoUriFromMetadata(photoMetadata, maxWidthDp = 250)
                if (uri != null) {
                    Glide.with(holder.image.context)
                        .load(uri)
                        .placeholder(R.drawable.cafe_placeholder)
                        .into(holder.image)
                } else {
                    holder.image.setImageResource(R.drawable.cafe_placeholder)
                }
            }
        }
        holder.title.text = item.name ?: ""

        // Set rating
        item.rating?.let { rating ->
            val ratingText = String.format("%.1f (%d)", rating, item.ratingCount ?: 0)
            holder.rating.text = ratingText
            holder.rating.visibility = View.VISIBLE
        } ?: run {
            holder.rating.visibility = View.GONE
        }

        // Set distance
        val distance = locationUtil.distanceMeters(currentLocation, item.location)
        val distanceText = locationUtil.distanceAndEtaLabel(distance)
        if (distanceText.isNotEmpty()) {
            holder.distance.text = distanceText
            holder.distance.visibility = View.VISIBLE
        } else {
            holder.distance.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    fun updateItems(newItems: List<CoffeePlaceLite>, userLocation: LatLng? = null) {
        if (userLocation != null) {
            currentLocation = userLocation
        }
        submitList(newItems)
    }
}
