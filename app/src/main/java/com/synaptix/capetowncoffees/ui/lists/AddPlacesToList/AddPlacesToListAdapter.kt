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

package com.synaptix.capetowncoffees.ui.lists.AddPlacesToList

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.databinding.ItemSavedAddToListBinding
import com.synaptix.capetowncoffees.domain.model.CoffeeList

// ─────────── Adapter ───────────
// Presents user lists with a checkbox; selection is tracked internally and exposed via getSelected().
public class AddPlacesToListAdapter(
    private val onToggle: (id: String, checked: Boolean) -> Unit
) : ListAdapter<CoffeeList, AddPlacesToListAdapter.VH>(Diff) {

    // ─────────── State ───────────
    // LinkedHashSet keeps selection order stable for UX (e.g., recent taps stay last).
    private val selected: LinkedHashSet<String> = linkedSetOf()

    public fun getSelected(): List<String> = selected.toList()

    public fun setSelected(ids: Collection<String>) {
        selected.clear()
        selected.addAll(ids)
        notifyDataSetChanged() // full refresh is acceptable for small lists in a modal
    }

    // ─────────── Diff ───────────
    // Identity by id; full equality for content.
    private object Diff : DiffUtil.ItemCallback<CoffeeList>() {
        override fun areItemsTheSame(o: CoffeeList, n: CoffeeList): Boolean = o.id == n.id
        override fun areContentsTheSame(o: CoffeeList, n: CoffeeList): Boolean = o == n
    }

    // ─────────── ViewHolder ───────────
    // Simple binding holder; no per-bind allocations beyond view ops.
    public inner class VH(public val b: ItemSavedAddToListBinding) : RecyclerView.ViewHolder(b.root)

    // ─────────── Adapter Overrides ───────────
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemSavedAddToListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val b = holder.b

        // Icon + primary text
        b.ivIcon.setImageResource(R.drawable.ic_ctc_bookmark)
        b.tvName.text = item.name

        // Subtitle packs visibility + count into a compact label
        b.tvSubtitle.text = (if (item.isPublic) "Public" else "Private") + " • ${item.placeIds.size} places"

        // ─────────── Binding: Checkbox ───────────
        val check: CheckBox = b.check
        check.setOnCheckedChangeListener(null) // avoid recycling callbacks flipping state
        check.isChecked = selected.contains(item.id)
        check.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selected.add(item.id) else selected.remove(item.id)
            onToggle(item.id, isChecked) // bubble change to caller
        }

        // Whole row toggles the checkbox for a larger tap target
        b.root.setOnClickListener { check.performClick() }
    }
}
