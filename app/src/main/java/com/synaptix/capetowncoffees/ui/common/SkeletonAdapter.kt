@file:Suppress("unused")

package com.synaptix.capetowncoffees.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView

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
 *       layout = R.layout.item_place_skeleton,
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

/* ─────────────────────────── Simple Skeleton ─────────────────────────── */

/**
 * Lightweight adapter for rendering N copies of a single skeleton layout.
 *
 * @param count how many rows to render
 * @param layoutResId the skeleton row layout (e.g., R.layout.item_place_skeleton)
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
}

/* ─────────────────────────── Multi-type Skeleton ─────────────────────────── */

/** Describes one skeleton block: which layout and how many times to repeat it. */
data class SkeletonSpec(@param:LayoutRes val layout: Int, val count: Int)

/**
 * Renders a sequence of skeleton blocks. Useful when you want a header skeleton
 * followed by repeated card skeletons, etc.
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
}

/* ───────────────────────────── Factories ───────────────────────────── */

object SkeletonFactories {

    /**
     * Create a simple skeleton adapter.
     *
     * @param layout the skeleton item layout resource
     * @param count how many rows to show
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

    // Swap pattern:
    rv.adapter = featuredSkeleton              // during Loading
    // ...
    realAdapter.updateItems(data)
    rv.adapter = realAdapter                   // when Data arrives
*/
