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

package com.synaptix.capetowncoffees.ui.home.adapter

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
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
import com.synaptix.capetowncoffees.util.LocationFormattingUtil
import com.synaptix.capetowncoffees.util.LocationUtil
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.DecimalFormat

// ─────────── Adapter ───────────
// Renders compact coffee place cards; supports distance updates and image preloading.
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
                o.combinedRating != n.combinedRating || o.combinedRatingCount != n.combinedRatingCount -> Payload.Rating
                else -> null
            }
        }
    ),
    idProvider = { ID_NAMESPACE xor it.id.hashCode().toLong() }
) {

    // ─────────── Public API ───────────
    // Set by the Fragment once per holder so Glide can compute correct preload sizes.
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

    // Efficient updates; when only location changes we invalidate distance labels via payload.
    fun updateItems(items: List<CoffeePlaceLite>, userLocation: LatLng? = null) {
        val locationChanged = userLocation != null && userLocation != currentLocation
        if (userLocation != null) currentLocation = userLocation
        submitList(items) {
            if (locationChanged && itemCountFast > 0) {
                Timber.tag(TAG).d("Distance payload refresh for %d items", itemCountFast)
                notifyItemRangeChanged(0, itemCountFast, Payload.Distance)
            }
        }
    }

    // ─────────── Adapter Wiring ───────────
    override fun itemViewTypeFor(position: Int): Int = R.layout.item_coffee_near_me

    override fun onCreateBinding(inflater: LayoutInflater, parent: ViewGroup): ItemCoffeeNearMeBinding =
        ItemCoffeeNearMeBinding.inflate(inflater, parent, false)

    override fun onCreateVH(binding: ItemCoffeeNearMeBinding) =
        RowVH(
            binding = binding,
            locationUtil = locationUtil,
            coffeePlaceUtils = coffeePlaceUtils,
            showPopularChip = showPopularChip,
            onClick = ::emitClick,
            getCurrentLocation = { currentLocation }
        ).also { vh ->
            preloadSizeProvider?.setView(vh.vb.ivImage)
        }

    private fun emitClick(click: Click) = onClick(click)

    // ─────────── ViewHolder ───────────
    // Keeps a per-holder scope for image/url work; cancels on recycle to prevent leaks.
    class RowVH(
        binding: ItemCoffeeNearMeBinding,
        private val locationUtil: LocationUtil,
        private val coffeePlaceUtils: CoffeePlaceUtilsUseCase,
        private val showPopularChip: Boolean,
        private val onClick: (Click) -> Unit,
        private val getCurrentLocation: () -> LatLng?,
    ) : BaseViewHolder<CoffeePlaceLite, ItemCoffeeNearMeBinding>(binding) {

        private val job = SupervisorJob()
        private val scope = CoroutineScope(Dispatchers.Main.immediate + job)

        private var bindToken: Int = 0           // generation counter to ignore stale async writes
        private var boundItem: CoffeePlaceLite? = null

        init {
            vb.root.setOnClickListener { boundItem?.let { onClick(Click.Open(it.id)) } }
            vb.ivImage.setOnClickListener { boundItem?.let { onClick(Click.Open(it.id)) } }
            vb.btnAddToList.setOnClickListener { boundItem?.let { onClick(Click.AddToList(it.id)) } }
        }

        override fun onAttached() = Unit
        override fun onDetached() = Unit

        override fun onRecycled() {
            job.cancelChildren()
            Glide.with(vb.ivImage).clear(vb.ivImage)
            boundItem = null
        }

        // Full bind: fill static text, compute distance, resolve image url.
        override fun bind(item: CoffeePlaceLite) {
            boundItem = item
            bindToken++
            val tokenAtBind = bindToken

            with(vb) {
                pillPopular.isVisible = showPopularChip

                tvCafeName.text = item.name.orEmpty()
                tvCafeName.contentDescription = item.name.orEmpty()

                renderAddress(item.address)
                renderDistance(item)
                renderRating(item.combinedRating, item.combinedRatingCount)

                tvCafePrice.visibility = View.GONE

                ivImage.setImageResource(R.drawable.featured_placeholder)
                ivImage.contentDescription = item.name?.let { "$it photo" }
                    ?: root.context.getString(R.string.coffee_image)
            }

            // If it's not cached and we also have no images, bail early.
            if (!item.isCached && item.images.isNullOrEmpty()) {
                vb.bindImage(null, item.id) // optional; keeps placeholder
                return
            }

            scope.launch {
                val url = try {
                    val uri = coffeePlaceUtils.getPhotoUriFromMetadata(
                        photoMetadata   = item.images?.firstOrNull(),
                        isCached        = item.isCached,
                        cachedImageUrl  = item.cachedImageUrl,
                        maxWidthDp      = 500
                    )
                    uri?.toString()
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Timber.tag(TAG).d("photo-resolve-fail id=%s err=%s", item.id, t.message)
                    null
                }

                // ViewHolder reused for another item? bail out
                if (tokenAtBind != bindToken) return@launch

                vb.bindImage(url, item.id) // url may be null -> placeholder stays
            }
        }

        // Partial bind: respond to targeted payloads only.
        override fun bind(item: CoffeePlaceLite, payloads: List<Any>) {
            if (payloads.isEmpty()) { bind(item); return }
            payloads.forEach { p ->
                when (p) {
                    Payload.Rating   -> vb.renderRating(item.combinedRating, item.combinedRatingCount)
                    Payload.Distance -> vb.renderDistance(item)
                }
            }
        }

        // ─────────── Render Helpers ───────────
        // Keep these cheap; they run often during scroll.
        private fun ItemCoffeeNearMeBinding.renderAddress(address: String?) {
            tvCafeAddress.isGone = address.isNullOrBlank()
            if (!address.isNullOrBlank()) tvCafeAddress.text = address
        }

        private fun ItemCoffeeNearMeBinding.renderDistance(item: CoffeePlaceLite) {
            val meters = LocationFormattingUtil.distanceMeters(getCurrentLocation(), item.location)
            val label = if (meters < 1) null else LocationFormattingUtil.distanceAndEtaLabel(meters)
            tvDistance.isGone = label.isNullOrBlank()
            if (!label.isNullOrBlank()) tvDistance.text = label
        }

        private fun ItemCoffeeNearMeBinding.renderRating(rating: Double?, count: Int?) {
            val label = rating?.let { r -> "${RATING_FMT.format(r)} (${count ?: 0})" }
            tvCafeRating.isGone = label.isNullOrBlank()
            if (!label.isNullOrBlank()) tvCafeRating.text = label
        }

        private fun ItemCoffeeNearMeBinding.bindImage(url: String?, id: String) {
            if (url == null) return
            Glide.with(ivImage)
                .load(url)
                .thumbnail(0.25f)
                .placeholder(R.drawable.featured_placeholder)
                .error(R.drawable.featured_placeholder)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .centerCrop()
                .listener(glideLogger(id))
                .into(ivImage)
        }
    }

    // ─────────── Internals ───────────
    private sealed interface Payload {
        data object Fav : Payload
        data object Rating : Payload
        data object Distance : Payload
    }

    private companion object {
        private const val TAG = "CTC-IMG"
        private const val ID_NAMESPACE: Long = 0x10_0000_0000L
        private val RATING_FMT = DecimalFormat("0.0")

        private fun glideLogger(id: String) = object : RequestListener<Drawable> {
            override fun onLoadFailed(
                e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean
            ): Boolean = false

            override fun onResourceReady(
                resource: Drawable?, model: Any?, target: Target<Drawable>?,
                dataSource: DataSource, isFirstResource: Boolean
            ): Boolean = false
        }
    }
}
