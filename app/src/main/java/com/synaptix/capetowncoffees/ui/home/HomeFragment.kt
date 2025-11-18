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
//* ChatGPT assisted in designing and structuring this Fragment, including lifecycle
//handling, navigation setup, and interaction with the ViewModel.
//* It also provided guidance on ConstraintLayout usage and UI event handling.
//* It also helped generate useful comments
//======================================================================================

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
import com.google.android.material.transition.platform.MaterialSharedAxis
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.databinding.FragmentHomeBinding
import com.synaptix.capetowncoffees.domain.model.Category
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite
import com.synaptix.capetowncoffees.domain.usecase.coffeePlace.CoffeePlaceUtilsUseCase
import com.synaptix.capetowncoffees.domain.usecase.connectivity.IsEffectivelyOnlineUseCase
import com.synaptix.capetowncoffees.domain.usecase.connectivity.ObserveConnectivityStateUseCase
import com.synaptix.capetowncoffees.ui.common.SkeletonAdapter
import com.synaptix.capetowncoffees.ui.common.observeConnectivity
import com.synaptix.capetowncoffees.ui.common.showOfflineBannerWhenNeeded
import com.synaptix.capetowncoffees.ui.common.viewmodel.Effect
import com.synaptix.capetowncoffees.ui.common.viewmodel.Loadable
import com.synaptix.capetowncoffees.ui.common.viewmodel.collect
import com.synaptix.capetowncoffees.ui.common.viewmodel.collectLoadable
import com.synaptix.capetowncoffees.ui.common.viewmodel.start
import com.synaptix.capetowncoffees.ui.home.adapter.CategoryAdapter
import com.synaptix.capetowncoffees.ui.home.adapter.CoffeePlaceItemAdapter
import com.synaptix.capetowncoffees.ui.lists.AddPlacesToList.AddPlacesToListBottomSheet
import com.synaptix.capetowncoffees.util.ImagePreloadUtil
import com.synaptix.capetowncoffees.util.PhotoUrlCache
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment() {

    // ─────────── View & ViewModel ───────────
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val vm: HomeViewModel by activityViewModels()

    // ─────────── DI & Utilities ───────────
    @Inject lateinit var coffeePlaceUtils: CoffeePlaceUtilsUseCase
    @Inject lateinit var placeItemAdapterFactory: CoffeePlaceItemAdapter.Factory
    @Inject lateinit var observeConnectivityStateUseCase: ObserveConnectivityStateUseCase
    @Inject lateinit var isEffectivelyOnlineUseCase: IsEffectivelyOnlineUseCase
    private lateinit var photoCache: PhotoUrlCache

    // ─────────── Adapters & Sections ───────────
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var popularAdapter: CoffeePlaceItemAdapter
    private lateinit var nearAdapter: CoffeePlaceItemAdapter

    // Section wrapper to keep per-section state together
    private data class Section(
        val name: String,
        val skeletonCount: Int,
        val skeleton: SkeletonAdapter,
        val concatAdapter: ConcatAdapter,
        val recyclerView: RecyclerView,
        val sizeProvider: ViewPreloadSizeProvider<String>,
        var items: List<CoffeePlaceLite> = emptyList(),
        var preloader: RecyclerView.OnScrollListener? = null
    )

    private lateinit var popularSection: Section
    private lateinit var nearSection: Section

    // ─────────── Image Preloading ───────────
    private val popularSizeProvider = ViewPreloadSizeProvider<String>()
    private val nearSizeProvider = ViewPreloadSizeProvider<String>()

    // ─────────── Scroll & Refresh State ───────────
    private var appBarOffset: Int = 0
    private var lastIsRefreshing: Boolean = false

    // Track pull-to-refresh state to avoid conflicts with load states.
    private var pullToRefreshInProgress: Boolean = false
    // When true we temporarily show skeleton-only adapters to visibly replace content during pull-to-refresh
    private var showingSkeletonOnlyForPull: Boolean = false

    // ─────────── Permissions ───────────
    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val ok = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) {
            fetchLocation()
        } else {
            Toast.makeText(
                requireContext(),
                R.string.location_permission_required,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ─────────── Constants ───────────
    companion object {
        const val PRELOAD_AHEAD = 6
        const val POPULAR_SKELETON_COUNT = 5
        const val NEAR_SKELETON_COUNT = 5

        const val ARG_OPEN_FILTERS  = "open_filters"
        const val ARG_PREFILL_QUERY = "prefill_query"
        const val ARG_RADIUS_KM     = "radius_km"
        const val ARG_STRICT_ONLY   = "strict_only"
    }

    // ─────────── Shared Recycler Config ───────────
    private val concatConfig: ConcatAdapter.Config by lazy {
        ConcatAdapter.Config.Builder()
            .setIsolateViewTypes(true)
            .setStableIdMode(ConcatAdapter.Config.StableIdMode.ISOLATED_STABLE_IDS)
            .build()
    }

    // ─────────── Lifecycle ───────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.Y, true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Y, false)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
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

        // Show skeletons immediately (if online) so UI isn't blank while location/network begin
        showInitialSkeletonsIfNeeded()

        // Simplified connectivity observation using extension + helpers
        observeConnectivity(observeConnectivityStateUseCase) { state ->
            val online = state.effectiveIsOnline && !vm.ui.value.isOffline
            updateOnlineState(online)
        }

        // Initialize visibility based on current effective connectivity and VM offline flag to avoid flashes
        updateOnlineState(isEffectivelyOnlineUseCase() && !vm.ui.value.isOffline)

        binding.searchCard.setOnClickListener { navigateToSearch(openFilters = false) }
        binding.root.findViewById<View?>(R.id.btnQuickFilterHit)?.setOnClickListener {
            navigateToSearch(openFilters = true)
        }
        binding.searchCard.setOnLongClickListener {
            navigateToSearch(openFilters = true)
            true
        }

        // Show offline snackbar automatically when connectivity changes
        showOfflineBannerWhenNeeded(observeConnectivityStateUseCase, binding.root)

        // Retry button uses simplified refresh logic
        binding.offlineBanner.btnRetry.setOnClickListener {
            if (effectiveOnline()) {
                vm.onOfflineBannerRetry()
            } else {
                showOfflineSnackbar()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val hasLocation = vm.ui.value.currentLocation != null
        if (hasLocation && vm.shouldRefreshForSearchParamsChange() && effectiveOnline()) {
            // Keep old data; only show skeletons if empty
            showInitialSkeletonsIfNeeded()
            vm.refresh(force = true)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        detachPreloaders()
        _binding = null
    }

    // ─────────── Adapters Setup ───────────
    private fun initAdapters() {
        categoryAdapter = CategoryAdapter { category: Category ->
            vm.onCategorySelected(category)
        }

        val itemClick: (CoffeePlaceItemAdapter.Click) -> Unit = { click ->
            when (click) {
                is CoffeePlaceItemAdapter.Click.Open ->
                    navigateToCafeDetailsId(click.id)
                is CoffeePlaceItemAdapter.Click.ToggleFavorite ->
                    Toast.makeText(
                        requireContext(),
                        "Fav ${click.id}: ${click.newValue}",
                        Toast.LENGTH_SHORT
                    ).show()
                is CoffeePlaceItemAdapter.Click.AddToList ->
                    showAddToListBottomSheet(click.id)
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

        val popularSkeleton = SkeletonAdapter(
            count = POPULAR_SKELETON_COUNT,
            layoutResId = R.layout.item_place_skeleton
        )
        val nearSkeleton = SkeletonAdapter(
            count = NEAR_SKELETON_COUNT,
            layoutResId = R.layout.item_place_skeleton
        )

        popularAdapter.stateRestorationPolicy =
            RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        nearAdapter.stateRestorationPolicy =
            RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        popularSkeleton.stateRestorationPolicy =
            RecyclerView.Adapter.StateRestorationPolicy.PREVENT
        nearSkeleton.stateRestorationPolicy =
            RecyclerView.Adapter.StateRestorationPolicy.PREVENT

        val popularConcat = ConcatAdapter(concatConfig, popularSkeleton, popularAdapter)
        val nearConcat = ConcatAdapter(concatConfig, nearSkeleton, nearAdapter)

        // Bind sections to RecyclerViews
        popularSection = Section(
            name = "featured",
            skeletonCount = POPULAR_SKELETON_COUNT,
            skeleton = popularSkeleton,
            concatAdapter = popularConcat,
            recyclerView = binding.rvFeatured,
            sizeProvider = popularSizeProvider
        )

        nearSection = Section(
            name = "near",
            skeletonCount = NEAR_SKELETON_COUNT,
            skeleton = nearSkeleton,
            concatAdapter = nearConcat,
            recyclerView = binding.rvNearMe,
            sizeProvider = nearSizeProvider
        )

        binding.rvFeatured.adapter = popularSection.concatAdapter
        binding.rvNearMe.adapter = nearSection.concatAdapter
    }

    // ─────────── RecyclerViews Setup ───────────
    private fun setupRecyclerViews() = with(binding) {
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

        rvFeatured.tune(horizontal = true)
        rvNearMe.tune(horizontal = false)
    }

    // ─────────── Pull-to-Refresh & Skeleton Swap ───────────
    private fun setupPullToRefresh() = with(binding) {
        swipeRefresh.isEnabled = true
        appBar.doOnLayout {
            val start = appBar.height + dp(8)
            val end = start + dp(64)
            swipeRefresh.setProgressViewOffset(true, start, end)
        }
        appBar.addOnOffsetChangedListener { _, verticalOffset ->
            appBarOffset = verticalOffset
        }
        swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            val contentNotAtTop = rootScroll.canScrollVertically(-1)
            val appBarCollapsed = appBarOffset != 0
            val currentlyRefreshing = vm.ui.value.isRefreshing
            contentNotAtTop || appBarCollapsed || currentlyRefreshing
        }
        swipeRefresh.setOnRefreshListener {
            swipeRefresh.isRefreshing = false
            if (effectiveOnline()) {
                // Mark pull-to-refresh mode and force skeletons on both sections
                pullToRefreshInProgress = true
                showPullToRefreshSkeletons()
                vm.pullToRefresh()
            } else {
                showOfflineSnackbar()
            }
        }
    }

    // ─────────── Collectors ───────────
    private fun setupCollectors() {
        collect(vm.effects) { eff ->
            if (eff is Effect.Message) {
                Toast.makeText(requireContext(), eff.text, Toast.LENGTH_SHORT).show()
            }
        }

        collect(vm.ui.flow) { ui ->
            binding.progressBar.isGone = true
            categoryAdapter.updateCategories(ui.categories ?: emptyList())
            lastIsRefreshing = ui.isRefreshing
            binding.offlineBanner.root.isVisible = ui.isOffline
            updateSectionsVisibility()
        }

        collectLoadable(vm.featured) { loadable ->
            when (loadable) {
                Loadable.Uninitialized,
                Loadable.Loading -> {
                    popularSection.ensureInitialSkeletonIfNeeded()
                }
                is Loadable.Data -> {
                    popularSection.items = loadable.value
                    binding.root.post {
                        photoCache.warm(popularSection.items, take = PRELOAD_AHEAD * 2)
                    }
                    popularSection.skeleton.hide()
                    popularAdapter.updateItems(loadable.value, vm.ui.value.currentLocation)
                    attachPreloadersIfNeeded()
                }
                is Loadable.Error -> {
                    popularSection.skeleton.hide()
                }
            }
            syncSkeletonsResolution()
            updateSectionsVisibility()
        }

        collectLoadable(vm.nearMe) { loadable ->
            when (loadable) {
                Loadable.Uninitialized,
                Loadable.Loading -> {
                    nearSection.ensureInitialSkeletonIfNeeded()
                }
                is Loadable.Data -> {
                    nearSection.items = loadable.value
                    binding.root.post {
                        photoCache.warm(nearSection.items, take = PRELOAD_AHEAD * 2)
                    }
                    nearSection.skeleton.hide()
                    nearAdapter.updateItems(loadable.value, vm.ui.value.currentLocation)
                    attachPreloadersIfNeeded()
                }
                is Loadable.Error -> {
                    nearSection.skeleton.hide()
                }
            }
            syncSkeletonsResolution()
            updateSectionsVisibility()
        }
    }

    // ─────────── Skeleton helpers ───────────
    private fun Section.ensureInitialSkeletonIfNeeded() {
        if (!effectiveOnline()) return
        if (items.isEmpty() && skeleton.itemCount == 0 && !pullToRefreshInProgress) {
            skeleton.show(skeletonCount)
        }
    }

    private fun Section.hideSkeletonIfEmpty() {
        if (items.isEmpty()) {
            skeleton.hide()
        }
    }

    private fun Section.showSkeletonForPullToRefresh() {
        if (!effectiveOnline()) return
        skeleton.show(skeletonCount)
    }

    /** Show skeletons for sections that are currently empty, keeping lists visible. */
    private fun showInitialSkeletonsIfNeeded() {
        if (!effectiveOnline()) return
        popularSection.ensureInitialSkeletonIfNeeded()
        nearSection.ensureInitialSkeletonIfNeeded()
        updateSectionsVisibility()
    }

    /** Show skeletons for both sections during a pull-to-refresh, regardless of existing data. */
    private fun showPullToRefreshSkeletons() {
        if (!effectiveOnline()) return

        // Expand and bring content to top so skeletons are visible
        binding.appBar.setExpanded(true, true)
        binding.rootScroll.post { binding.rootScroll.smoothScrollTo(0, 0) }

        popularSection.showSkeletonForPullToRefresh()
        nearSection.showSkeletonForPullToRefresh()

        // Swap to skeleton-only adapters so the skeletons visually replace data without clearing adapters' state
        try {
            if (!showingSkeletonOnlyForPull) {
                binding.rvFeatured.adapter = ConcatAdapter(concatConfig, popularSection.skeleton)
                binding.rvNearMe.adapter = ConcatAdapter(concatConfig, nearSection.skeleton)
                showingSkeletonOnlyForPull = true
            }
        } catch (_: Throwable) {
            // best-effort swap; fall back to scrolling to top if swap fails
            scrollRecyclerToTop(binding.rvFeatured)
            scrollRecyclerToTop(binding.rvNearMe)
        }

        // Ensure recycler views are at position 0 so skeleton rows are visible
        scrollRecyclerToTop(binding.rvFeatured)
        scrollRecyclerToTop(binding.rvNearMe)

        updateSectionsVisibility()
    }

    private fun scrollRecyclerToTop(rv: RecyclerView) {
        rv.post {
            try {
                rv.stopScroll()
                rv.scrollToPosition(0)
            } catch (_: Throwable) {
                // best effort
            }
        }
    }

    private fun syncSkeletonsResolution() {
        val featuredResolved =
            vm.featured.value is Loadable.Data || vm.featured.value is Loadable.Error
        val nearResolved =
            vm.nearMe.value is Loadable.Data || vm.nearMe.value is Loadable.Error

        if (featuredResolved && nearResolved) {
            hideSkeletons()
            pullToRefreshInProgress = false
        }
    }

    private fun hideSkeletons() {
        popularSection.skeleton.hide()
        nearSection.skeleton.hide()
        // If we temporarily swapped adapters for pull-to-refresh, restore the original concat adapters
        if (showingSkeletonOnlyForPull) {
            try {
                binding.rvFeatured.adapter = popularSection.concatAdapter
                binding.rvNearMe.adapter = nearSection.concatAdapter
            } catch (_: Throwable) {
                // ignore
            }
            showingSkeletonOnlyForPull = false
            // Reattach preloaders now that the real adapters are back
            attachPreloadersIfNeeded()
        }
        updateSectionsVisibility()
    }

    // ─────────── Location Permissions & Fetch ───────────
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
        val fine =
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        val coarse =
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
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
                Toast.makeText(
                    requireContext(),
                    R.string.location_fetch_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    // ─────────── Preloaders & Connectivity ───────────
    private fun Section.attachPreloaderIfNeeded() {
        if (!effectiveOnline()) return
        if (preloader != null || items.isEmpty()) return

        preloader = ImagePreloadUtil.attachWithSkeleton(
            recyclerView = recyclerView,
            fragment = this@HomeFragment,
            sizeProvider = sizeProvider,
            maxPreload = PRELOAD_AHEAD,
            skeletonCountProvider = { skeleton.itemCount },
            dataItemCountProvider = { items.size },
            urlProviderAtAdapterIndex = { idx ->
                items.getOrNull(idx)?.let { place -> photoCache.peekOrCompute(place) }
            }
        )
    }

    private fun Section.detachPreloaderIfNeeded() {
        preloader?.let { listener ->
            recyclerView.removeOnScrollListener(listener)
        }
        preloader = null
    }

    private fun attachPreloadersIfNeeded() {
        if (!effectiveOnline()) return
        popularSection.attachPreloaderIfNeeded()
        nearSection.attachPreloaderIfNeeded()
    }

    private fun detachPreloaders() {
        popularSection.detachPreloaderIfNeeded()
        nearSection.detachPreloaderIfNeeded()
    }

    private fun effectiveOnline(): Boolean =
        isEffectivelyOnlineUseCase() && !vm.ui.value.isOffline

    private fun updateOnlineState(online: Boolean) {
        if (!online) {
            detachPreloaders()
            popularSection.hideSkeletonIfEmpty()
            nearSection.hideSkeletonIfEmpty()
        } else {
            attachPreloadersIfNeeded()
            // If we regained connectivity and don't have data trigger refresh
            if ((popularSection.items.isEmpty() || nearSection.items.isEmpty()) &&
                vm.ui.value.currentLocation != null
            ) {
                showInitialSkeletonsIfNeeded()
                vm.refresh(force = true)
            }
        }
        updateSectionsVisibility()
    }

    private fun showOfflineSnackbar() {
        com.google.android.material.snackbar.Snackbar
            .make(binding.root, "Offline", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
            .show()
    }

    private fun updateSectionsVisibility() = with(binding) {
        val online = effectiveOnline()
        if (!online) {
            tvFeaturedTitle.isGone = true
            rvFeatured.isGone = true
            tvNearTitle.isGone = true
            rvNearMe.isGone = true
            return
        }

        val showFeatured =
            popularSection.items.isNotEmpty() || popularSection.skeleton.itemCount > 0
        val showNear =
            nearSection.items.isNotEmpty() || nearSection.skeleton.itemCount > 0

        tvFeaturedTitle.isGone = !showFeatured
        rvFeatured.isGone = !showFeatured
        tvNearTitle.isGone = !showNear
        rvNearMe.isGone = !showNear
    }

    // ─────────── UI Helpers ───────────
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
        findNavController().navigate(
            R.id.action_homeFragment_to_cafeDetailFragment,
            bundle
        )
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
