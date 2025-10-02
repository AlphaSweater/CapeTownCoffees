package com.synaptix.capetowncoffees.ui.home.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.libraries.places.api.net.PlacesClient
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite

class FeaturedAdapter(
    private var items: List<CoffeePlaceLite> = emptyList(),
    private val placesClient: PlacesClient,
    private val onItemClick: (CoffeePlaceLite) -> Unit = {}
) : RecyclerView.Adapter<FeaturedAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.ivFeaturedImage)
        val title: TextView = view.findViewById(R.id.tvCafeName)
        val rating: TextView = view.findViewById(R.id.tvCafeRating)
        val distance: TextView = view.findViewById(R.id.tvFeaturedDistance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_featured, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        
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
        
        item.rating?.let { rating ->
            val ratingText = String.format("%.1f (%d)", rating, item.ratingCount ?: 0)
            holder.rating.text = ratingText
        }
        
        // Hide distance if not available
        holder.distance.visibility = View.GONE
        
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    fun updateItems(newItems: List<CoffeePlaceLite>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size
}
