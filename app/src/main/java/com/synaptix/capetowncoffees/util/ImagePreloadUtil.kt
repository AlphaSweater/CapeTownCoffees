package com.synaptix.capetowncoffees.util

import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.ListPreloader
import com.bumptech.glide.integration.recyclerview.RecyclerViewPreloader
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.util.ViewPreloadSizeProvider

/**
 * Skeleton-aware Glide preloader:
 * • works with ConcatAdapter (skeleton + data)
 * • you pass the skeleton header size and an index→URL resolver (nullable for "not ready yet")
 */
object ImagePreloadUtil {

    fun attachWithSkeleton(
        recyclerView: RecyclerView,
        fragment: Fragment,
        sizeProvider: ViewPreloadSizeProvider<String>,
        maxPreload: Int,
        skeletonCountProvider: () -> Int,
        dataItemCountProvider: () -> Int,
        urlProviderAtAdapterIndex: (Int) -> String?
    ) {
        val requestManager = Glide.with(fragment)

        val provider = object : ListPreloader.PreloadModelProvider<String> {
            override fun getPreloadItems(position: Int): List<String> {
                val dataIndex = position - skeletonCountProvider()
                if (dataIndex !in 0 until dataItemCountProvider()) return emptyList()
                val url = urlProviderAtAdapterIndex(dataIndex) ?: return emptyList()
                return listOf(url)
            }

            override fun getPreloadRequestBuilder(item: String) =
                requestManager
                    .load(item)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .thumbnail(0.25f)
                    .centerCrop()
        }

        recyclerView.addOnScrollListener(
            RecyclerViewPreloader(
                requestManager,
                provider,
                sizeProvider,   // supplied by your item ViewHolder via adapter
                maxPreload
            )
        )
    }
}
