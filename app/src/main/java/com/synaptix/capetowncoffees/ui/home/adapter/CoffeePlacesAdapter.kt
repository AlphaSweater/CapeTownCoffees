package com.synaptix.capetowncoffees.ui.home.adapter

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import java.text.DecimalFormat

/**
 * CoffeePlaceItemAdapter — powers both Near Me and Popular lists.
 * Works with a suspend photo resolver and keeps binds lightweight.
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
                // o.isFavorite != n.isFavorite -> Payload.Fav
                o.rating != n.rating || o.ratingCount != n.ratingCount -> Payload.Rating
                else -> null
            }
        }
    ),
    // 🔒 Namespace stable IDs so Concat adapters never collide
    idProvider = { ID_NAMESPACE xor it.id.hashCode().toLong() }
) {

    /* ╭────────────────────────── Public API ────────────────────────────╮ */

    /** Provided by Fragment so Glide preloader knows the real size. */
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

    /** Efficient updates; distance-only invalidation when location changes. */
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

    /* ╰──────────────────────────────────────────────────────────────────╯ */
    /* ╭───────────────────────── Adapter Wiring ─────────────────────────╮ */

    override fun itemViewTypeFor(position: Int): Int = R.layout.item_coffee_near_me

    override fun onCreateBinding(
        inflater: LayoutInflater,
        parent: ViewGroup
    ): ItemCoffeeNearMeBinding = ItemCoffeeNearMeBinding.inflate(inflater, parent, false)

    override fun onCreateVH(binding: ItemCoffeeNearMeBinding) =
        RowVH(
            binding = binding,
            locationUtil = locationUtil,
            coffeePlaceUtils = coffeePlaceUtils,
            showPopularChip = showPopularChip,
            onClick = ::emitClick,
            getCurrentLocation = { currentLocation }
        ).also { vh ->
            // Preloader gets the measured size ONCE per holder
            preloadSizeProvider?.setView(vh.vb.ivImage)
        }

    private fun emitClick(click: Click) = onClick(click)

    /* ╰──────────────────────────────────────────────────────────────────╯ */
    /* ╭──────────────────────────── ViewHolder ───────────────────────────╮ */

    class RowVH(
        binding: ItemCoffeeNearMeBinding,
        private val locationUtil: LocationUtil,
        private val coffeePlaceUtils: CoffeePlaceUtilsUseCase,
        private val showPopularChip: Boolean,
        private val onClick: (Click) -> Unit,
        private val getCurrentLocation: () -> LatLng?,
    ) : BaseViewHolder<CoffeePlaceLite, ItemCoffeeNearMeBinding>(binding) {

        // One scope per holder (cheap). Cancel on recycle/detach.
        private val job = SupervisorJob()
        private val scope = CoroutineScope(Dispatchers.Main.immediate + job)

        private var bindToken: Int = 0 // prevents late image writes after rebinding
        private var boundItem: CoffeePlaceLite? = null

        init {
            // Static click hookups (no per-bind allocations)
            vb.root.setOnClickListener { boundItem?.let { onClick(Click.Open(it.id)) } }
            vb.ivImage.setOnClickListener { boundItem?.let { onClick(Click.Open(it.id)) } }
            vb.btnAddToList.setOnClickListener { boundItem?.let { onClick(Click.AddToList(it.id)) } }
            // vb.btnFavorite.setOnClickListener { boundItem?.let { onClick(Click.ToggleFavorite(it.id, !vb.btnFavorite.isChecked)) } }
        }

        override fun onAttached() {
            // If you ever pause/resume animations or preloading, hook here.
        }

        override fun onDetached() {
            // Keep scope alive; Glide cancels via clear() in onRecycled().
        }

        override fun onRecycled() {
            // Cancel any in-flight work for this holder
            job.cancelChildren()
            Glide.with(vb.ivImage).clear(vb.ivImage)
            boundItem = null
        }

        override fun bind(item: CoffeePlaceLite) {
            boundItem = item
            bindToken++ // new generation for this holder
            val tokenAtBind = bindToken

            with(vb) {
                // Popular/Featured chip
                pillPopular.isVisible = showPopularChip

                // Name
                tvCafeName.text = item.name.orEmpty()
                tvCafeName.contentDescription = item.name.orEmpty()

                // Address
                renderAddress(item.address)

                // Distance
                renderDistance(item)

                // Rating
                renderRating(item.rating, item.ratingCount)

                // Price (not in use yet)
                tvCafePrice.visibility = View.GONE

                // Reset image to placeholder while resolving URL
                ivImage.setImageResource(R.drawable.featured_placeholder)
                ivImage.contentDescription = item.name?.let { "$it photo" }
                    ?: root.context.getString(R.string.coffee_image)
            }

            // Resolve photo URL asynchronously (suspend util)
            scope.launch {
                val url = try {
                    item.images?.firstOrNull()
                        ?.let { meta -> coffeePlaceUtils.getPhotoUriFromMetadata(meta, maxWidthDp = 500) }
                        ?.toString()
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Timber.tag(TAG).d("photo-resolve-fail id=%s err=%s", item.id, t.message)
                    null
                }

                // If holder has been rebound since we launched, abort
                if (tokenAtBind != bindToken) return@launch

                with(vb) { bindImage(url, item.id) }
            }
        }

        override fun bind(item: CoffeePlaceLite, payloads: List<Any>) {
            if (payloads.isEmpty()) { bind(item); return }
            with(vb) {
                payloads.forEach { p ->
                    when (p) {
                        Payload.Rating   -> renderRating(item.rating, item.ratingCount)
                        Payload.Distance -> renderDistance(item)
                    }
                }
            }
        }

        /* ────────────────────── Render helpers (fast) ─────────────────── */

        private fun ItemCoffeeNearMeBinding.renderAddress(address: String?) {
            tvCafeAddress.isGone = address.isNullOrBlank()
            if (!address.isNullOrBlank()) tvCafeAddress.text = address
        }

        private fun ItemCoffeeNearMeBinding.renderDistance(item: CoffeePlaceLite) {
            val meters = locationUtil.distanceMeters(getCurrentLocation(), item.location)
            val label = if (meters < 1) null else locationUtil.distanceAndEtaLabel(meters)
            tvDistance.isGone = label.isNullOrBlank()
            if (!label.isNullOrBlank()) tvDistance.text = label
        }

        private fun ItemCoffeeNearMeBinding.renderRating(rating: Double?, count: Int?) {
            val label = rating?.let { r -> "${RATING_FMT.format(r)} (${count ?: 0})" }
            tvCafeRating.isGone = label.isNullOrBlank()
            if (!label.isNullOrBlank()) tvCafeRating.text = label
        }

        private fun ItemCoffeeNearMeBinding.bindImage(url: String?, id: String) {
            if (url == null) {
                // Timber.tag(TAG).d("no-url id=%s", id)
                return
            }

            // Timber.tag(TAG).d("load-start id=%s url=%s", id, url)

            Glide.with(ivImage)
                .load(url)
                .thumbnail(0.25f)
                .placeholder(R.drawable.featured_placeholder)
                .error(R.drawable.featured_placeholder)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .centerCrop()
                .listener(GLIDE_LOGGER(id))
                .into(ivImage)
        }
    }

    /* ╰──────────────────────────────────────────────────────────────────╯ */
    /* ╭──────────────────────────── Internals ────────────────────────────╮ */

    private sealed interface Payload {
        data object Fav : Payload
        data object Rating : Payload
        data object Distance : Payload
    }

    private companion object {
        const val TAG = "CTC-IMG"
        private const val ID_NAMESPACE: Long = 0x10_0000_0000L
        private val RATING_FMT = DecimalFormat("0.0")

        // Reusable lightweight Glide listener (no big allocations per bind)
        private fun GLIDE_LOGGER(id: String) = object : RequestListener<Drawable> {
            override fun onLoadFailed(
                e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean
            ): Boolean {
                // Timber.tag(TAG).d("load-fail id=%s url=%s err=%s", id, model, e?.localizedMessage)
                return false
            }

            override fun onResourceReady(
                resource: Drawable?, model: Any?, target: Target<Drawable>?,
                dataSource: DataSource, isFirstResource: Boolean
            ): Boolean {
                // Timber.tag(TAG).d("load-ok   id=%s src=%s first=%s", id, dataSource, isFirstResource)
                return false
            }
        }
    }
}