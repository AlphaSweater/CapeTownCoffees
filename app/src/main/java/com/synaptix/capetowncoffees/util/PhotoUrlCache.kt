package com.synaptix.capetowncoffees.util

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite
import com.synaptix.capetowncoffees.domain.usecase.coffeePlace.CoffeePlaceUtilsUseCase
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Small helper that:
 *  • caches computed photo URLs per place.id
 *  • can "warm" the first N URLs eagerly
 *  • returns null immediately if not ready, and computes in the background
 */
class PhotoUrlCache(
    private val owner: LifecycleOwner,
    private val utils: CoffeePlaceUtilsUseCase,
    private val maxWidthDp: Int = 500
) {
    private val cache = mutableMapOf<String, String?>()

    /** Returns cached if known; otherwise triggers async compute and returns null now. */
    fun peekOrCompute(place: CoffeePlaceLite): String? {
        val cached = cache[place.id]
        if (cached != null || cache.containsKey(place.id)) return cached

        owner.lifecycleScope.launch {
            val url = place.images?.firstOrNull()?.let { meta ->
                utils.getPhotoUriFromMetadata(meta, maxWidthDp)?.toString()
            }
            cache[place.id] = url
        }
        return null
    }

    /** Eagerly compute first [take] URLs to avoid cold misses on initial bind/scroll. */
    fun warm(items: List<CoffeePlaceLite>, take: Int) {
        if (items.isEmpty()) return
        val n = min(items.size, take)
        owner.lifecycleScope.launch {
            for (i in 0 until n) {
                val p = items[i]
                if (!cache.containsKey(p.id)) {
                    val url = p.images?.firstOrNull()?.let { meta ->
                        utils.getPhotoUriFromMetadata(meta, maxWidthDp)?.toString()
                    }
                    cache[p.id] = url
                }
            }
        }
    }
}
