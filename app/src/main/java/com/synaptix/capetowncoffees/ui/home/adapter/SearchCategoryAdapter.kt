package com.synaptix.capetowncoffees.ui.home.adapter

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.synaptix.capetowncoffees.R

data class FilterCategory(
    val title: String,
    val options: List<String>,
    var isExpanded: Boolean = false,
    var selectedOptionIndex: Int = -1 // Track selected option for THIS category
)

class SearchCategoryAdapter(
    private val categories: List<FilterCategory>,
    private val onSizeChanged: () -> Unit
) : RecyclerView.Adapter<SearchCategoryAdapter.CategoryViewHolder>() {

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val headerRow: LinearLayout = itemView.findViewById(R.id.headerRow)
        val categoryTitle: TextView = itemView.findViewById(R.id.tvCategoryTitle)
        val arrowIcon: ImageView = itemView.findViewById(R.id.ivArrow)
        val pillsScrollView: HorizontalScrollView = itemView.findViewById(R.id.pillsScrollView)
        // Reference the new LinearLayout
        val optionsContainer: LinearLayout = itemView.findViewById(R.id.optionsContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_filter, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        holder.categoryTitle.text = category.title

        updateCategoryView(holder, category)

        holder.optionsContainer.removeAllViews()
        val inflater = LayoutInflater.from(holder.itemView.context)

        // --- THIS IS THE FIX: Replicate the selection logic from CategoryAdapter ---
        category.options.forEachIndexed { index, optionText ->
            val button = inflater.inflate(R.layout.item_category, holder.optionsContainer, false) as MaterialButton

            button.apply {
                text = optionText
                icon = null // No icon for these filters
                // Set initial style based on selection state
                setStyle(this, index == category.selectedOptionIndex)
            }

            button.setOnClickListener {
                // If the same button is clicked again, deselect it. Otherwise, select the new one.
                val newSelection = if (category.selectedOptionIndex == index) -1 else index

                // Update the data model
                category.selectedOptionIndex = newSelection

                // Update the style for all buttons in this group
                holder.optionsContainer.children.forEachIndexed { childIndex, childView ->
                    if (childView is MaterialButton) {
                        setStyle(childView, childIndex == newSelection)
                    }
                }
            }
            holder.optionsContainer.addView(button)
        }
        // --- END OF FIX ---

        holder.headerRow.setOnClickListener {
            category.isExpanded = !category.isExpanded
            notifyItemChanged(position)
            onSizeChanged()
        }
    }

    /**
     * Helper function to apply styling for selected/unselected states,
     * mimicking the logic in your HomeFragment's CategoryAdapter.
     */
    private fun setStyle(button: MaterialButton, isSelected: Boolean) {
        val context = button.context
        if (isSelected) {
            // Selected state: Solid fill
            button.backgroundTintList = ContextCompat.getColorStateList(context, R.color.button_category)
            button.setTextColor(ContextCompat.getColor(context, R.color.white)) // Assuming white text on solid bg
            button.iconTint = ContextCompat.getColorStateList(context, R.color.white)
        } else {
            // Unselected state: Stroked outline
            button.backgroundTintList = ContextCompat.getColorStateList(context, R.color.background)
            button.strokeColor = ContextCompat.getColorStateList(context, R.color.button_category)
            button.setTextColor(ContextCompat.getColor(context, R.color.button_category))
            button.iconTint = ContextCompat.getColorStateList(context, R.color.button_category)
        }
    }

    // updateCategoryView and animation helpers remain the same
    private fun updateCategoryView(holder: CategoryViewHolder, category: FilterCategory) {
        holder.arrowIcon.setImageResource(
            if (category.isExpanded) R.drawable.ic_arrow_down else R.drawable.ic_arrow_forward
        )

        if (category.isExpanded) {
            holder.pillsScrollView.slideDown()
        } else {
            if (holder.pillsScrollView.visibility == View.VISIBLE) {
                holder.pillsScrollView.slideUp()
            } else {
                holder.pillsScrollView.visibility = View.GONE
            }
        }
    }

    private fun View.slideDown() {
        val view = this
        if (view.visibility == View.VISIBLE && view.height > 0) return
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val targetHeight = view.measuredHeight
        if (targetHeight == 0) {
            view.visibility = View.VISIBLE
            return
        }
        val animator = ValueAnimator.ofInt(0, targetHeight).apply {
            addUpdateListener {
                view.updateLayoutParams<ViewGroup.LayoutParams> { height = it.animatedValue as Int }
            }
            duration = 300
        }
        view.updateLayoutParams<ViewGroup.LayoutParams> { height = 0 }
        view.visibility = View.VISIBLE
        animator.start()
    }

    private fun View.slideUp() {
        val view = this
        val startHeight = view.height
        if (startHeight == 0) return
        val animator = ValueAnimator.ofInt(startHeight, 0).apply {
            addUpdateListener {
                view.updateLayoutParams<ViewGroup.LayoutParams> { height = it.animatedValue as Int }
                if (it.animatedValue as Int == 0) {
                    view.visibility = View.GONE
                }
            }
            duration = 300
        }
        animator.start()
    }

    override fun getItemCount() = categories.size
}
