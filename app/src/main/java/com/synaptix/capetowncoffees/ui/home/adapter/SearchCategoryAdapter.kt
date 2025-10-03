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
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.synaptix.capetowncoffees.R

data class FilterCategory(
    val title: String,
    val options: List<String>,
    var isExpanded: Boolean = false,
    val selectedOptionIndices: MutableSet<Int> = mutableSetOf()
)

class SearchCategoryAdapter(
    private val categories: List<FilterCategory>,
    private val onSizeChanged: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private companion object {
        const val VIEW_TYPE_BUTTONS = 1
        const val VIEW_TYPE_SLIDER = 2
    }

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val headerRow: LinearLayout = itemView.findViewById(R.id.headerRow)
        val categoryTitle: TextView = itemView.findViewById(R.id.tvCategoryTitle)
        val arrowIcon: ImageView = itemView.findViewById(R.id.ivArrow)
        val pillsScrollView: HorizontalScrollView = itemView.findViewById(R.id.pillsScrollView)
        val optionsContainer: LinearLayout = itemView.findViewById(R.id.optionsContainer)
    }

    inner class SliderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val headerRow: LinearLayout = itemView.findViewById(R.id.headerRow)
        val categoryTitle: TextView = itemView.findViewById(R.id.tvCategoryTitle)
        val arrowIcon: ImageView = itemView.findViewById(R.id.ivArrow)
        val sliderContentContainer: LinearLayout = itemView.findViewById(R.id.sliderContentContainer)
        val radiusSlider: Slider = itemView.findViewById(R.id.radiusSlider)
        val sliderValueText: TextView = itemView.findViewById(R.id.tvSliderValue)
    }

    override fun getItemViewType(position: Int): Int {
        return if (categories[position].title == "Search Radius") {
            VIEW_TYPE_SLIDER
        } else {
            VIEW_TYPE_BUTTONS
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SLIDER -> {
                val view = inflater.inflate(R.layout.item_category_slider, parent, false)
                SliderViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_category_filter, parent, false)
                CategoryViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder.itemViewType) {
            VIEW_TYPE_SLIDER -> {
                val sliderHolder = holder as SliderViewHolder
                bindSliderViewHolder(sliderHolder, position)
            }
            else -> {
                val buttonHolder = holder as CategoryViewHolder
                bindButtonViewHolder(buttonHolder, position)
            }
        }
    }

    private fun bindSliderViewHolder(holder: SliderViewHolder, position: Int) {
        val category = categories[position]
        holder.categoryTitle.text = category.title

        if (category.options.isNotEmpty()) {
            holder.radiusSlider.valueFrom = 0.0f
            holder.radiusSlider.valueTo = (category.options.size - 1).toFloat()
            holder.radiusSlider.stepSize = 1.0f
        }

        fun updateSliderText(value: Float) {
            val index = value.toInt()
            if (index in category.options.indices) {
                holder.sliderValueText.text = category.options[index]
            }
        }

        updateSliderText(holder.radiusSlider.value)

        holder.radiusSlider.addOnChangeListener { _, value, _ ->
            updateSliderText(value)
        }

        updateViewVisibility(holder.sliderContentContainer, category.isExpanded)
        holder.arrowIcon.setImageResource(
            if (category.isExpanded) R.drawable.ic_arrow_down else R.drawable.ic_arrow_forward
        )

        holder.headerRow.setOnClickListener {
            category.isExpanded = !category.isExpanded
            notifyItemChanged(position)
            onSizeChanged()
        }
    }

    private fun bindButtonViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        holder.categoryTitle.text = category.title

        updateViewVisibility(holder.pillsScrollView, category.isExpanded)
        holder.arrowIcon.setImageResource(
            if (category.isExpanded) R.drawable.ic_arrow_down else R.drawable.ic_arrow_forward
        )

        holder.optionsContainer.removeAllViews()
        val inflater = LayoutInflater.from(holder.itemView.context)

        category.options.forEachIndexed { index, optionText ->
            val button = inflater.inflate(R.layout.item_category, holder.optionsContainer, false) as MaterialButton
            button.apply {
                text = optionText
                icon = null
                setStyle(this, category.selectedOptionIndices.contains(index))
            }

            button.setOnClickListener {
                if (category.selectedOptionIndices.contains(index)) {
                    category.selectedOptionIndices.remove(index)
                } else {
                    category.selectedOptionIndices.add(index)
                }
                setStyle(it as MaterialButton, category.selectedOptionIndices.contains(index))
            }
            holder.optionsContainer.addView(button)
        }

        holder.headerRow.setOnClickListener {
            category.isExpanded = !category.isExpanded
            notifyItemChanged(position)
            onSizeChanged()
        }
    }

    private fun updateViewVisibility(view: View, isExpanded: Boolean) {
        if (isExpanded) {
            view.slideDown()
        } else {
            if (view.visibility == View.VISIBLE) {
                view.slideUp()
            } else {
                view.visibility = View.GONE
            }
        }
    }

    override fun getItemCount() = categories.size

    private fun setStyle(button: MaterialButton, isSelected: Boolean) {
        val context = button.context
        if (isSelected) {
            button.backgroundTintList = ContextCompat.getColorStateList(context, R.color.button_category)
            button.setTextColor(ContextCompat.getColor(context, R.color.white))
            button.iconTint = ContextCompat.getColorStateList(context, R.color.white)
        } else {
            button.backgroundTintList = ContextCompat.getColorStateList(context, R.color.background)
            button.strokeColor = ContextCompat.getColorStateList(context, R.color.button_category)
            button.setTextColor(ContextCompat.getColor(context, R.color.button_category))
            button.iconTint = ContextCompat.getColorStateList(context, R.color.button_category)
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
}
