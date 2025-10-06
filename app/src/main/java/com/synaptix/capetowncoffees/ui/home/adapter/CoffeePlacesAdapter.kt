package com.synaptix.capetowncoffees.ui.home.adapter

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.util.ViewPreloadSizeProvider
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
import kotlinx.coroutines.*
import timber.log.Timber
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
                // o.isFavorite != n.isFavorite -> PAYLOAD_FAV
                o.rating != n.rating || o.ratingCount != n.ratingCount -> PAYLOAD_RATING
                else -> null
            }
        }
    ),
    // 🔒 Namespace stable IDs to avoid any chance of collisions across adapters
    idProvider = { ID_NAMESPACE xor it.id.hashCode().toLong() }
) {
    // 🔒 Distinct viewType (layout id) so Concat never mixes holders
    override fun itemViewTypeFor(position: Int): Int = R.layout.item_coffee_near_me

    /** Injected from the Fragment after construction so AssistedInject stays simple. */
    var preloadSizeProvider: ViewPreloadSizeProvider<String>? = null

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
        // Payload keys
        const val PAYLOAD_FAV = "payload_fav"
        const val PAYLOAD_RATING = "payload_rating"
        const val PAYLOAD_DISTANCE = "payload_distance"
        const val TAG = "CTC-IMG"

        // Any unique salt works; just keep it constant for this adapter.
        private const val ID_NAMESPACE: Long = 0x10_0000_0000L  // high-bit salt
    }

    fun updateItems(items: List<CoffeePlaceLite>, userLocation: LatLng? = null) {
        val locationChanged = userLocation != null && userLocation != currentLocation
        if (userLocation != null) currentLocation = userLocation
        submitList(items) {
            if (locationChanged && itemCount > 0) {
                Timber.tag(TAG).d("Distance payload refresh for %d items", itemCount)
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
                Timber.tag(TAG).d("bind id=%s pos=%d", item.id, bindingAdapterPosition)
                val rowScope = makeRowScope()

                // Give the preloader the actual ImageView so it knows the exact size.
                preloadSizeProvider?.setView(ivImage)

                // Popular/Featured chip
                pillPopular.isVisible = showPopularChip

                // Name
                tvCafeName.text = item.name.orEmpty()
                tvCafeName.contentDescription = item.name.orEmpty()

                // Address
                val address = item.address
                tvCafeAddress.isGone = address.isNullOrBlank()
                if (!address.isNullOrBlank()) tvCafeAddress.text = address

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

                    if (url == null) {
                        Timber.tag(TAG).d("no-url id=%s (no photo metadata or resolver returned null)", item.id)
                        return@launch
                    }

                    Timber.tag(TAG).d("load-start id=%s url=%s", item.id, url)

                    Glide.with(ivImage)
                        .load(url)
                        .thumbnail(0.25f)
                        .placeholder(R.drawable.featured_placeholder)
                        .error(R.drawable.featured_placeholder)
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .centerCrop()
                        .listener(object : RequestListener<Drawable> {
                            override fun onLoadFailed(
                                e: GlideException?,
                                model: Any?,
                                target: Target<Drawable>?,
                                isFirstResource: Boolean
                            ): Boolean {
                                Timber.tag(TAG).d(
                                    "load-fail id=%s url=%s err=%s",
                                    item.id, model, e?.localizedMessage
                                )
                                return false
                            }

                            override fun onResourceReady(
                                resource: Drawable?,
                                model: Any?,
                                target: Target<Drawable>?,
                                dataSource: DataSource,
                                isFirstResource: Boolean
                            ): Boolean {
                                Timber.tag(TAG).d(
                                    "load-ok   id=%s src=%s first=%s",
                                    item.id, dataSource, isFirstResource
                                )
                                return false
                            }
                        })
                        .into(ivImage)
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
                        Timber.tag(TAG).d("payload-fav id=%s", item.id)
                        // btnFavorite.isChecked = item.isFavorite
                    }
                    payloads.contains(PAYLOAD_RATING) -> {
                        Timber.tag(TAG).d("payload-rating id=%s", item.id)
                        val ratingLabel = run {
                            val r = item.rating ?: return@run null
                            val c = item.ratingCount ?: 0
                            String.format(Locale.getDefault(), "%.1f (%d)", r, c)
                        }
                        tvCafeRating.isGone = ratingLabel.isNullOrBlank()
                        if (!ratingLabel.isNullOrBlank()) tvCafeRating.text = ratingLabel
                    }
                    payloads.contains(PAYLOAD_DISTANCE) -> {
                        Timber.tag(TAG).d("payload-distance id=%s", item.id)
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

            override fun onRecycled() {
                Timber.tag(TAG).d("recycled pos=%d", bindingAdapterPosition)
                rowJob?.cancel()
                rowJob = null
                Glide.with(vb.ivImage).clear(vb.ivImage)
            }
        }
}
