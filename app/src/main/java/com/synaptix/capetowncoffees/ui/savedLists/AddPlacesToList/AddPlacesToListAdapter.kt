package com.synaptix.capetowncoffees.ui.savedLists.AddPlacesToList

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.databinding.ItemSavedAddToListBinding
import com.synaptix.capetowncoffees.domain.model.CoffeeList

class AddPlacesToListAdapter(
    private val onToggle: (id: String, checked: Boolean) -> Unit
) : ListAdapter<CoffeeList, AddPlacesToListAdapter.VH>(Diff) {

    private val selected = linkedSetOf<String>()
    fun getSelected(): List<String> = selected.toList()
    fun setSelected(ids: Collection<String>) { selected.clear(); selected.addAll(ids); notifyDataSetChanged() }

    object Diff : DiffUtil.ItemCallback<CoffeeList>() {
        override fun areItemsTheSame(o: CoffeeList, n: CoffeeList) = o.id == n.id
        override fun areContentsTheSame(o: CoffeeList, n: CoffeeList) = o == n
    }

    inner class VH(val b: ItemSavedAddToListBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemSavedAddToListBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val b = holder.b

        b.ivIcon.setImageResource(R.drawable.ic_ctc_bookmark)
        b.tvName.text = item.name
        b.tvSubtitle.text = (if (item.isPublic) "Public" else "Private") +
                " • ${item.placeIds.size} places"

        val check: CheckBox = b.check
        check.setOnCheckedChangeListener(null)
        check.isChecked = selected.contains(item.id)
        check.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selected.add(item.id) else selected.remove(item.id)
            onToggle(item.id, isChecked)
        }
        b.root.setOnClickListener { check.performClick() }
    }
}
