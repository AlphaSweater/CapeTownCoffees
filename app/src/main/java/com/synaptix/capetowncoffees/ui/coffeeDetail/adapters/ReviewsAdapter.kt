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

package com.synaptix.capetowncoffees.ui.coffeeDetail.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.databinding.ItemCoffeeReviewBinding
import com.synaptix.capetowncoffees.domain.model.CoffeeReview
import com.synaptix.capetowncoffees.domain.model.avatarModelOrNull
import com.synaptix.capetowncoffees.domain.model.isInApp
import com.synaptix.capetowncoffees.domain.model.photoUrlsOrEmpty
import com.synaptix.capetowncoffees.domain.model.safeReviewKey
import com.synaptix.capetowncoffees.domain.model.stableId
import com.synaptix.capetowncoffees.ui.common.BaseAdapter
import com.synaptix.capetowncoffees.ui.common.BaseViewHolder
import com.synaptix.capetowncoffees.ui.common.simpleDiff
import com.synaptix.capetowncoffees.util.CoffeeTimeUtils
import com.synaptix.capetowncoffees.util.LocationUtil
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope

// ─────────── Adapter ───────────
// Renders a list of reviews with an optional photo mosaic and simple in-app actions.
class ReviewsAdapter @AssistedInject constructor(
    @Assisted private val coroutineScope: CoroutineScope,                  // kept in signature for assisted construction
    @Assisted private val onClick: (Click) -> Unit,                        // bubble up user interactions
    private val locationUtil: LocationUtil                                 // kept for future geo-aware features
) : BaseAdapter<CoffeeReview, ItemCoffeeReviewBinding>(
    diff = simpleDiff(
        sameItem = { o, n -> (o.id ?: o.safeReviewKey()) == (n.id ?: n.safeReviewKey()) },
        sameContent = { o, n -> o == n },
        payload = { _, _ -> null }
    ),
    idProvider = { it.stableId() }                                         // stable item ids to reduce RV churn
) {

    public override fun itemViewTypeFor(position: Int): Int = R.layout.item_coffee_review

    // ─────────── Click Events ───────────
    // Encapsulates all user events the host can react to.
    sealed interface Click {
        data class Like(val reviewId: String) : Click
        data class Dislike(val reviewId: String) : Click
        data class OpenPhoto(val reviewId: String, val startIndex: Int, val urls: List<String>) : Click
    }

    // ─────────── Factory ───────────
    // Assisted Factory to supply runtime deps (scope, handlers).
    @AssistedFactory
    interface Factory {
        fun create(
            coroutineScope: CoroutineScope,
            onClick: (Click) -> Unit
        ): ReviewsAdapter
    }

    // External update entry to keep naming consistent with other adapters.
    public fun updateItems(items: List<CoffeeReview>) = submitList(items)

    // ─────────── Binding Creation ───────────
    public override fun onCreateBinding(inflater: LayoutInflater, parent: ViewGroup): ItemCoffeeReviewBinding =
        ItemCoffeeReviewBinding.inflate(inflater, parent, false)

    public override fun onCreateVH(binding: ItemCoffeeReviewBinding) =
        object : BaseViewHolder<CoffeeReview, ItemCoffeeReviewBinding>(binding) {

            // ─────────── Bind (Full) ───────────
            // Binds all fields; uses small helpers for photo mosaic.
            public override fun bind(item: CoffeeReview) = with(vb) {
                // Source chip tells if this is an in-app or Google review
                tvSourceChip.text = if (item.isInApp)
                    root.context.getString(R.string.coffee_review_chip_in_app)
                else
                    root.context.getString(R.string.coffee_review_chip_google)

                // Author & avatar (supports ByteArray/URL via avatarModelOrNull)
                tvAuthor.text = item.authorName.orEmpty()
                Glide.with(ivAvatar)
                    .load(item.avatarModelOrNull())
                    .placeholder(R.drawable.ic_ctc_person)
                    .error(R.drawable.ic_ctc_person)
                    .circleCrop()
                    .into(ivAvatar)

                // Rating (both numeric label and visual bar)
                val rating = item.rating
                tvRating.text = rating?.let { String.format("%.1f", it) } ?: ""
                tvRating.isGone = rating == null
                ratingBar.isGone = rating == null
                ratingBar.rating = (rating ?: 0.0).toFloat()

                // Relative date for readability
                "• ${CoffeeTimeUtils.formatRelativeTime(item.publishTime)}".also { tvDate.text = it }
                tvDate.isGone = tvDate.text.isNullOrBlank()

                // Review body shown only when present
                tvBody.text = item.text.orEmpty()
                tvBody.isVisible = !item.text.isNullOrBlank()

                // Photo mosaic with tap-to-open gallery behavior
                val urls = item.photoUrlsOrEmpty()
                bindMosaic(reviewId = item.safeReviewKey(), urls = urls)

                // In-app reviews expose like/dislike; Google is read-only
                val inApp = item.isInApp
                actionsContainer.isVisible = inApp
                if (inApp) {
                    val key = item.safeReviewKey()
                    btnLike.setOnClickListener { onClick(Click.Like(key)) }
                    btnDislike.setOnClickListener { onClick(Click.Dislike(key)) }
                } else {
                    btnLike.setOnClickListener(null)
                    btnDislike.setOnClickListener(null)
                }
            }

            public override fun bind(item: CoffeeReview, payloads: List<Any>) = bind(item)

            // ─────────── Mosaic Helpers ───────────
            // Computes a responsive layout in a ConstraintLayout for 0..N photos.
            private fun ItemCoffeeReviewBinding.bindMosaic(
                reviewId: String,
                urls: List<String>
            ) {
                val count = urls.size
                photosMosaic.isVisible = count > 0
                if (count == 0) return

                fun load(targetId: Int, url: String) {
                    val iv = root.findViewById<com.google.android.material.imageview.ShapeableImageView>(targetId)
                    Glide.with(iv)
                        .load(url)
                        .placeholder(R.drawable.featured_placeholder)
                        .error(R.drawable.featured_placeholder)
                        .centerCrop()
                        .into(iv)
                }

                // Reset photo views before applying layout
                ivPhoto1.isVisible = false
                ivPhoto2.isVisible = false
                ivPhoto3.isVisible = false
                photo4Container.isVisible = false
                moreScrim.isVisible = false
                tvMoreBadge.isVisible = false

                val cs = ConstraintSet().apply { clone(photosMosaic) }

                fun clearAll() {
                    listOf(R.id.ivPhoto1, R.id.ivPhoto2, R.id.ivPhoto3, R.id.photo4Container).forEach { cs.clear(it) }
                }

                fun full(target: Int) {
                    cs.connect(target, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                    cs.connect(target, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                    cs.connect(target, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                    cs.connect(target, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                }

                fun leftHalfFullHeight(target: Int) {
                    cs.connect(target, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                    cs.connect(target, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                    cs.connect(target, ConstraintSet.END, R.id.gVert50, ConstraintSet.START)
                    cs.connect(target, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                }

                fun rightHalfFullHeight(target: Int) {
                    cs.connect(target, ConstraintSet.START, R.id.gVert50, ConstraintSet.START)
                    cs.connect(target, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                    cs.connect(target, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                    cs.connect(target, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                }

                fun rightTop(target: Int) {
                    cs.connect(target, ConstraintSet.START, R.id.gVert50, ConstraintSet.START)
                    cs.connect(target, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                    cs.connect(target, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                    cs.connect(target, ConstraintSet.BOTTOM, R.id.gHorz50, ConstraintSet.TOP)
                }

                fun rightBottom(target: Int) {
                    cs.connect(target, ConstraintSet.START, R.id.gVert50, ConstraintSet.START)
                    cs.connect(target, ConstraintSet.TOP, R.id.gHorz50, ConstraintSet.BOTTOM)
                    cs.connect(target, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                    cs.connect(target, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                }

                when (count) {
                    1 -> {
                        clearAll()
                        ivPhoto1.isVisible = true
                        load(R.id.ivPhoto1, urls[0])
                        full(R.id.ivPhoto1)
                        ivPhoto1.setOnClickListener { onClick(Click.OpenPhoto(reviewId, 0, urls)) }
                    }
                    2 -> {
                        clearAll()
                        ivPhoto1.isVisible = true
                        ivPhoto2.isVisible = true
                        load(R.id.ivPhoto1, urls[0])
                        load(R.id.ivPhoto2, urls[1])
                        leftHalfFullHeight(R.id.ivPhoto1)
                        rightHalfFullHeight(R.id.ivPhoto2)
                        ivPhoto1.setOnClickListener { onClick(Click.OpenPhoto(reviewId, 0, urls)) }
                        ivPhoto2.setOnClickListener { onClick(Click.OpenPhoto(reviewId, 1, urls)) }
                    }
                    3 -> {
                        clearAll()
                        ivPhoto1.isVisible = true
                        ivPhoto2.isVisible = true
                        ivPhoto3.isVisible = true
                        load(R.id.ivPhoto1, urls[0])
                        load(R.id.ivPhoto2, urls[1])
                        load(R.id.ivPhoto3, urls[2])
                        leftHalfFullHeight(R.id.ivPhoto1)
                        rightTop(R.id.ivPhoto2)
                        rightBottom(R.id.ivPhoto3)
                        ivPhoto1.setOnClickListener { onClick(Click.OpenPhoto(reviewId, 0, urls)) }
                        ivPhoto2.setOnClickListener { onClick(Click.OpenPhoto(reviewId, 1, urls)) }
                        ivPhoto3.setOnClickListener { onClick(Click.OpenPhoto(reviewId, 2, urls)) }
                    }
                    else -> {
                        // 4+ => 2x2 grid with "+N" indicator
                        clearAll()
                        ivPhoto1.isVisible = true
                        ivPhoto2.isVisible = true
                        ivPhoto3.isVisible = true
                        photo4Container.isVisible = true

                        load(R.id.ivPhoto1, urls[0])
                        load(R.id.ivPhoto2, urls[1])
                        load(R.id.ivPhoto3, urls[2])
                        load(R.id.ivPhoto4, urls[3])

                        cs.connect(R.id.ivPhoto1, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                        cs.connect(R.id.ivPhoto1, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                        cs.connect(R.id.ivPhoto1, ConstraintSet.END, R.id.gVert50, ConstraintSet.START)
                        cs.connect(R.id.ivPhoto1, ConstraintSet.BOTTOM, R.id.gHorz50, ConstraintSet.TOP)

                        cs.connect(R.id.ivPhoto2, ConstraintSet.START, R.id.gVert50, ConstraintSet.START)
                        cs.connect(R.id.ivPhoto2, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                        cs.connect(R.id.ivPhoto2, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                        cs.connect(R.id.ivPhoto2, ConstraintSet.BOTTOM, R.id.gHorz50, ConstraintSet.TOP)

                        cs.connect(R.id.ivPhoto3, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                        cs.connect(R.id.ivPhoto3, ConstraintSet.TOP, R.id.gHorz50, ConstraintSet.BOTTOM)
                        cs.connect(R.id.ivPhoto3, ConstraintSet.END, R.id.gVert50, ConstraintSet.START)
                        cs.connect(R.id.ivPhoto3, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)

                        cs.connect(R.id.photo4Container, ConstraintSet.START, R.id.gVert50, ConstraintSet.START)
                        cs.connect(R.id.photo4Container, ConstraintSet.TOP, R.id.gHorz50, ConstraintSet.BOTTOM)
                        cs.connect(R.id.photo4Container, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                        cs.connect(R.id.photo4Container, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)

                        val more = urls.size - 4
                        val hasMore = more > 0
                        moreScrim.isVisible = hasMore
                        tvMoreBadge.isVisible = hasMore
                        if (hasMore) tvMoreBadge.text = "+$more"

                        ivPhoto1.setOnClickListener { onClick(Click.OpenPhoto(reviewId, 0, urls)) }
                        ivPhoto2.setOnClickListener { onClick(Click.OpenPhoto(reviewId, 1, urls)) }
                        ivPhoto3.setOnClickListener { onClick(Click.OpenPhoto(reviewId, 2, urls)) }
                        photo4Container.setOnClickListener { onClick(Click.OpenPhoto(reviewId, 3, urls)) }
                    }
                }

                cs.applyTo(photosMosaic)
            }
        }
}
