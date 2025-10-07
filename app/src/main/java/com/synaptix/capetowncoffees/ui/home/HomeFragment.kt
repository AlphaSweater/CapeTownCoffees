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
import com.google.android.material.transition.platform.MaterialSharedAxis
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.databinding.FragmentHomeNewBinding
import com.synaptix.capetowncoffees.domain.model.Category
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite
import com.synaptix.capetowncoffees.domain.usecase.coffeePlace.CoffeePlaceUtilsUseCase
import com.synaptix.capetowncoffees.ui.common.viewmodel.Effect
import com.synaptix.capetowncoffees.ui.common.viewmodel.Loadable
import com.synaptix.capetowncoffees.ui.common.viewmodel.collect
import com.synaptix.capetowncoffees.ui.common.viewmodel.collectLoadable
import com.synaptix.capetowncoffees.ui.common.viewmodel.start
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

    /* ╭─────────────────────────── View & VM ───────────────────────────╮ */
    private var _binding: FragmentHomeNewBinding? = null
    private val binding get() = _binding!!
    private val vm: HomeViewModel by activityViewModels()
    /* ╰──────────────────────────────────────────────────────────────────╯ */

    /* ╭──────────────────────────── DI / Utils ──────────────────────────╮ */
    @Inject lateinit var coffeePlaceUtils: CoffeePlaceUtilsUseCase
    @Inject lateinit var placeItemAdapterFactory: CoffeePlaceItemAdapter.Factory
    private lateinit var photoCache: PhotoUrlCache
    /* ╰──────────────────────────────────────────────────────────────────╯ */

    /* ╭──────────────────────────── Adapters ────────────────────────────╮ */
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var popularAdapter: CoffeePlaceItemAdapter
    private lateinit var nearAdapter: CoffeePlaceItemAdapter

    // Backing lists for preloading
    private var popularItems: List<CoffeePlaceLite> = emptyList()
    private var nearItems: List<CoffeePlaceLite> = emptyList()

    // Skeletons + Concat
    private lateinit var popularSkeleton: SkeletonAdapter
    private lateinit var nearSkeleton: SkeletonAdapter
    private lateinit var popularConcat: ConcatAdapter
    private lateinit var nearConcat: ConcatAdapter
    /* ╰──────────────────────────────────────────────────────────────────╯ */

    /* ╭──────────────────────── Image Preloading ────────────────────────╮ */
    private val popularSizeProvider = ViewPreloadSizeProvider<String>()
    private val nearSizeProvider = ViewPreloadSizeProvider<String>()
    /* ╰──────────────────────────────────────────────────────────────────╯ */

    /* ╭──────────────────────────── Scroll/Refresh ──────────────────────╮ */
    private var appBarOffset: Int = 0
    private var lastIsRefreshing: Boolean = false
    /* ╰──────────────────────────────────────────────────────────────────╯ */

    /* ╭──────────────────────────── Permissions ─────────────────────────╮ */
    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val ok = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) fetchLocation() else {
            Toast.makeText(requireContext(), R.string.location_permission_required, Toast.LENGTH_LONG).show()
        }
    }
    /* ╰──────────────────────────────────────────────────────────────────╯ */

    /* ╭──────────────────────────── Constants ───────────────────────────╮ */
    companion object {
        const val PRELOAD_AHEAD = 6
        const val POPULAR_SKELETON_COUNT = 5
        const val NEAR_SKELETON_COUNT = 5

        // Search args
        const val ARG_OPEN_FILTERS  = "open_filters"
        const val ARG_PREFILL_QUERY = "prefill_query"
        const val ARG_RADIUS_KM     = "radius_km"
        const val ARG_STRICT_ONLY   = "strict_only"
    }
    /* ╰──────────────────────────────────────────────────────────────────╯ */

    /* ╭──────────────────────── Shared RV resources ─────────────────────╮ */
    private val concatConfig: ConcatAdapter.Config by lazy {
        ConcatAdapter.Config.Builder()
            .setIsolateViewTypes(true)
            .setStableIdMode(ConcatAdapter.Config.StableIdMode.ISOLATED_STABLE_IDS)
            .build()
    }
    /* ╰──────────────────────────────────────────────────────────────────╯ */

    /* ───────────────────────────── Lifecycle ─────────────────────────── */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Smooth screen-to-screen lift
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.Y, /* forward = */ true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Y, /* forward = */ false)
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

        // OPEN SEARCH (default — filters closed)
        binding.searchCard.setOnClickListener {
            navigateToSearch(openFilters = false)
        }

        // Optional quick filters button (if present in your layout)
        binding.root.findViewById<View?>(R.id.btnQuickFilterHit)?.setOnClickListener {
            navigateToSearch(openFilters = true)
        }

        // Optional: long-press search card to open with filters expanded
        binding.searchCard.setOnLongClickListener {
            navigateToSearch(openFilters = true)
            true
        }
    }

    override fun onResume() {
        super.onResume()
        val hasLocation = vm.ui.value.currentLocation != null
        if (hasLocation && vm.shouldRefreshForSearchParamsChange()) {
            startNetworkRefresh()      // show skeletons
            vm.refresh(force = true)   // refetch Nearby + Featured
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /* ─────────────────────────── Adapters Setup ──────────────────────── */

    private fun initAdapters() {
        categoryAdapter = CategoryAdapter { category: Category ->
            vm.onCategorySelected(category)
        }

        val itemClick: (CoffeePlaceItemAdapter.Click) -> Unit = { click ->
            when (click) {
                is CoffeePlaceItemAdapter.Click.Open -> navigateToCafeDetailsId(click.id)
                is CoffeePlaceItemAdapter.Click.ToggleFavorite ->
                    Toast.makeText(requireContext(), "Fav ${click.id}: ${click.newValue}", Toast.LENGTH_SHORT).show()
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

        popularSkeleton = SkeletonAdapter(count = POPULAR_SKELETON_COUNT, layoutResId = R.layout.item_place_skeleton)
        nearSkeleton    = SkeletonAdapter(count = NEAR_SKELETON_COUNT, layoutResId = R.layout.item_place_skeleton)

        popularAdapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        nearAdapter.stateRestorationPolicy    = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        popularSkeleton.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT
        nearSkeleton.stateRestorationPolicy    = RecyclerView.Adapter.StateRestorationPolicy.PREVENT

        popularConcat = ConcatAdapter(concatConfig, popularSkeleton, popularAdapter)
        nearConcat    = ConcatAdapter(concatConfig, nearSkeleton, nearAdapter)
    }

    /* ───────────────────────── RecyclerViews Setup ───────────────────── */

    private fun setupRecyclerViews() = with(binding) {
        // generic tuning to avoid repetition
        fun RecyclerView.tune(horizontal: Boolean) {
            setHasFixedSize(true)
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
            setItemViewCacheSize(if (horizontal) 8 else 2)
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutManager = LinearLayoutManager(
                requireContext(),
                if (horizontal) LinearLayoutManager.HORIZONTAL else LinearLayoutManager.VERTICAL,
                false
            ).also { lm ->
                if (horizontal) lm.initialPrefetchItemCount = 6
                else lm.isItemPrefetchEnabled = false
            }
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
            tune(horizontal = true)
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
            tune(horizontal = false)
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

    /* ───────────────────── Pull-to-refresh & Skeleton Swap ───────────── */

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
            swipeRefresh.isRefreshing = false
            startNetworkRefresh()
            vm.pullToRefresh()
        }
    }

    private inline fun RecyclerView.batchLayout(block: () -> Unit) {
        suppressLayout(true)
        try { block() } finally { suppressLayout(false) }
    }

    private fun startNetworkRefresh() {
        scrollHomeToTop()

        binding.rvFeatured.batchLayout {
            popularSkeleton.show(POPULAR_SKELETON_COUNT)
            popularAdapter.updateItems(emptyList(), vm.ui.value.currentLocation)
            popularItems = emptyList()
        }
        binding.rvNearMe.batchLayout {
            nearSkeleton.show(NEAR_SKELETON_COUNT)
            nearAdapter.updateItems(emptyList(), vm.ui.value.currentLocation)
            nearItems = emptyList()
        }
    }

    private fun showSkeletons(popularCount: Int, nearCount: Int) {
        popularSkeleton.show(popularCount)
        nearSkeleton.show(nearCount)
    }

    private fun hideSkeletons() {
        popularSkeleton.hide()
        nearSkeleton.hide()
    }

    private fun clearDataAdapters() {
        popularAdapter.updateItems(emptyList(), vm.ui.value.currentLocation)
        nearAdapter.updateItems(emptyList(), vm.ui.value.currentLocation)
        popularItems = emptyList()
        nearItems = emptyList()
    }

    /* ───────────────────────────── Collectors ────────────────────────── */

    private fun setupCollectors() {
        collect(vm.effects) { eff ->
            if (eff is Effect.Message)
                Toast.makeText(requireContext(), eff.text, Toast.LENGTH_SHORT).show()
        }

        collect(vm.ui.flow) { ui ->
            binding.progressBar.isGone = true
            categoryAdapter.updateCategories(ui.categories)

            val combinedRefreshing = ui.isRefreshing
            val hasDataAlready =
                (vm.nearMe.value is Loadable.Data && (vm.nearMe.value as Loadable.Data).value.isNotEmpty()) ||
                        (vm.featured.value is Loadable.Data && (vm.featured.value as Loadable.Data).value.isNotEmpty())

            // Only flip to skeletons if we DON'T already have content shown
            if (!lastIsRefreshing && combinedRefreshing && !hasDataAlready) {
                startNetworkRefresh()
            }
            lastIsRefreshing = combinedRefreshing
        }

        // Featured (Popular)
        collectLoadable(vm.featured) { loadable ->
            when (loadable) {
                Loadable.Uninitialized, Loadable.Loading -> {
                    popularSkeleton.show(POPULAR_SKELETON_COUNT)
                    binding.rvFeatured.isVisible = true
                }
                is Loadable.Data -> {
                    popularItems = loadable.value
                    binding.root.post { photoCache.warm(popularItems, take = PRELOAD_AHEAD * 2) }

                    hideSkeletonsIfBothResolved(featuredResolved = true, nearResolved = vm.nearMe.value is Loadable.Data)
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
                    nearSkeleton.show(NEAR_SKELETON_COUNT)
                    binding.rvNearMe.isVisible = true
                }
                is Loadable.Data -> {
                    nearItems = loadable.value
                    binding.root.post { photoCache.warm(nearItems, take = PRELOAD_AHEAD * 2) }

                    hideSkeletonsIfBothResolved(
                        featuredResolved = vm.featured.value is Loadable.Data,
                        nearResolved = true
                    )
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

    // Hide both skeletons when both sections have resolved to Data at least once
    private fun hideSkeletonsIfBothResolved(featuredResolved: Boolean, nearResolved: Boolean) {
        if (featuredResolved && nearResolved) hideSkeletons()
    }

    /* ───────────────────── Location Permissions & Fetch ───────────────── */

    private fun ensureLocation() {
        if (hasLocationPermission()) {
            fetchLocation()
        } else {
            requestPerms.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun hasLocationPermission(): Boolean {
        val ctx = requireContext()
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        return fine || coarse
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

    /* ───────────────────────────── UI Helpers ────────────────────────── */

    private fun scrollHomeToTop() {
        // Expand app bar if collapsed
        binding.appBar.setExpanded(true, true)

        // Scroll the outer container to top
        binding.rootScroll.post { binding.rootScroll.smoothScrollTo(0, 0) }

        // Also reset inner RV positions (defensive)
        binding.rvNearMe.stopScroll()
        binding.rvNearMe.scrollToPosition(0)

        binding.rvFeatured.stopScroll()
        binding.rvFeatured.scrollToPosition(0)
    }

    private fun navigateToSearch(
        openFilters: Boolean = false,
        prefill: String? = null,
        radiusKm: Int? = null,
        strictOnly: Boolean? = null
    ) {
        val args = Bundle().apply {
            putBoolean(ARG_OPEN_FILTERS, openFilters)
            prefill?.let { putString(ARG_PREFILL_QUERY, it) }
            radiusKm?.let { putInt(ARG_RADIUS_KM, it) }
            strictOnly?.let { putBoolean(ARG_STRICT_ONLY, it) }
        }
        findNavController().navigate(R.id.searchFragment, args)
    }


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
}