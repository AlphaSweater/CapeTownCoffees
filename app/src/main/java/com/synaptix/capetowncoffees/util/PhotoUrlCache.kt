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

    /** Returns cached if known; otherwise triggers async compute (if allowed) and returns null now. */
    fun peekOrCompute(place: CoffeePlaceLite): String? {
        // 1) If we've already cached something for this id (including null), just return it.
        if (cache.containsKey(place.id)) {
            return cache[place.id]
        }

        // 2) Place is cached: NEVER hit Places; rely only on cachedImageUrl.
        if (place.isCached) {
            val cachedUrl = place.cachedImageUrl
            cache[place.id] = cachedUrl   // may be null; that's intentional
            return cachedUrl
        }

        // 3) Not cached yet: allowed to call Places.
        owner.lifecycleScope.launch {
            val url = place.images
                ?.firstOrNull()
                ?.let { meta -> utils.getPhotoUriFromMetadata(meta, maxWidthDp)?.toString() }

            cache[place.id] = url // may be null if resolution failed or no images.
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

                // Skip if we already have an entry (even if it's null).
                if (cache.containsKey(p.id)) continue

                if (p.isCached) {
                    // Cached: never hit Places, just store cachedImageUrl (can be null).
                    cache[p.id] = p.cachedImageUrl
                    continue
                }

                // Not cached: allowed to call Places via images metadata.
                val url = p.images
                    ?.firstOrNull()
                    ?.let { meta -> utils.getPhotoUriFromMetadata(meta, maxWidthDp)?.toString() }

                cache[p.id] = url
            }
        }
    }
}
