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
import com.synaptix.capetowncoffees.domain.model.isInApp
import com.synaptix.capetowncoffees.ui.common.BaseAdapter
import com.synaptix.capetowncoffees.ui.common.BaseViewHolder
import com.synaptix.capetowncoffees.ui.common.simpleDiff
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope

// Local adapter-only helpers (implemented in CoffeeReviewExtensions.kt)
import com.synaptix.capetowncoffees.domain.model.avatarModelOrNull
import com.synaptix.capetowncoffees.domain.model.photoUrlsOrEmpty
import com.synaptix.capetowncoffees.domain.model.safeReviewKey
import com.synaptix.capetowncoffees.domain.model.stableId

/**
 * ReviewsAdapter
 *
 * Photos mosaic rules:
 *   0 -> hidden
 *   1 -> full width
 *   2 -> split halves (left/right)
 *   3 -> left tall, right split (top/bottom)
 *   4+ -> 2x2 grid with "+N" badge on bottom-right
 *
 * In-App reviews show actions (like/dislike); Google reviews are read-only.
 */
class ReviewsAdapter @AssistedInject constructor(
    @Assisted private val coroutineScope: CoroutineScope,
    @Assisted private val onClick: (Click) -> Unit,
) : BaseAdapter<CoffeeReview, ItemCoffeeReviewBinding>(
    diff = simpleDiff(
        sameItem = { o, n -> (o.id ?: o.safeReviewKey()) == (n.id ?: n.safeReviewKey()) },
        sameContent = { o, n -> o == n },
        payload = { _, _ -> null }
    ),
    idProvider = { it.stableId() }
) {

    sealed interface Click {
        data class Like(val reviewId: String) : Click
        data class Dislike(val reviewId: String) : Click
        data class OpenPhoto(val reviewId: String, val startIndex: Int, val urls: List<String>) : Click
    }

    @AssistedFactory
    interface Factory {
        fun create(
            coroutineScope: CoroutineScope,
            onClick: (Click) -> Unit
        ): ReviewsAdapter
    }

    fun updateItems(items: List<CoffeeReview>) = submitList(items)

    override fun onCreateBinding(inflater: LayoutInflater, parent: ViewGroup): ItemCoffeeReviewBinding =
        ItemCoffeeReviewBinding.inflate(inflater, parent, false)

    override fun onCreateVH(binding: ItemCoffeeReviewBinding) =
        object : BaseViewHolder<CoffeeReview, ItemCoffeeReviewBinding>(binding) {

            override fun bind(item: CoffeeReview) = with(vb) {
                // ─── Author / Avatar ───────────────────────────────────────────
                tvAuthor.text = item.authorName.orEmpty()

                Glide.with(ivAvatar)
                    .load(item.avatarModelOrNull()) // ByteArray for Base64 OR String URL for Google
                    .placeholder(R.drawable.ic_ctc_person)
                    .error(R.drawable.ic_ctc_person)
                    .circleCrop()
                    .into(ivAvatar)

                // ─── Rating (number & bar) ─────────────────────────────────────
                val rating = item.rating
                tvRating.text = rating?.let { String.format("%.1f", it) } ?: ""
                tvRating.isGone = rating == null

                ratingBar.isGone = rating == null
                ratingBar.rating = (rating ?: 0.0).toFloat()

                // ─── Date (prefer relative from VM if you have it) ─────────────
                // Bind your VM-provided string here (left blank otherwise).
                tvDate.text = ""
                tvDate.isGone = tvDate.text.isNullOrBlank()

                // ─── Body ──────────────────────────────────────────────────────
                tvBody.text = item.text.orEmpty()
                tvBody.isVisible = !item.text.isNullOrBlank()

                // ─── Photos mosaic (optional) ──────────────────────────────────
                val urls = item.photoUrlsOrEmpty()
                bindMosaic(reviewId = item.safeReviewKey(), urls = urls)

                // ─── Actions (In-App only) ─────────────────────────────────────
                val isInApp = item.isInApp
                actionsContainer.isVisible = isInApp
                dividerActions.isVisible = isInApp

                if (isInApp) {
                    val key = item.safeReviewKey()
                    btnLike.setOnClickListener { onClick(Click.Like(key)) }
                    btnDislike.setOnClickListener { onClick(Click.Dislike(key)) }
                } else {
                    btnLike.setOnClickListener(null)
                    btnDislike.setOnClickListener(null)
                }
            }

            override fun bind(item: CoffeeReview, payloads: List<Any>) = bind(item)

            // ───────────────────────── helpers ─────────────────────────

            private fun ItemCoffeeReviewBinding.bindMosaic(
                reviewId: String,
                urls: List<String>
            ) {
                val count = urls.size
                photosMosaic.isVisible = count > 0
                if (count == 0) return

                fun load(targetId: Int, url: String) {
                    val iv =
                        root.findViewById<com.google.android.material.imageview.ShapeableImageView>(targetId)
                    Glide.with(iv)
                        .load(url)
                        .placeholder(R.drawable.featured_placeholder)
                        .error(R.drawable.featured_placeholder)
                        .centerCrop()
                        .into(iv)
                }

                // Reset visibilities
                ivPhoto1.isVisible = false
                ivPhoto2.isVisible = false
                ivPhoto3.isVisible = false
                photo4Container.isVisible = false
                moreScrim.isVisible = false
                tvMoreBadge.isVisible = false

                val cs = ConstraintSet()
                cs.clone(photosMosaic)

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

                when {
                    count == 1 -> {
                        clearAll()
                        ivPhoto1.isVisible = true
                        load(R.id.ivPhoto1, urls[0])
                        full(R.id.ivPhoto1)
                        ivPhoto1.setOnClickListener { onClick(Click.OpenPhoto(reviewId, 0, urls)) }
                    }

                    count == 2 -> {
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

                    count == 3 -> {
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
                        // 4+
                        clearAll()
                        ivPhoto1.isVisible = true
                        ivPhoto2.isVisible = true
                        ivPhoto3.isVisible = true
                        photo4Container.isVisible = true

                        load(R.id.ivPhoto1, urls[0])
                        load(R.id.ivPhoto2, urls[1])
                        load(R.id.ivPhoto3, urls[2])
                        load(R.id.ivPhoto4, urls[3])

                        // Use 2x2 grid
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
                        moreScrim.isVisible = more > 0
                        tvMoreBadge.isVisible = more > 0
                        if (more > 0) tvMoreBadge.text = "+$more"

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
