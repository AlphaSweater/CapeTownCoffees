//======================================================================================
//Group 2 - Group Members:
//======================================================================================
//* Chad Fairlie ST10269509
//* Dhiren Ruthenavelu ST10256859
//* Kayla Ferreira ST10259527
//* Nathan Teixeira ST10249266
//======================================================================================
//References:
//======================================================================================
//* ChatGPT was used to guide the structure of this Adapter, including the ViewHolder
//setup, data binding logic, and handling click listeners.
//* Assistance was also provided for optimizing RecyclerView performance and readability.
//* It also helped generate useful comments
//======================================================================================

package com.synaptix.capetowncoffees.ui.lists.listDetails

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
import com.google.android.gms.maps.model.LatLng
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceFull
import com.synaptix.capetowncoffees.domain.usecase.coffeePlace.CoffeePlaceUtilsUseCase
import com.synaptix.capetowncoffees.util.LocationFormattingUtil
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.Locale

class ListDetailsPlacesAdapter @AssistedInject constructor(
    @Assisted private val coroutineScope: CoroutineScope,
    @Assisted private val onItemClicked: (CoffeePlaceFull) -> Unit,
    private val photoResolver: ListDetailsPhotoResolver,
) : ListAdapter<CoffeePlaceFull, ListDetailsPlacesAdapter.VH>(Diff()) {

    private var userLocation: LatLng? = null

    fun setUserLocation(loc: LatLng?) {
        userLocation = loc
        notifyDataSetChanged() // Distance values rely on user location
    }

    @AssistedFactory
    interface Factory {
        fun create(
            coroutineScope: CoroutineScope,
            onItemClicked: (CoffeePlaceFull) -> Unit
        ): ListDetailsPlacesAdapter
    }

    init { setHasStableIds(true) }

    override fun getItemId(position: Int): Long = getItem(position).id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_list_place, parent, false)
        return VH(view, coroutineScope, photoResolver, ::onClick, ::distanceFor)
    }

    override fun onBindViewHolder(holder: VH, position: Int) { holder.bind(getItem(position)) }

    fun submit(items: List<CoffeePlaceFull>) = submitList(items)

    private fun onClick(place: CoffeePlaceFull) = onItemClicked(place)

    private fun distanceFor(place: CoffeePlaceFull): String? {
        val meters = LocationFormattingUtil.distanceMeters(userLocation, place.location)
        if (!meters.isFinite()) return null
        return LocationFormattingUtil.distanceAndEtaLabel(meters)
    }

    class VH(
        itemView: View,
        private val scope: CoroutineScope,
        private val photoResolver: ListDetailsPhotoResolver,
        private val onItemClicked: (CoffeePlaceFull) -> Unit,
        private val distanceProvider: (CoffeePlaceFull) -> String?
    ) : RecyclerView.ViewHolder(itemView) {

        // Correct IDs matching item_list_place.xml
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvAddress: TextView = itemView.findViewById(R.id.tvAddress)
        private val tvRating: TextView = itemView.findViewById(R.id.tvRating)
        private val imageThumb: ImageView = itemView.findViewById(R.id.ivThumb)
        // Optional distance view (could be used later)
        private val tvDistance: TextView? = itemView.findViewById(R.id.tvDistance)

        fun bind(item: CoffeePlaceFull) {
            tvName.text = item.name ?: itemView.context.getString(R.string.unknown)
            tvAddress.text = item.address.orEmpty()

            val rating = item.combinedRating
            val count = item.combinedRatingCount
            tvRating.text = String.format(Locale.getDefault(), "%.1f (%d)", rating, count)

            // Distance label
            val distanceLabel = distanceProvider(item)
            if (distanceLabel != null) {
                tvDistance?.visibility = View.VISIBLE
                tvDistance?.text = distanceLabel
            } else {
                tvDistance?.visibility = View.GONE
            }

            // Placeholder first
            imageThumb.setImageResource(R.drawable.featured_placeholder)

            // Load image (async)
            @Suppress("DEPRECATION")
            scope.launch {
                val url = photoResolver.url(item)
                if (url.isNullOrBlank()) {
                    imageThumb.setImageResource(R.drawable.featured_placeholder)
                } else {
                    Glide.with(imageThumb)
                        .load(url)
                        .thumbnail(0.25f)
                        .placeholder(R.drawable.featured_placeholder)
                        .error(R.drawable.featured_placeholder)
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .into(imageThumb)
                }
            }

            itemView.setOnClickListener { onItemClicked(item) }
            imageThumb.setOnClickListener { onItemClicked(item) }
        }
    }

    private class Diff : DiffUtil.ItemCallback<CoffeePlaceFull>() {
        override fun areItemsTheSame(o: CoffeePlaceFull, n: CoffeePlaceFull) = o.id == n.id
        override fun areContentsTheSame(o: CoffeePlaceFull, n: CoffeePlaceFull) = o == n
    }
}

// helper for images
class ListDetailsPhotoResolver @Inject constructor(
    private val coffeePlaceUtils: CoffeePlaceUtilsUseCase
) {
    suspend fun url(place: CoffeePlaceFull): String? {
        return try {
            coffeePlaceUtils.getPhotoUriFromMetadata(
                photoMetadata   = place.images?.firstOrNull(),
                isCached        = place.isCached,
                cachedImageUrl  = place.cachedImageUrl,
                maxWidthDp      = 500
            )?.toString()
        } catch (t: Throwable) {
            null
        }
    }
}