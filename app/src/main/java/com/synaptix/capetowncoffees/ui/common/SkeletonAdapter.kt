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

@file:Suppress("unused")

package com.synaptix.capetowncoffees.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import com.facebook.shimmer.ShimmerFrameLayout
import com.synaptix.capetowncoffees.R

/**
 * ---------------------------------------------------------------------------
 * SkeletonAdapters — reusable skeleton rows for RecyclerViews
 * ---------------------------------------------------------------------------
 *
 * What’s new:
 * - Mutable counts with granular notify (no scroll jump)
 * - show()/hide() helpers
 * - setShimmerEnabled(enabled) to pause/resume shimmer
 * - Stable IDs for smoother prefetch/recycling
 * - MultiSkeletonAdapter: setSpecs(...), setVisible(...), setShimmerEnabled(...)
 *
 * Typical pattern (keeps your ConcatAdapter stable):
 *
 *   val popularSkeleton = SkeletonFactories.of(R.layout.item_place_skeleton, 5)
 *   val popularConcat = ConcatAdapter(popularSkeleton, popularAdapter)
 *
 *   // While loading
 *   popularSkeleton.show(5)
 *
 *   // When data arrives
 *   popularAdapter.updateItems(data)
 *   popularSkeleton.hide()
 */

/* ───────────────────────────── Utilities ───────────────────────────── */

/** Prevents clicks/focus on a skeleton row. */
private fun View.disableInteractive() {
    isClickable = false
    isLongClickable = false
    isFocusable = false
    isFocusableInTouchMode = false
}

/**
 * Finds a ShimmerFrameLayout for this row:
 * - If the root *is* a ShimmerFrameLayout, return it.
 * - Else, look for a child with id @id/shimmerRoot (recommended in your skeleton layout).
 */
private fun View.findShimmer(): ShimmerFrameLayout? = when (this) {
    is ShimmerFrameLayout -> this
    else -> findViewById(R.id.shimmerRoot)
}

/* ─────────────────────────── Simple Skeleton ─────────────────────────── */

/**
 * Lightweight adapter for rendering N copies of a single skeleton layout.
 *
 * The skeleton layout should either:
 *  - Have a ShimmerFrameLayout as the root, OR
 *  - Contain a ShimmerFrameLayout with android:id="@+id/shimmerRoot"
 *
 * @param count how many rows to render initially (default 5)
 * @param layoutResId the skeleton row layout (e.g., R.layout.item_place_skeleton)
 * @param onBind optional callback to tweak per-item (margins, width, etc.)
 */
class SkeletonAdapter(
    count: Int = 5,
    @param:LayoutRes private val layoutResId: Int,
    private var onBind: ((itemView: View, position: Int) -> Unit)? = null
) : RecyclerView.Adapter<SkeletonAdapter.VH>() {

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView)

    // Mutable so we can toggle without swapping adapters
    private var count: Int = count

    // Let the adapter pause shimmer globally
    private var shimmerEnabled: Boolean = true

    init {
        setHasStableIds(true) // improves prefetch/recycling
    }

    override fun getItemId(position: Int): Long = position.toLong()

    // 🔒 IMPORTANT: return the layout id as the viewType to avoid Concat collisions
    override fun getItemViewType(position: Int): Int = layoutResId

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(layoutResId, parent, false)
        v.disableInteractive()
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        onBind?.invoke(holder.itemView, position)
    }

    override fun getItemCount(): Int = count

    /** Start shimmer when the row becomes visible (if enabled). */
    override fun onViewAttachedToWindow(holder: VH) {
        super.onViewAttachedToWindow(holder)
        holder.itemView.findShimmer()?.let { shimmer ->
            if (shimmerEnabled) shimmer.startShimmer() else shimmer.stopShimmer()
        }
    }

    /** Stop shimmer when off-screen to save CPU/GPU. */
    override fun onViewDetachedFromWindow(holder: VH) {
        holder.itemView.findShimmer()?.stopShimmer()
        super.onViewDetachedFromWindow(holder)
    }

    override fun onViewRecycled(holder: VH) {
        holder.itemView.findShimmer()?.stopShimmer()
        super.onViewRecycled(holder)
    }

    // ─────────────── Public controls ───────────────

    /** Toggle how many skeleton rows are shown, with granular updates (no jump). */
    fun setCount(newCount: Int) {
        if (newCount == count) return
        val old = count
        count = newCount

        when {
            newCount == 0 && old > 0 -> notifyItemRangeRemoved(0, old)
            old == 0 && newCount > 0 -> notifyItemRangeInserted(0, newCount)
            newCount > old           -> notifyItemRangeInserted(old, newCount - old)
            newCount < old           -> notifyItemRangeRemoved(newCount, old - newCount)
        }
    }

    /** Convenience: show N skeleton rows (default 5). */
    fun show(n: Int = 5) = setCount(n)

    /** Convenience: hide all skeleton rows. */
    fun hide() = setCount(0)

    /** Enable/disable shimmer globally (e.g., in onResume/onPause). */
    fun setShimmerEnabled(enabled: Boolean) {
        if (shimmerEnabled == enabled) return
        shimmerEnabled = enabled
        // Refresh visible rows to apply start/stop. This is cheap for small skeleton lists.
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount)
    }
}

