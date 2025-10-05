package com.synaptix.capetowncoffees.ui.home.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.google.android.gms.maps.model.LatLng
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.databinding.ItemCoffeeNearMeBinding
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite
import com.synaptix.capetowncoffees.domain.usecase.coffeePlace.CoffeePlaceUtilsUseCase
import com.synaptix.capetowncoffees.ui.common.BaseAdapter
import com.synaptix.capetowncoffees.ui.common.BaseViewHolder
import com.synaptix.capetowncoffees.ui.common.simpleDiff
import com.synaptix.capetowncoffees.util.LocationUtil
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * NearMeItemAdapter — built on the project's BaseAdapter kit.
 *
 * Layout: item_coffee_near_me.xml (ItemCoffeeNearMeBinding)
 * Domain Model:  CoffeePlaceLite
 * Clicks: Open(cafe), ToggleFavorite(id), AddToList(id)
 *
 * Notes:
 * - Uses payloads (PAYLOAD_FAV) if we later add `isFavorite` to CoffeePlaceLite.
 * - Image is loaded via CoffeePlaceUtilsUseCase.getPhotoUriFromMetadata() like before.
 */

// 1) Click contract (one callback supports all actions on the row)
class NearMeItemAdapter @AssistedInject constructor(
    // Per-screen params via Assisted:
    @Assisted private var currentLocation: LatLng? = null,
    @Assisted private val coroutineScope: CoroutineScope,
    @Assisted private val onClick: (Click) -> Unit,
    // Inject small helpers (keeps Fragment clean):
    private val distanceFormatter: DistanceFormatter,
    private val ratingFormatter: RatingFormatter,
    private val photoResolver: PhotoResolver,
) : BaseAdapter<CoffeePlaceLite, ItemCoffeeNearMeBinding>(
    diff = simpleDiff(
        sameItem = { o, n -> o.id == n.id },
        sameContent = { o, n -> o == n },
        payload = { o, n ->
            //TODO: Uncomment when we add a favorite flag to domain model
            if (o.isFavorite != n.isFavorite) PAYLOAD_FAV else null
            null
        }
    ),
    idProvider = { it.id.hashCode().toLong() }
) {

    sealed interface Click {
        data class Open(val id: String) : Click
        data class ToggleFavorite(val id: String, val newValue: Boolean) : Click
        data class AddToList(val id: String) : Click
    }

    @AssistedFactory
    interface Factory {
        fun create(
            currentLocation: LatLng?,
            coroutineScope: CoroutineScope,
            onClick: (Click) -> Unit
        ): NearMeItemAdapter
    }

    private companion object { const val PAYLOAD_FAV = "payload_fav" }

    // Convenience API (optional)
    fun updateItems(items: List<CoffeePlaceLite>, userLocation: LatLng? = null) {
        if (userLocation != null) currentLocation = userLocation
        submitList(items)
    }

    override fun onCreateBinding(
        inflater: LayoutInflater,
        parent: ViewGroup
    ): ItemCoffeeNearMeBinding = ItemCoffeeNearMeBinding.inflate(inflater, parent, false)

    override fun onCreateVH(binding: ItemCoffeeNearMeBinding) =
        object : BaseViewHolder<CoffeePlaceLite, ItemCoffeeNearMeBinding>(binding) {

            override fun bind(item: CoffeePlaceLite) = with(vb) {
                val id = item.id // guard: adapter needs id for clicks

                // --- Name
                tvCafeName.text = item.name.orEmpty()

                // --- Address (hide if blank)
                val address = item.address
                if (address.isNullOrBlank()) {
                    tvCafeAddress.isGone = true
                } else {
                    tvCafeAddress.isVisible = true
                    tvCafeAddress.text = address
                }

                // --- Distance (computed with LocationUtil via DistanceFormatter)
                val distanceLabel = distanceFormatter.label(currentLocation, item.location)
                if (distanceLabel.isNullOrBlank()) {
                    tvDistance.isGone = true
                } else {
                    tvDistance.isVisible = true
                    tvDistance.text = distanceLabel
                }

                // --- Rating
                val ratingLabel = ratingFormatter.label(item.rating, item.ratingCount)
                if (ratingLabel.isNullOrBlank()) {
                    tvCafeRating.isGone = true
                } else {
                    tvCafeRating.isVisible = true
                    tvCafeRating.text = ratingLabel
                }

                // --- Price (optional; hide by default for now)
                tvCafePrice.visibility = View.GONE

                // --- Image
                ivImage.setImageResource(R.drawable.featured_placeholder)
                coroutineScope.launch {
                    val url = photoResolver.url(item)
                    if (url != null) {
                        Glide.with(ivImage)
                            .load(url)
                            .thumbnail(0.25f)                     // render faster preview
                            .placeholder(R.drawable.featured_placeholder)
                            .error(R.drawable.featured_placeholder)
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.AUTOMATIC)
                            .into(ivImage)
                    } else {
                        ivImage.setImageResource(R.drawable.featured_placeholder)
                    }
                }

                // --- Favorite
                // btnFavorite.isChecked = item.isFavorite  // TODO: enable when domain adds it
                // btnFavorite.isChecked = false

                // --- Clicks
                root.setOnClickListener { onClick(Click.Open(id)) }
                ivImage.setOnClickListener { onClick(Click.Open(id)) }
                btnFavorite.setOnClickListener {
//                    val newValue = btnFavorite.isChecked
//                    onClick(Click.ToggleFavorite(id, newValue))
//                    item.isFavorite = newValue // update cached value
//                    btnFavorite.isChecked = newValue
                    Toast.makeText(root.context, "Not implemented", Toast.LENGTH_SHORT).show()
                }
                btnAddToList.setOnClickListener { onClick(Click.AddToList(id)) }
            }

            override fun bind(item: CoffeePlaceLite, payloads: List<Any>) {
                if (payloads.contains(PAYLOAD_FAV)) {
                    // vb.btnFavorite.isChecked = item.isFavorite
                } else bind(item)
            }
        }
}

/* ─────────────────────────────────────────
 * Helpers for formatting parts of the UI
 * ───────────────────────────────────────── */

class DistanceFormatter @Inject constructor(
    private val locationUtil: LocationUtil
) {
    fun label(user: LatLng?, place: LatLng?): String? {
        val meters = locationUtil.distanceMeters(user, place)
        return locationUtil.distanceAndEtaLabel(meters).takeIf { it.isNotEmpty() }
    }
}

class RatingFormatter @Inject constructor() {
    fun label(rating: Double?, count: Int?): String? =
        rating?.let { String.format("%.1f (%d)", it, count ?: 0) }
}

class PhotoResolver @Inject constructor(
    private val coffeePlaceUtils: CoffeePlaceUtilsUseCase
) {
    /** Returns a direct URL (or null) for the first photo of a place. */
    suspend fun url(place: CoffeePlaceLite): String? {
        val meta = place.images?.firstOrNull() ?: return null
        return coffeePlaceUtils.getPhotoUriFromMetadata(meta, maxWidthDp = 500)?.toString()
    }
}