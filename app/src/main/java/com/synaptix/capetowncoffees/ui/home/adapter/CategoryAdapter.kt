package com.synaptix.capetowncoffees.ui.home.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.databinding.ItemCategoryBinding
import com.synaptix.capetowncoffees.domain.model.Category

class CategoryAdapter(
    private val onCategoryClick: (Category) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    private val categories = mutableListOf<Category>()
    private var selectedPosition = RecyclerView.NO_POSITION

    inner class CategoryViewHolder(
        private val binding: ItemCategoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(category: Category) {
            val button = binding.root as MaterialButton

            button.text = category.name
            button.setIconResource(category.iconResId)

            if (adapterPosition == selectedPosition) {
                // Selected state
                button.backgroundTintList = ContextCompat.getColorStateList(
                    itemView.context,
                    R.color.coffee_medium
                )
                button.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.white)
                )
                button.iconTint = ContextCompat.getColorStateList(
                    itemView.context,
                    R.color.white
                )
            } else {
                // Unselected state
                button.backgroundTintList = ContextCompat.getColorStateList(
                    itemView.context,
                    R.color.background
                )
                button.strokeColor = ContextCompat.getColorStateList(
                    itemView.context,
                    R.color.button_category
                )
                button.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.button_category)
                )
                button.iconTint = ContextCompat.getColorStateList(
                    itemView.context,
                    R.color.button_category
                )
            }

            button.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    updateSelectedPosition(position)
                    onCategoryClick(category)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemCategoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categories[position])
    }

    override fun getItemCount(): Int = categories.size

    fun updateCategories(newCategories: List<Category>) {
        categories.clear()
        categories.addAll(newCategories)
        notifyDataSetChanged()
    }

    private fun updateSelectedPosition(newPosition: Int) {
        if (newPosition in 0 until itemCount) {
            val previousPosition = selectedPosition
            selectedPosition = newPosition

            // Only notify changes if positions are valid
            if (previousPosition != RecyclerView.NO_POSITION) {
                notifyItemChanged(previousPosition)
            }
            if (selectedPosition != RecyclerView.NO_POSITION) {
                notifyItemChanged(selectedPosition)
            }
        }
    }
}