/* ─────────────────────────── Multi-type Skeleton ─────────────────────────── */

/** Describes one skeleton block: which layout and how many times to repeat it. */
data class SkeletonSpec(@param:LayoutRes val layout: Int, val count: Int)

/**
 * Renders a sequence of skeleton blocks. Useful for a header skeleton followed by repeated cards, etc.
 *
 * Example:
 *   val adapter = MultiSkeletonAdapter(
 *       specs = listOf(
 *           SkeletonSpec(R.layout.item_section_header_skeleton, 1),
 *           SkeletonSpec(R.layout.item_place_skeleton, 5)
 *       )
 *   )
 */
class MultiSkeletonAdapter(
    specs: List<SkeletonSpec>,
    private var onBind: ((itemView: View, absolutePos: Int, layout: Int) -> Unit)? = null
) : RecyclerView.Adapter<MultiSkeletonAdapter.VH>() {

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView)

    private var layouts: MutableList<Int> = buildList {
        specs.forEach { spec -> repeat(spec.count) { add(spec.layout) } }
    }.toMutableList()

    private var shimmerEnabled: Boolean = true

    init { setHasStableIds(true) }

    override fun getItemId(position: Int): Long {
        // compose viewType + position for stability across identical layouts
        return (getItemViewType(position).toLong() shl 32) or position.toLong()
    }

    override fun getItemCount(): Int = layouts.size
    override fun getItemViewType(position: Int): Int = layouts[position]

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        v.disableInteractive()
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        onBind?.invoke(holder.itemView, position, getItemViewType(position))
    }

    override fun onViewAttachedToWindow(holder: VH) {
        super.onViewAttachedToWindow(holder)
        holder.itemView.findShimmer()?.let { shimmer ->
            if (shimmerEnabled) shimmer.startShimmer() else shimmer.stopShimmer()
        }
    }

    override fun onViewDetachedFromWindow(holder: VH) {
        holder.itemView.findShimmer()?.stopShimmer()
        super.onViewDetachedFromWindow(holder)
    }

    override fun onViewRecycled(holder: VH) {
        holder.itemView.findShimmer()?.stopShimmer()
        super.onViewRecycled(holder)
    }

    // ─────────────── Public controls ───────────────

    /** Replace skeleton sequence; simplest refresh (skeletons are cheap). */
    fun setSpecs(specs: List<SkeletonSpec>) {
        val newLayouts = buildList {
            specs.forEach { spec -> repeat(spec.count) { add(spec.layout) } }
        }
        layouts = newLayouts.toMutableList()
        notifyDataSetChanged()
    }

    /** Show/hide the current skeleton sequence. */
    fun setVisible(visible: Boolean) {
        if (visible) {
            // no-op: caller should also ensure layouts is non-empty (via setSpecs)
            return
        }
        if (layouts.isNotEmpty()) {
            val n = layouts.size
            layouts.clear()
            notifyItemRangeRemoved(0, n)
        }
    }

    /** Enable/disable shimmer globally (e.g., in onResume/onPause). */
    fun setShimmerEnabled(enabled: Boolean) {
        if (shimmerEnabled == enabled) return
        shimmerEnabled = enabled
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount)
    }
}

/* ───────────────────────────── Factories ───────────────────────────── */

object SkeletonFactories {

    /**
     * Create a simple skeleton adapter.
     *
     * @param layout the skeleton item layout resource
     * @param count how many rows to show initially (default 5)
     * @param onBind optional callback to tweak each skeleton row
     */
    fun of(
        @LayoutRes layout: Int,
        count: Int = 5,
        onBind: ((itemView: View, position: Int) -> Unit)? = null
    ): SkeletonAdapter = SkeletonAdapter(
        count = count,
        layoutResId = layout,
        onBind = onBind
    )

    /**
     * Create a multi-type skeleton adapter from a list of specs.
     */
    fun multi(
        specs: List<SkeletonSpec>,
        onBind: ((itemView: View, absolutePos: Int, layout: Int) -> Unit)? = null
    ): MultiSkeletonAdapter = MultiSkeletonAdapter(specs, onBind)
}

/* ───────────────────────────── Examples ───────────────────────────── */
/*
    // Simple:
    val featuredSkeleton = SkeletonFactories.of(
        layout = R.layout.item_place_skeleton,
        count = 5
    )

    // Multi:
    val fancySkeleton = SkeletonFactories.multi(
        specs = listOf(
            SkeletonSpec(R.layout.item_section_header_skeleton, 1),
            SkeletonSpec(R.layout.item_place_skeleton, 5)
        )
    )

    // With ConcatAdapter (recommended):
    val concat = ConcatAdapter(featuredSkeleton, realAdapter)

    // While loading:
    featuredSkeleton.show(5)

    // When Data arrives:
    realAdapter.updateItems(data)
    featuredSkeleton.hide()

    // Pause/resume shimmer with Fragment lifecycle:
    override fun onResume() {
        super.onResume()
        featuredSkeleton.setShimmerEnabled(true)
    }
    override fun onPause() {
        featuredSkeleton.setShimmerEnabled(false)
        super.onPause()
    }
*/
