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

class SuggestionsAdapter(
    private val onClick: (CoffeePlaceSuggestion) -> Unit
) : BaseAdapter<CoffeePlaceSuggestion, ItemSearchSuggestionBinding>(
    diff = simpleDiff(
        sameItem = { o, n -> o.id == n.id },
        sameContent = { o, n -> o == n }
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
            tvTitle.text = item.name.orEmpty()

            val addr = item.address.orEmpty()
            tvSubtitle.isVisible = addr.isNotBlank()
            tvSubtitle.text = addr

            val dist = item.distance?.toFloat() ?: 0f
            tvDistance.text = LocationFormattingUtil.formatDistance(dist)
            tvDistance.visibility = if (dist == 0f) View.GONE else View.VISIBLE

            root.setOnClickListener { onClick(item) }
        }
    }
}