package com.synaptix.capetowncoffees.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.bumptech.glide.util.ViewPreloadSizeProvider
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.appbar.AppBarLayout
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.databinding.FragmentHomeNewBinding
import com.synaptix.capetowncoffees.domain.model.Category
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite
import com.synaptix.capetowncoffees.domain.usecase.coffeePlace.CoffeePlaceUtilsUseCase
import com.synaptix.capetowncoffees.ui._simple.viewmodel.Effect
import com.synaptix.capetowncoffees.ui._simple.viewmodel.Loadable
import com.synaptix.capetowncoffees.ui._simple.viewmodel.collect
import com.synaptix.capetowncoffees.ui._simple.viewmodel.collectLoadable
import com.synaptix.capetowncoffees.ui._simple.viewmodel.start
import com.synaptix.capetowncoffees.ui.common.SkeletonAdapter
import com.synaptix.capetowncoffees.ui.home.adapter.CategoryAdapter
import com.synaptix.capetowncoffees.ui.home.adapter.CoffeePlaceItemAdapter
import com.synaptix.capetowncoffees.ui.savedLists.AddPlacesToList.AddPlacesToListBottomSheet
import com.synaptix.capetowncoffees.util.ImagePreloadUtil
import com.synaptix.capetowncoffees.util.PhotoUrlCache
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeNewBinding? = null
    private val binding get() = _binding!!

    private val vm: HomeViewModel by activityViewModels()

    private lateinit var categoryAdapter: CategoryAdapter

    @Inject lateinit var coffeePlaceUtils: CoffeePlaceUtilsUseCase

    @Inject lateinit var placeItemAdapterFactory: CoffeePlaceItemAdapter.Factory
    private lateinit var popularAdapter: CoffeePlaceItemAdapter
    private lateinit var nearAdapter: CoffeePlaceItemAdapter

    // Data backing the adapters (used for preloading)
    private var popularItems: List<CoffeePlaceLite> = emptyList()
    private var nearItems: List<CoffeePlaceLite> = emptyList()

    // Skeletons via Concat
    private lateinit var popularSkeleton: SkeletonAdapter
    private lateinit var nearSkeleton: SkeletonAdapter

    private lateinit var popularConcat: ConcatAdapter
    private lateinit var nearConcat: ConcatAdapter

    // Image preloading config
    private val PRELOAD_AHEAD = 6
    private val popularSizeProvider = ViewPreloadSizeProvider<String>()
    private val nearSizeProvider = ViewPreloadSizeProvider<String>()

    // Shared photo URL cache / warmer (moved out of the fragment)
    private lateinit var photoCache: PhotoUrlCache

    // 🔒 Concat isolation config
    private val concatConfig: ConcatAdapter.Config by lazy {
        ConcatAdapter.Config.Builder()
            .setIsolateViewTypes(true)
            .setStableIdMode(ConcatAdapter.Config.StableIdMode.ISOLATED_STABLE_IDS)
            .build()
    }

    // Pull-to-refresh gating
    private var appBarOffset: Int = 0
    private var lastIsRefreshing: Boolean = false   // detect rising edge

    // Permissions
    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val ok = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) fetchLocation() else {
            Toast.makeText(requireContext(), R.string.location_permission_required, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeNewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        start(vm)

        photoCache = PhotoUrlCache(viewLifecycleOwner, coffeePlaceUtils, maxWidthDp = 500)

        initAdapters()
        setupRecyclerViews()
        setupPullToRefresh()
        setupCollectors()
        ensureLocation()
    }

    // ───────────────────────── Adapters ─────────────────────────

    private fun initAdapters() {
        categoryAdapter = CategoryAdapter { category: Category ->
            vm.onCategorySelected(category)
        }

        val itemClick: (CoffeePlaceItemAdapter.Click) -> Unit = { click ->
            when (click) {
                is CoffeePlaceItemAdapter.Click.Open -> navigateToCafeDetailsId(click.id)
                is CoffeePlaceItemAdapter.Click.ToggleFavorite -> {
                    Toast.makeText(requireContext(), "Fav ${click.id}: ${click.newValue}", Toast.LENGTH_SHORT).show()
                }
                is CoffeePlaceItemAdapter.Click.AddToList -> showAddToListBottomSheet(click.id)
            }
        }

        popularAdapter = placeItemAdapterFactory.create(
            currentLocation = null,
            onClick = itemClick,
            showPopularChip = true
        ).also { it.preloadSizeProvider = popularSizeProvider }

        nearAdapter = placeItemAdapterFactory.create(
            currentLocation = null,
            onClick = itemClick,
            showPopularChip = false
        ).also { it.preloadSizeProvider = nearSizeProvider }

        popularSkeleton = SkeletonAdapter(count = 5, layoutResId = R.layout.item_place_skeleton)
        nearSkeleton    = SkeletonAdapter(count = 5, layoutResId = R.layout.item_place_skeleton)

        popularAdapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        nearAdapter.stateRestorationPolicy    = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        popularSkeleton.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT
        nearSkeleton.stateRestorationPolicy    = RecyclerView.Adapter.StateRestorationPolicy.PREVENT

        popularConcat = ConcatAdapter(concatConfig, popularSkeleton, popularAdapter)
        nearConcat    = ConcatAdapter(concatConfig, nearSkeleton, nearAdapter)
    }

    // ───────────────────────── RecyclerViews ─────────────────────────

    private fun setupRecyclerViews() = with(binding) {
        fun RecyclerView.tune(commonHorizontal: Boolean) {
            setHasFixedSize(true)
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
            setItemViewCacheSize(if (commonHorizontal) 8 else 2)
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER

            val lm = if (commonHorizontal)
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            else
                LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)

            if (commonHorizontal) lm.initialPrefetchItemCount = 6
            else lm.isItemPrefetchEnabled = false

            layoutManager = lm
        }

        rvCategories.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
            setHasFixedSize(true)
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        }

        rvFeatured.apply {
            tune(commonHorizontal = true)
            setRecycledViewPool(RecyclerView.RecycledViewPool())
            adapter = popularConcat

            ImagePreloadUtil.attachWithSkeleton(
                recyclerView = this,
                fragment = this@HomeFragment,
                sizeProvider = popularSizeProvider,
                maxPreload = PRELOAD_AHEAD,
                skeletonCountProvider = { popularSkeleton.itemCount },
                dataItemCountProvider = { popularItems.size },
                urlProviderAtAdapterIndex = { idx ->
                    popularItems.getOrNull(idx)?.let { place -> photoCache.peekOrCompute(place) }
                }
            )
        }

        rvNearMe.apply {
            tune(commonHorizontal = false)
            setRecycledViewPool(RecyclerView.RecycledViewPool())
            adapter = nearConcat

            ImagePreloadUtil.attachWithSkeleton(
                recyclerView = this,
                fragment = this@HomeFragment,
                sizeProvider = nearSizeProvider,
                maxPreload = PRELOAD_AHEAD,
                skeletonCountProvider = { nearSkeleton.itemCount },
                dataItemCountProvider = { nearItems.size },
                urlProviderAtAdapterIndex = { idx ->
                    nearItems.getOrNull(idx)?.let { place -> photoCache.peekOrCompute(place) }
                }
            )
        }
    }

    // ───────────────────────── Pull-to-refresh (swap to skeletons) ─────────────────────────

    private fun setupPullToRefresh() = with(binding) {
        swipeRefresh.isEnabled = true

        appBar.doOnLayout {
            val start = appBar.height + dp(8)
            val end   = start + dp(64)
            swipeRefresh.setProgressViewOffset(true, start, end)
        }

        appBar.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { _, verticalOffset ->
            appBarOffset = verticalOffset
        })

        swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            val contentNotAtTop = rootScroll.canScrollVertically(-1)
            val appBarCollapsed = appBarOffset != 0
            val currentlyRefreshing = vm.ui.value.isRefreshing
            contentNotAtTop || appBarCollapsed || currentlyRefreshing
        }

        swipeRefresh.setOnRefreshListener {
            // Hide spinner immediately; we'll show skeletons instead
            swipeRefresh.isRefreshing = false
            // Start network refresh UX: show skeletons, hide data items
            startNetworkRefresh()
            vm.pullToRefresh()
        }
    }

    /** Swap UI into "refreshing" state: skeletons on, data off. */
    private fun startNetworkRefresh() {
        // Show shimmer headers
        popularSkeleton.show(5)
        nearSkeleton.show(5)
        // Hide current data items so only skeletons are visible
        popularAdapter.updateItems(emptyList(), vm.ui.value.currentLocation)
        nearAdapter.updateItems(emptyList(), vm.ui.value.currentLocation)
        // Also clear backing lists so preloader counts align
        popularItems = emptyList()
        nearItems = emptyList()
    }

    // ───────────────────────── Collectors ─────────────────────────

    private fun setupCollectors() {
        collect(vm.effects) { eff ->
            if (eff is Effect.Message)
                Toast.makeText(requireContext(), eff.text, Toast.LENGTH_SHORT).show()
        }

        // Detect refresh start/finish to ensure swap happens even for programmatic refreshes
        collect(vm.ui.flow) { ui ->
            binding.progressBar.isGone = true
            categoryAdapter.updateCategories(ui.categories)

            // Rising edge: false -> true means a real network refresh started
            if (!lastIsRefreshing && ui.isRefreshing) {
                startNetworkRefresh()
            }
            lastIsRefreshing = ui.isRefreshing
        }

        // Popular (Featured)
        collectLoadable(vm.featured) { loadable ->
            when (loadable) {
                Loadable.Uninitialized, Loadable.Loading -> {
                    // Initial load path
                    popularSkeleton.show(5)
                    binding.rvFeatured.isVisible = true
                }
                is Loadable.Data -> {
                    popularItems = loadable.value
                    photoCache.warm(popularItems, take = PRELOAD_AHEAD * 2)

                    // Swap back to data: hide skeletons, push items
                    popularSkeleton.hide()
                    popularAdapter.updateItems(loadable.value, vm.ui.value.currentLocation)
                    binding.rvFeatured.isVisible = loadable.value.isNotEmpty()
                }
                is Loadable.Error -> {
                    popularSkeleton.hide()
                    binding.rvFeatured.isVisible = false
                    Toast.makeText(requireContext(), loadable.error.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Near Me
        collectLoadable(vm.nearMe) { loadable ->
            when (loadable) {
                Loadable.Uninitialized, Loadable.Loading -> {
                    // Initial load path
                    nearSkeleton.show(5)
                    binding.rvNearMe.isVisible = true
                }
                is Loadable.Data -> {
                    nearItems = loadable.value
                    photoCache.warm(nearItems, take = PRELOAD_AHEAD * 2)

                    // Swap back to data
                    nearSkeleton.hide()
                    nearAdapter.updateItems(loadable.value, vm.ui.value.currentLocation)
                    binding.rvNearMe.isVisible = loadable.value.isNotEmpty()
                }
                is Loadable.Error -> {
                    nearSkeleton.hide()
                    Toast.makeText(requireContext(), loadable.error.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ───────────────────── Location permissions & fetch ─────────────────────

    private fun ensureLocation() {
        val fine = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (fine || coarse) fetchLocation() else requestPerms.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    private fun fetchLocation() {
        @Suppress("MissingPermission")
        LocationServices
            .getFusedLocationProviderClient(requireContext())
            .lastLocation
            .addOnSuccessListener { loc ->
                if (loc != null) vm.onUserLocation(LatLng(loc.latitude, loc.longitude))
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), R.string.location_fetch_failed, Toast.LENGTH_SHORT).show()
            }
    }

    // ─────────────────────────── UI helpers ───────────────────────────

    private fun showAddToListBottomSheet(id: String) {
        AddPlacesToListBottomSheet.new(id)
            .show(childFragmentManager, "AddPlacesToListBottomSheet")
    }

    private fun navigateToCafeDetailsId(id: String) {
        val bundle = Bundle().apply { putString("placeId", id) }
        findNavController().navigate(R.id.action_homeFragment_to_cafeDetailFragment, bundle)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
