package com.synaptix.capetowncoffees.ui.home.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * CoffeePlaceItemAdapter — powers both Near Me and Popular lists.
 */
class CoffeePlaceItemAdapter @AssistedInject constructor(
    @Assisted private var currentLocation: LatLng? = null,
    @Assisted private val onClick: (Click) -> Unit,
    @Assisted private val showPopularChip: Boolean,
    private val locationUtil: LocationUtil,
    private val coffeePlaceUtils: CoffeePlaceUtilsUseCase,
) : BaseAdapter<CoffeePlaceLite, ItemCoffeeNearMeBinding>(
    diff = simpleDiff(
        sameItem = { o, n -> o.id == n.id },
        sameContent = { o, n -> o == n },
        payload = { o, n ->
            when {
                // o.isFavorite != n.isFavorite -> PAYLOAD_FAV  // when available
                o.rating != n.rating || o.ratingCount != n.ratingCount -> PAYLOAD_RATING
                else -> null
            }
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
            onClick: (Click) -> Unit,
            showPopularChip: Boolean
        ): CoffeePlaceItemAdapter
    }

    private companion object {
        const val PAYLOAD_FAV = "payload_fav"
        const val PAYLOAD_RATING = "payload_rating"
        const val PAYLOAD_DISTANCE = "payload_distance"
    }

    fun updateItems(items: List<CoffeePlaceLite>, userLocation: LatLng? = null) {
        val locationChanged = userLocation != null && userLocation != currentLocation
        if (userLocation != null) currentLocation = userLocation
        submitList(items) {
            if (locationChanged && itemCount > 0) {
                notifyItemRangeChanged(0, itemCount, PAYLOAD_DISTANCE)
            }
        }
    }

    override fun onCreateBinding(
        inflater: LayoutInflater,
        parent: ViewGroup
    ): ItemCoffeeNearMeBinding = ItemCoffeeNearMeBinding.inflate(inflater, parent, false)

    override fun onCreateVH(binding: ItemCoffeeNearMeBinding) =
        object : BaseViewHolder<CoffeePlaceLite, ItemCoffeeNearMeBinding>(binding) {

            private var rowJob: Job? = null

            private fun makeRowScope(): CoroutineScope {
                rowJob?.cancel()
                rowJob = SupervisorJob()
                return CoroutineScope(Dispatchers.Main.immediate + rowJob!!)
            }

            private inline fun safeClick(crossinline action: () -> Unit) {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) action()
            }

            override fun bind(item: CoffeePlaceLite) = with(vb) {
                val rowScope = makeRowScope()

                // Popular/Featured chip
                pillPopular.isVisible = showPopularChip

                // Name
                tvCafeName.text = item.name.orEmpty()
                tvCafeName.contentDescription = item.name.orEmpty()

                // Address
                val address = item.address
                tvCafeAddress.isGone = address.isNullOrBlank()
                if (!address.isNullOrBlank()) {
                    tvCafeAddress.text = address
                }

                // Distance (null-safe)
                val distanceLabel = run {
                    val meters = locationUtil.distanceMeters(currentLocation, item.location)
                    if (meters < 1) return@run null
                    locationUtil.distanceAndEtaLabel(meters).takeIf { it.isNotEmpty() }
                }
                tvDistance.isGone = distanceLabel.isNullOrBlank()
                if (!distanceLabel.isNullOrBlank()) tvDistance.text = distanceLabel

                // Rating
                val ratingLabel = run {
                    val r = item.rating ?: return@run null
                    val c = item.ratingCount ?: 0
                    String.format(Locale.getDefault(), "%.1f (%d)", r, c)
                }
                tvCafeRating.isGone = ratingLabel.isNullOrBlank()
                if (!ratingLabel.isNullOrBlank()) tvCafeRating.text = ratingLabel

                // Price (not in use yet)
                tvCafePrice.visibility = View.GONE

                // Image
                ivImage.setImageResource(R.drawable.featured_placeholder)
                ivImage.contentDescription = item.name?.let { "$it photo" }
                    ?: root.context.getString(R.string.coffee_image)

                rowScope.launch {
                    val url = run {
                        val meta = item.images?.firstOrNull() ?: return@run null
                        coffeePlaceUtils.getPhotoUriFromMetadata(meta, maxWidthDp = 500)?.toString()
                    }
                    if (url != null) {
                        Glide.with(ivImage)
                            .load(url)
                            .thumbnail(0.25f)
                            .placeholder(R.drawable.featured_placeholder)
                            .error(R.drawable.featured_placeholder)
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.AUTOMATIC)
                            .centerCrop()
                            .into(ivImage)
                    }
                }

                // Clicks
                root.setOnClickListener { safeClick { onClick(Click.Open(item.id)) } }
                ivImage.setOnClickListener { safeClick { onClick(Click.Open(item.id)) } }

                btnFavorite.setOnClickListener {
                    Toast.makeText(root.context, "Not implemented", Toast.LENGTH_SHORT).show()
                }
                btnAddToList.setOnClickListener { safeClick { onClick(Click.AddToList(item.id)) } }
            }

            override fun bind(item: CoffeePlaceLite, payloads: List<Any>) = with(vb) {
                when {
                    payloads.contains(PAYLOAD_FAV) -> {
                        // btnFavorite.isChecked = item.isFavorite
                    }
                    payloads.contains(PAYLOAD_RATING) -> {
                        val ratingLabel = run {
                            val r = item.rating ?: return@run null
                            val c = item.ratingCount ?: 0
                            String.format(Locale.getDefault(), "%.1f (%d)", r, c)
                        }
                        tvCafeRating.isGone = ratingLabel.isNullOrBlank()
                        if (!ratingLabel.isNullOrBlank()) tvCafeRating.text = ratingLabel
                    }
                    payloads.contains(PAYLOAD_DISTANCE) -> {
                        val distanceLabel = run {
                            val meters = locationUtil.distanceMeters(currentLocation, item.location)
                            if (meters < 1) return@run null
                            locationUtil.distanceAndEtaLabel(meters).takeIf { it.isNotEmpty() }
                        }
                        tvDistance.isGone = distanceLabel.isNullOrBlank()
                        if (!distanceLabel.isNullOrBlank()) tvDistance.text = distanceLabel
                    }
                    else -> bind(item)
                }
            }

            override fun onRecycled() { // IMPORTANT: now overrides base hook
                rowJob?.cancel()
                rowJob = null
                Glide.with(vb.ivImage).clear(vb.ivImage)
            }
        }
}