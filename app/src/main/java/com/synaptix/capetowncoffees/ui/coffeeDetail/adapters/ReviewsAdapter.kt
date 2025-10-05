package com.synaptix.capetowncoffees.ui.coffeeDetail.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.databinding.ItemReviewBinding
import com.synaptix.capetowncoffees.domain.model.CoffeeReview
import com.synaptix.capetowncoffees.domain.model.isInApp
import com.synaptix.capetowncoffees.ui.common.BaseAdapter
import com.synaptix.capetowncoffees.ui.common.BaseViewHolder
import com.synaptix.capetowncoffees.ui.common.simpleDiff
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope

/**
 * ReviewsAdapter (prototype)
 *
 * - Binds domain model directly: CoffeeReview (sealed type with subtypes like InAppReview, GooglePlaceReview)
 * - ONLY InAppReview supports interactions (Like / Dislike). Google reviews are view-only.
 * - Payload path is ready for vote changes so we don't rebind the entire row.
 *
 * Layout expectations (adjust IDs or visibility logic if your layout differs):
 * - item_review.xml contains:
 *      ivAvatar, tvAuthor, tvRating, tvDate, tvBody
 *      btnLike, btnDislike (make a small horizontal group). If you still have btnHelpful, see TODO below.
 *
 * Domain expectations (adjust as needed):
 * - CoffeeReview has: id, authorId?, authorName?, authorPhotoUrl?, rating?, text?, relativeDateLabel?, absoluteDateLabel?
 * - Optional: userVote: ReviewVote?  // NONE / LIKE / DISLIKE
 *
 * Tiny convenience flags (somewhere in your domain module):
 *   val CoffeeReview.isInApp get() = this is InAppReview
 *   val CoffeeReview.isGoogle get() = this is GooglePlaceReview
 */
class ReviewsAdapter @AssistedInject constructor(
    @Assisted private val coroutineScope: CoroutineScope,
    @Assisted private val onClick: (Click) -> Unit,
) : BaseAdapter<CoffeeReview, ItemReviewBinding>(
    diff = simpleDiff(
        sameItem    = { o, n -> o.id == n.id },
        sameContent = { o, n -> o == n },
        // If you add userVote / like counts, return PAYLOAD_VOTE when it changes.
        payload     = { o, n ->
            when {
                // TODO: if you expose userVote/counts on CoffeeReview, detect change here:
                // o.userVote != n.userVote -> PAYLOAD_VOTE
                else -> null
            }
        }
    ),
    idProvider = { it.id.hashCode().toLong() }
) {

    sealed interface Click {
        // We keep these minimal for the prototype
        data class Like(val reviewId: String) : Click
        data class Dislike(val reviewId: String) : Click
        // If you later want to open author, add: data class OpenProfile(val authorId: String) : Click
    }

    @AssistedFactory
    interface Factory {
        fun create(
            coroutineScope: CoroutineScope,
            onClick: (Click) -> Unit
        ): ReviewsAdapter
    }

    private companion object {
        const val PAYLOAD_VOTE = "payload_vote"
    }

    fun updateItems(items: List<CoffeeReview>) = submitList(items)

    override fun onCreateBinding(inflater: LayoutInflater, parent: ViewGroup): ItemReviewBinding =
        ItemReviewBinding.inflate(inflater, parent, false)

    override fun onCreateVH(binding: ItemReviewBinding) =
        object : BaseViewHolder<CoffeeReview, ItemReviewBinding>(binding) {

            override fun bind(item: CoffeeReview) = with(vb) {
                // --- Author & avatar
                tvAuthor.text = item.authorName.orEmpty()
                Glide.with(ivAvatar)
                    .load(item.authorPhotoUrl)
                    .placeholder(R.drawable.ic_avatar_placeholder)
                    .error(R.drawable.ic_avatar_placeholder)
                    .into(ivAvatar)
                // NOTE: No profile click for prototype.
                // ivAvatar.setOnClickListener { item.authorId?.let { onClick(Click.OpenProfile(it)) } }

                // --- Rating
                val ratingText = item.rating?.let { String.format("%.1f", it) } ?: ""
                tvRating.text = ratingText
                tvRating.isGone = ratingText.isBlank()

                // --- Date (prefer relative if available)
                tvDate.text = item.relativeDateLabel ?: item.absoluteDateLabel ?: ""
                tvDate.isGone = tvDate.text.isNullOrBlank()

                // --- Body
                tvBody.text = item.text.orEmpty()
                tvBody.isVisible = !item.text.isNullOrBlank()
                // TODO: if you want "See more", add maxLines + click-to-expand here.

                // --- Interactions (InApp only)
                val isInApp = item.isInApp // extension you mentioned
                // If you kept "btnHelpful" in your layout instead of like/dislike pair,
                // show only that one here for InApp and hide for Google. Otherwise use both:
                btnLike?.isVisible = isInApp
                btnDislike?.isVisible = isInApp

                if (isInApp) {
                    // If you have userVote, reflect it here (selected state, tint, etc.)
                    // Example:
                    // val vote = item.userVote ?: ReviewVote.NONE
                    // btnLike.isChecked = vote == ReviewVote.LIKE
                    // btnDislike.isChecked = vote == ReviewVote.DISLIKE

                    btnLike?.setOnClickListener { onClick(Click.Like(item.id)) }
                    btnDislike?.setOnClickListener { onClick(Click.Dislike(item.id)) }
                } else {
                    // GooglePlaceReview: view-only, hide actions
                    btnLike?.setOnClickListener(null)
                    btnDislike?.setOnClickListener(null)
                }

                // If you still have a single "btnHelpful" in XML and not like/dislike:
                // - Rename references above, OR:
                //   btnHelpful.isVisible = isInApp
                //   btnHelpful.setOnClickListener { onClick(Click.Like(item.id)) } // treat as "like"
            }

            override fun bind(item: CoffeeReview, payloads: List<Any>) {
                if (payloads.contains(PAYLOAD_VOTE)) {
                    // Micro-update only the vote visuals (no full rebind)
                    // Example if you add userVote:
                    // val vote = item.userVote ?: ReviewVote.NONE
                    // vb.btnLike?.isChecked = vote == ReviewVote.LIKE
                    // vb.btnDislike?.isChecked = vote == ReviewVote.DISLIKE
                } else bind(item)
            }
        }
}
