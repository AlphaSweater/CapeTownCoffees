package com.synaptix.capetowncoffees.ui.home.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.databinding.ItemCategoryBinding
import com.synaptix.capetowncoffees.domain.model.Category

// ─────────── Adapter ───────────
// Simple selectable category chips backed by a RecyclerView.
class CategoryAdapter(
    private val onCategoryClick: (Category) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    // ─────────── State ───────────
    private val categories = mutableListOf<Category>()
    private var selectedPosition: Int = RecyclerView.NO_POSITION

    // ─────────── ViewHolder ───────────
    // Binds a MaterialButton as a selectable chip.
    inner class CategoryViewHolder(
        private val binding: ItemCategoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(category: Category) {
            val button = binding.root as MaterialButton
            val ctx = itemView.context

            // Text & icon reflect the domain model
            button.text = category.name
            button.setIconResource(category.iconResId)

            // Apply selected vs unselected styling
            if (bindingAdapterPosition == selectedPosition) {
                button.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.coffee_medium)
                button.setTextColor(ContextCompat.getColor(ctx, R.color.white))
                button.iconTint = ContextCompat.getColorStateList(ctx, R.color.white)
            } else {
                button.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.background)
                button.strokeColor = ContextCompat.getColorStateList(ctx, R.color.button_category)
                button.setTextColor(ContextCompat.getColor(ctx, R.color.button_category))
                button.iconTint = ContextCompat.getColorStateList(ctx, R.color.button_category)
            }

            // Click selects this item and emits the domain object
            button.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    updateSelectedPosition(pos)
                    onCategoryClick(category)
                }
            }
        }
    }

    // ─────────── Adapter Overrides ───────────
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categories[position])
    }

    override fun getItemCount(): Int = categories.size

    // ─────────── Public API ───────────
    // Replaces the data set; simple swap since category list is small.
    fun updateCategories(newCategories: List<Category>) {
        categories.clear()
        categories.addAll(newCategories)
        notifyDataSetChanged()
    }

    // ─────────── Private Helpers ───────────
    // Updates selection and refreshes only the affected items.
    private fun updateSelectedPosition(newPosition: Int) {
        if (newPosition !in 0 until itemCount) return

        val previous = selectedPosition
        selectedPosition = newPosition

        if (previous != RecyclerView.NO_POSITION) notifyItemChanged(previous)
        if (selectedPosition != RecyclerView.NO_POSITION) notifyItemChanged(selectedPosition)
    }
}
