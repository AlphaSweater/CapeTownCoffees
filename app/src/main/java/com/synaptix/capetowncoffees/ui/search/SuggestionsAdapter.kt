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
            // Prefer stable ID when present; else fall back to name+address
            val ok = o.id
            val nk = n.id
            when {
                ok != null && nk != null -> ok == nk
                else -> o.name.orEmpty() == n.name.orEmpty() &&
                        o.address.orEmpty() == n.address.orEmpty()
            }
        },
        sameContent = { o, n ->
            // Compare exactly what we render
            o.name == n.name &&
                    o.address == n.address &&
                    o.distanceDisplay() == n.distanceDisplay()
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
            val dist = item.distanceDisplay().orEmpty()
            tvDistance.text = dist
            tvDistance.visibility = if (dist.isBlank()) View.GONE else View.VISIBLE

            // A11y: readable row description
            root.contentDescription = buildString {
                append(item.name ?: "")
                if (addr.isNotBlank()) append(", ").append(addr)
                if (dist.isNotBlank()) append(", ").append(dist)
            }

            root.setOnClickListener { onClick(item) }
        }
    }
}

/* ───────────────────────── helpers ─────────────────────────
   Read the distance text from the model. We try common field names
   so you don't have to rename your domain model today.
*/
private fun CoffeePlaceSuggestion.distanceDisplay(): String? {
    return when {
        // If your model has 'distanceLabel'
        runCatching { this.javaClass.getDeclaredField("distanceLabel") }.isSuccess ->
            (this.javaClass.getDeclaredField("distanceLabel").apply { isAccessible = true }.get(this) as? String)

        // Or 'distanceText'
        runCatching { this.javaClass.getDeclaredField("distanceText") }.isSuccess ->
            (this.javaClass.getDeclaredField("distanceText").apply { isAccessible = true }.get(this) as? String)

        // Or a plain 'distance' already formatted as string
        runCatching { this.javaClass.getDeclaredField("distance") }.isSuccess ->
            (this.javaClass.getDeclaredField("distance").apply { isAccessible = true }.get(this) as? String)

        else -> null
    }
}