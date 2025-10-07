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

package com.synaptix.capetowncoffees.ui.home.adapter

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
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

// ─────────── Models ───────────
// Basic category with selectable options; supports expanded/collapsed state.
data class FilterCategory(
    val title: String,
    val options: List<String>,
    var isExpanded: Boolean = true,
    val selectedOptionIndices: MutableSet<Int> = mutableSetOf()
)

// ─────────── Adapter ───────────
// Renders filter sections: either a pill-button row or a slider row.
class SearchCategoryAdapter(
    private val categories: List<FilterCategory>,
    private val onSizeChanged: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // ─────────── Constants ───────────
    private companion object {
        private const val VIEW_TYPE_BUTTONS = 1
        private const val VIEW_TYPE_SLIDER = 2
        private const val ANIM_DURATION_MS = 300L
        private const val SLIDER_TITLE = "Search Radius" // simple type switch without extra metadata
    }

    // ─────────── ViewHolders ───────────
    // Button-based category (chips/pills)
    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val headerRow: LinearLayout = itemView.findViewById(R.id.headerRow)
        val categoryTitle: TextView = itemView.findViewById(R.id.tvCategoryTitle)
        val arrowIcon: ImageView = itemView.findViewById(R.id.ivArrow)
        val pillsScrollView: HorizontalScrollView = itemView.findViewById(R.id.pillsScrollView)
        val optionsContainer: LinearLayout = itemView.findViewById(R.id.optionsContainer)
    }

    // Slider-based category (e.g., radius)
    inner class SliderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val headerRow: LinearLayout = itemView.findViewById(R.id.headerRow)
        val categoryTitle: TextView = itemView.findViewById(R.id.tvCategoryTitle)
        val arrowIcon: ImageView = itemView.findViewById(R.id.ivArrow)
        val sliderContentContainer: LinearLayout = itemView.findViewById(R.id.sliderContentContainer)
        val radiusSlider: Slider = itemView.findViewById(R.id.radiusSlider)
        val sliderValueText: TextView = itemView.findViewById(R.id.tvSliderValue)
    }

    // ─────────── Adapter Overrides ───────────
    override fun getItemViewType(position: Int): Int =
        if (categories[position].title == SLIDER_TITLE) VIEW_TYPE_SLIDER else VIEW_TYPE_BUTTONS

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SLIDER -> SliderViewHolder(inflater.inflate(R.layout.item_category_slider, parent, false))
            else -> CategoryViewHolder(inflater.inflate(R.layout.item_category_filter, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder.itemViewType) {
            VIEW_TYPE_SLIDER -> bindSliderViewHolder(holder as SliderViewHolder, position)
            else -> bindButtonViewHolder(holder as CategoryViewHolder, position)
        }
    }

    override fun getItemCount(): Int = categories.size

    // ─────────── Binding: Slider Row ───────────
    // Wires up a discrete slider (index-based) with expandable content.
    private fun bindSliderViewHolder(holder: SliderViewHolder, position: Int) {
        val category = categories[position]
        holder.categoryTitle.text = category.title

        holder.sliderContentContainer.visibility = if (category.isExpanded) View.VISIBLE else View.GONE
        holder.arrowIcon.setImageResource(expandIconRes(category.isExpanded))

        if (category.options.isNotEmpty()) {
            holder.radiusSlider.valueFrom = 0f
            holder.radiusSlider.valueTo = (category.options.size - 1).toFloat()
            holder.radiusSlider.stepSize = 1f
        }

        fun updateSliderText(value: Float) {
            val i = value.toInt()
            if (i in category.options.indices) holder.sliderValueText.text = category.options[i]
        }

        updateSliderText(holder.radiusSlider.value)
        holder.radiusSlider.addOnChangeListener { _, value, _ -> updateSliderText(value) }

        holder.headerRow.setOnClickListener {
            category.isExpanded = !category.isExpanded
            if (category.isExpanded) holder.sliderContentContainer.slideDown() else holder.sliderContentContainer.slideUp()
            holder.arrowIcon.setImageResource(expandIconRes(category.isExpanded))
            onSizeChanged()
        }
    }

    // ─────────── Binding: Buttons Row ───────────
    // Inflates MaterialButtons per option and toggles selection styling.
    private fun bindButtonViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        holder.categoryTitle.text = category.title

        holder.pillsScrollView.visibility = if (category.isExpanded) View.VISIBLE else View.GONE
        holder.arrowIcon.setImageResource(expandIconRes(category.isExpanded))

        holder.optionsContainer.removeAllViews()
        val inflater = LayoutInflater.from(holder.itemView.context)

        category.options.forEachIndexed { index, optionText ->
            val button = inflater.inflate(R.layout.item_category, holder.optionsContainer, false) as MaterialButton
            val selected = category.selectedOptionIndices.contains(index)
            button.text = optionText
            button.icon = null
            setStyle(button, selected)

            button.setOnClickListener {
                val nowSelected = if (category.selectedOptionIndices.remove(index)) false else {
                    category.selectedOptionIndices.add(index); true
                }
                setStyle(it as MaterialButton, nowSelected)
            }

            holder.optionsContainer.addView(button)
        }

        holder.headerRow.setOnClickListener {
            category.isExpanded = !category.isExpanded
            if (category.isExpanded) holder.pillsScrollView.slideDown() else holder.pillsScrollView.slideUp()
            holder.arrowIcon.setImageResource(expandIconRes(category.isExpanded))
            onSizeChanged()
        }
    }

    // ─────────── Styling Helpers ───────────
    // Applies selected/unselected chip styles without allocating themes per click.
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

    private fun expandIconRes(expanded: Boolean): Int =
        if (expanded) R.drawable.ic_ctc_arrow_drop_down else R.drawable.ic_ctc_arrow_drop_foward

    // ─────────── Animations ───────────
    // Lightweight expand/collapse with height animator; avoids layout thrash.
    private fun View.slideDown() {
        val view = this
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val targetHeight = view.measuredHeight
        if (view.visibility == View.VISIBLE && view.height == targetHeight) return

        view.updateLayoutParams<ViewGroup.LayoutParams> { height = 0 }
        view.visibility = View.VISIBLE

        ValueAnimator.ofInt(0, targetHeight).apply {
            addUpdateListener { anim ->
                view.updateLayoutParams<ViewGroup.LayoutParams> { height = anim.animatedValue as Int }
            }
            duration = ANIM_DURATION_MS
        }.start()
    }

    private fun View.slideUp() {
        val view = this
        val startHeight = view.height
        if (startHeight == 0) {
            view.visibility = View.GONE
            return
        }

        ValueAnimator.ofInt(startHeight, 0).apply {
            addUpdateListener { anim ->
                view.updateLayoutParams<ViewGroup.LayoutParams> { height = anim.animatedValue as Int }
            }
            duration = ANIM_DURATION_MS
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.visibility = View.GONE
                }
            })
        }.start()
    }
}
