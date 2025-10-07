package com.synaptix.capetowncoffees.ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.synaptix.capetowncoffees.databinding.ItemSearchSuggestionBinding
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceSuggestion
import com.synaptix.capetowncoffees.ui.common.BaseAdapter
import com.synaptix.capetowncoffees.ui.common.BaseViewHolder
import com.synaptix.capetowncoffees.ui.common.simpleDiff
import com.synaptix.capetowncoffees.util.LocationFormattingUtil

/**
 * SuggestionsAdapter — binds CoffeePlaceSuggestion directly:
 *  • Title (name)
 *  • Subtitle (address)
 *  • Distance pill (right) using the model's own distance string
 */
class SuggestionsAdapter(
    private val onClick: (CoffeePlaceSuggestion) -> Unit
) : BaseAdapter<CoffeePlaceSuggestion, ItemSearchSuggestionBinding>(
    diff = simpleDiff(
        sameItem = { o, n ->
            val ok = o.id
            val nk = n.id
            ok == nk
        },
        sameContent = { o, n ->
            o == n
        }
    ),
    idProvider = { s -> (s.id ?: "${s.name}|${s.address}").hashCode().toLong() }
) {

    override fun onCreateBinding(
        inflater: LayoutInflater,
        parent: ViewGroup,
        viewType: Int
    ): ItemSearchSuggestionBinding =
        ItemSearchSuggestionBinding.inflate(inflater, parent, false)

    override fun onCreateVH(binding: ItemSearchSuggestionBinding)
            : BaseViewHolder<CoffeePlaceSuggestion, ItemSearchSuggestionBinding> =
        RowVH(binding)

    private inner class RowVH(
        binding: ItemSearchSuggestionBinding
    ) : BaseViewHolder<CoffeePlaceSuggestion, ItemSearchSuggestionBinding>(binding) {

        override fun bind(item: CoffeePlaceSuggestion) = with(vb) {
            // Title
            tvTitle.text = item.name.orEmpty()

            // Subtitle (address)
            val addr = item.address.orEmpty()
            tvSubtitle.isVisible = addr.isNotBlank()
            tvSubtitle.text = addr

            // Distance pill (already formatted in the model)
            val dist = item.distance?.toFloat() ?: 0f
            tvDistance.text = LocationFormattingUtil.formatDistance(dist)
            tvDistance.visibility = if (dist == 0f) View.GONE else View.VISIBLE

            root.setOnClickListener { onClick(item) }
        }
    }
}