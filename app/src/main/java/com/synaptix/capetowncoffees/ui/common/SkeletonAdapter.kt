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
 * Use cases:
 *  - Show N copies of one skeleton layout -> [SkeletonAdapter]
 *  - Show a sequence of different skeleton layouts -> [MultiSkeletonAdapter] + [SkeletonSpec]
 *  - Quick helpers -> [SkeletonFactories]
 *
 * Typical pattern in Fragment:
 *
 *   private lateinit var featuredSkeleton: SkeletonAdapter
 *
 *   featuredSkeleton = SkeletonFactories.of(
 *       layout = R.layout.item_coffee_near_me_skeleton,
 *       count = 5
 *   )
 *
 *   // While loading
 *   if (rv.adapter !== featuredSkeleton) rv.adapter = featuredSkeleton
 *
 *   // When data arrives
 *   realAdapter.updateItems(data)
 *   if (rv.adapter !== realAdapter) rv.adapter = realAdapter
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
 * - Else, look for a child with id @id/shimmerRoot (recommended on your skeleton layout).
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
 * @param count how many rows to render (default 5)
 * @param layoutResId the skeleton row layout (e.g., R.layout.item_coffee_near_me_skeleton)
 * @param onBind optional callback to tweak per-item (margins, width, etc.)
 */
class SkeletonAdapter(
    private val count: Int = 5,
    @param:LayoutRes private val layoutResId: Int,
    private val onBind: ((itemView: View, position: Int) -> Unit)? = null
) : RecyclerView.Adapter<SkeletonAdapter.VH>() {

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(layoutResId, parent, false)
        v.disableInteractive()
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        onBind?.invoke(holder.itemView, position)
    }

    override fun getItemCount(): Int = count

    /** Start shimmer when the row becomes visible. */
    override fun onViewAttachedToWindow(holder: VH) {
        super.onViewAttachedToWindow(holder)
        holder.itemView.findShimmer()?.startShimmer()
    }

    /** Stop shimmer when the row is recycled / off-screen to save CPU/GPU. */
    override fun onViewDetachedFromWindow(holder: VH) {
        holder.itemView.findShimmer()?.stopShimmer()
        super.onViewDetachedFromWindow(holder)
    }

    override fun onViewRecycled(holder: VH) {
        holder.itemView.findShimmer()?.stopShimmer()
        super.onViewRecycled(holder)
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
 *           SkeletonSpec(R.layout.item_coffee_near_me_skeleton, 5)
 *       )
 *   )
 */
class MultiSkeletonAdapter(
    specs: List<SkeletonSpec>,
    private val onBind: ((itemView: View, absolutePos: Int, layout: Int) -> Unit)? = null
) : RecyclerView.Adapter<MultiSkeletonAdapter.VH>() {

    // Flatten spec blocks into a per-position layout list
    private val layouts: List<Int> = buildList {
        specs.forEach { spec -> repeat(spec.count) { add(spec.layout) } }
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView)

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

    /** Start shimmer when the row becomes visible. */
    override fun onViewAttachedToWindow(holder: VH) {
        super.onViewAttachedToWindow(holder)
        holder.itemView.findShimmer()?.startShimmer()
    }

    /** Stop shimmer when the row is recycled / off-screen to save CPU/GPU. */
    override fun onViewDetachedFromWindow(holder: VH) {
        holder.itemView.findShimmer()?.stopShimmer()
        super.onViewDetachedFromWindow(holder)
    }

    override fun onViewRecycled(holder: VH) {
        holder.itemView.findShimmer()?.stopShimmer()
        super.onViewRecycled(holder)
    }
}

/* ───────────────────────────── Factories ───────────────────────────── */

object SkeletonFactories {

    /**
     * Create a simple skeleton adapter.
     *
     * @param layout the skeleton item layout resource
     * @param count how many rows to show (default 5)
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
        onBind: ((
            itemView: View,
            absolutePos: Int,
            layout: Int
        ) -> Unit)? = null
    ): MultiSkeletonAdapter = MultiSkeletonAdapter(specs, onBind)
}

/* ───────────────────────────── Examples ───────────────────────────── */
/*
    // Simple:
    val featuredSkeleton = SkeletonFactories.of(
        layout = R.layout.item_coffee_near_me_skeleton,
        count = 5
    )

    // Multi:
    val fancySkeleton = SkeletonFactories.multi(
        specs = listOf(
            SkeletonSpec(R.layout.item_section_header_skeleton, 1),
            SkeletonSpec(R.layout.item_coffee_near_me_skeleton, 5)
        )
    )

    // Swap pattern:
    rv.adapter = featuredSkeleton              // during Loading
    // ...
    realAdapter.updateItems(data)
    rv.adapter = realAdapter                   // when Data arrives
*/
