package com.synaptix.capetowncoffees.ui.home.adapter

import android.view.LayoutInflater
import android.view.View
import android.location.Location
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.net.PlacesClient
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite

class FeaturedAdapter(
    private val placesClient: PlacesClient,
    private var currentLocation: LatLng? = null,
    private val onItemClick: (CoffeePlaceLite) -> Unit = {}
) : ListAdapter<CoffeePlaceLite, FeaturedAdapter.ViewHolder>(DiffCallback()) {

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
        
        // Load image using Places API
        item.images?.firstOrNull()?.let { photoMetadata ->
            val photoRequest = com.google.android.libraries.places.api.net.FetchPhotoRequest.builder(photoMetadata)
                .setMaxWidth(500)
                .build()
                
            placesClient.fetchPhoto(photoRequest).addOnSuccessListener { fetchPhotoResponse ->
                holder.image.setImageBitmap(fetchPhotoResponse.bitmap)
            }.addOnFailureListener {
                holder.image.setImageResource(R.drawable.cafe_placeholder)
            }
        } ?: run {
            holder.image.setImageResource(R.drawable.cafe_placeholder)
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
        val distanceText = calculateDistance(item.location)
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
    
    private fun calculateDistance(latLng: LatLng?): String {
        if (currentLocation == null || latLng == null) return ""
        
        val results = FloatArray(1)
        Location.distanceBetween(
            currentLocation!!.latitude,
            currentLocation!!.longitude,
            latLng.latitude,
            latLng.longitude,
            results
        )
        
        val distanceInKm = results[0] / 1000 // Convert meters to kilometers
        return if (distanceInKm < 1) {
            "${String.format("%.0f", results[0])} m"
        } else {
            "${String.format("%.1f", distanceInKm)} km"
        }
    }

    // getItemCount is provided by ListAdapter
}
