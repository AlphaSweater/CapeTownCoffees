package com.synaptix.capetowncoffees.ui.home.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.libraries.places.api.net.PlacesClient
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite

import android.location.Location
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.google.android.gms.maps.model.LatLng

class NearMeAdapter(
    private val placesClient: PlacesClient,
    private var currentLocation: LatLng? = null,
    private val onItemClick: (CoffeePlaceLite) -> Unit = {}
) : ListAdapter<CoffeePlaceLite, NearMeAdapter.ViewHolder>(DiffCallback()) {
    
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
        val name: TextView = view.findViewById(R.id.tvCafeName)
        val address: TextView = view.findViewById(R.id.tvCafeAddress)
        val rating: TextView = view.findViewById(R.id.tvCafeRating)
        val distance: TextView = view.findViewById(R.id.tvDistance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_near_me, parent, false)
        return ViewHolder(view)
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
            "${String.format("%.0f", results[0])} m away"
        } else {
            "${String.format("%.1f", distanceInKm)} km away"
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cafe = getItem(position)
        
        // Debug logging for Signature Cafe
        val isSignatureCafe = cafe.name?.contains("Signature Cafe", ignoreCase = true) == true
        if (isSignatureCafe) {
            android.util.Log.d("NearMeAdapter", "Processing Signature Cafe - Images: ${cafe.images?.size ?: 0}")
            cafe.images?.forEachIndexed { index, photo ->
                android.util.Log.d("NearMeAdapter", "Signature Cafe Image $index: $photo")
            }
        }

        // Load image using Places API with detailed error handling
        cafe.images?.firstOrNull()?.let { photoMetadata ->
            try {
                if (isSignatureCafe) {
                    android.util.Log.d("NearMeAdapter", "Attempting to load image for Signature Cafe: $photoMetadata")
                }
                
                val photoRequest = com.google.android.libraries.places.api.net.FetchPhotoRequest.builder(photoMetadata)
                    .setMaxWidth(500)
                    .build()
                
                if (isSignatureCafe) {
                    android.util.Log.d("NearMeAdapter", "Created photo request for Signature Cafe")
                }
                
                placesClient.fetchPhoto(photoRequest).addOnSuccessListener { fetchPhotoResponse ->
                    if (fetchPhotoResponse.bitmap != null) {
                        if (isSignatureCafe) {
                            android.util.Log.d("NearMeAdapter", "Successfully loaded bitmap for Signature Cafe")
                        }
                        holder.image.setImageBitmap(fetchPhotoResponse.bitmap)
                        android.util.Log.d("NearMeAdapter", "Successfully loaded image for ${cafe.name}")
                    } else {
                        val errorMsg = if (isSignatureCafe) "Received null bitmap for Signature Cafe" else "Received null bitmap for ${cafe.name}"
                        android.util.Log.e("NearMeAdapter", errorMsg)
                        holder.image.setImageResource(R.drawable.cafe_placeholder)
                    }
                }.addOnFailureListener { exception ->
                    val errorMsg = if (isSignatureCafe) "Failed to load image for Signature Cafe" else "Failed to load image for ${cafe.name}"
                    android.util.Log.e("NearMeAdapter", "$errorMsg: ${exception.message}", exception)
                    holder.image.setImageResource(R.drawable.cafe_placeholder)
                }
            } catch (e: Exception) {
                val errorMsg = if (isSignatureCafe) "Error creating photo request for Signature Cafe" else "Error creating photo request for ${cafe.name}"
                android.util.Log.e("NearMeAdapter", "$errorMsg: ${e.message}", e)
                holder.image.setImageResource(R.drawable.cafe_placeholder)
            }
        } ?: run {
            android.util.Log.w("NearMeAdapter", "No photo metadata available for ${cafe.name}")
            holder.image.setImageResource(R.drawable.cafe_placeholder)
        }
        
        holder.name.text = cafe.name ?: ""
        
        // Set distance
        val distanceText = calculateDistance(cafe.location)
        holder.distance.text = distanceText
        
        // Set rating
        cafe.rating?.let { rating ->
            val ratingText = String.format("%.1f (%d)", rating, cafe.ratingCount ?: 0)
            holder.rating.text = ratingText
            holder.rating.visibility = View.VISIBLE
        } ?: run {
            holder.rating.visibility = View.GONE
        }
        
        cafe.address?.let { address ->
            holder.address.text = address
        } ?: run {
            holder.address.visibility = View.GONE
        }
        
        holder.itemView.setOnClickListener {
            onItemClick(cafe)
        }
    }
    // getItemCount is provided by ListAdapter
}
