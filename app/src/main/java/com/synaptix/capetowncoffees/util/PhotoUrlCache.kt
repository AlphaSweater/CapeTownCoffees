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

    /** Returns cached if known; otherwise triggers async compute (using caching rules) and returns null now. */
    fun peekOrCompute(place: CoffeePlaceLite): String? {
        // Already resolved (including null)?
        cache[place.id]?.let { return it }
        if (cache.containsKey(place.id)) return null

        // Compute async using the new unified logic
        owner.lifecycleScope.launch {
            val uri = try {
                utils.getPhotoUriFromMetadata(
                    photoMetadata   = place.images?.firstOrNull(),
                    isCached        = place.isCached,
                    cachedImageUrl  = place.cachedImageUrl,
                    maxWidthDp      = maxWidthDp
                )
            } catch (_: Throwable) {
                null
            }

            cache[place.id] = uri?.toString()
        }

        return null
    }

    /** Eagerly compute the first [take] URLs using caching rules. */
    fun warm(items: List<CoffeePlaceLite>, take: Int) {
        if (items.isEmpty()) return
        val n = min(items.size, take)

        owner.lifecycleScope.launch {
            for (i in 0 until n) {
                val place = items[i]

                // Skip if already resolved
                if (cache.containsKey(place.id)) continue

                val uri = try {
                    utils.getPhotoUriFromMetadata(
                        photoMetadata   = place.images?.firstOrNull(),
                        isCached        = place.isCached,
                        cachedImageUrl  = place.cachedImageUrl,
                        maxWidthDp      = maxWidthDp
                    )
                } catch (_: Throwable) {
                    null
                }

                cache[place.id] = uri?.toString()
            }
        }
    }
}