package com.synaptix.capetowncoffees.ui.home.adapter

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.synaptix.capetowncoffees.R

data class FilterCategory(
    val title: String,
    val options: List<String>,
    var isExpanded: Boolean = false
)

class SearchCategoryAdapter(private val categories: List<FilterCategory>) :
    RecyclerView.Adapter<SearchCategoryAdapter.CategoryViewHolder>() {

    // --- ViewHolder updated to find the new views ---
    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val headerRow: LinearLayout = itemView.findViewById(R.id.headerRow)
        val categoryTitle: TextView = itemView.findViewById(R.id.tvCategoryTitle)
        val arrowIcon: ImageView = itemView.findViewById(R.id.ivArrow)
        // Find the new views
        val pillsScrollView: HorizontalScrollView = itemView.findViewById(R.id.pillsScrollView)
        val chipGroup: ChipGroup = itemView.findViewById(R.id.chipGroup)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_filter, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        holder.categoryTitle.text = category.title

        // Call the animation helper function, now targeting the ScrollView
        updateCategoryView(holder, category)

        // --- THIS IS THE FIX: Create Chips instead of CheckBoxes ---
        holder.chipGroup.removeAllViews() // Clear previous pills
        category.options.forEach { optionText ->
            val chip = Chip(holder.itemView.context).apply {
                text = optionText
                isCheckable = true
                // You can add styling here, e.g., setChipBackgroundColorResource, etc.
            }
            holder.chipGroup.addView(chip)
        }
        // --- END OF FIX ---

        holder.headerRow.setOnClickListener {
            category.isExpanded = !category.isExpanded
            notifyItemChanged(position)
        }
    }

    private fun updateCategoryView(holder: CategoryViewHolder, category: FilterCategory) {
        holder.arrowIcon.setImageResource(
            if (category.isExpanded) R.drawable.ic_arrow_down else R.drawable.ic_arrow_forward
        )

        // Animate the HorizontalScrollView instead of the old container
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

    // Animation helpers remain the same but will now operate on the pillsScrollView
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
                view.updateLayoutParams<ViewGroup.LayoutParams> {
                    height = it.animatedValue as Int
                }
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
                view.updateLayoutParams<ViewGroup.LayoutParams> {
                    height = it.animatedValue as Int
                }
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
