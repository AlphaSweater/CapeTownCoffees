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
import com.synaptix.capetowncoffees.ui.common.SkeletonAdapter
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
import com.synaptix.capetowncoffees.ui.common.showOfflineBannerWhenNeeded
import com.synaptix.capetowncoffees.ui.common.observeConnectivity
import com.synaptix.capetowncoffees.domain.usecase.connectivity.ObserveConnectivityStateUseCase
import com.synaptix.capetowncoffees.domain.usecase.connectivity.IsEffectivelyOnlineUseCase

@AndroidEntryPoint
class HomeFragment : Fragment() {

    // ─────────── View & ViewModel ───────────
    // Binding is scoped to the view lifecycle to avoid leaks; VM is activity-scoped for shared state.
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val vm: HomeViewModel by activityViewModels()

    // ─────────── DI & Utilities ───────────
    // Photo resolver + factory for item adapters shared across sections.
    @Inject lateinit var coffeePlaceUtils: CoffeePlaceUtilsUseCase
    @Inject lateinit var placeItemAdapterFactory: CoffeePlaceItemAdapter.Factory
    @Inject lateinit var observeConnectivityStateUseCase: ObserveConnectivityStateUseCase
    @Inject lateinit var isEffectivelyOnlineUseCase: IsEffectivelyOnlineUseCase
    private lateinit var photoCache: PhotoUrlCache

    // ─────────── Adapters & Data ───────────
    // We keep two sections (Featured / Near Me) with skeletons and concat containers.
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var popularAdapter: CoffeePlaceItemAdapter
    private lateinit var nearAdapter: CoffeePlaceItemAdapter

    private var popularItems: List<CoffeePlaceLite> = emptyList()
    private var nearItems: List<CoffeePlaceLite> = emptyList()

    private lateinit var popularSkeleton: SkeletonAdapter
    private lateinit var nearSkeleton: SkeletonAdapter
    private lateinit var popularConcat: ConcatAdapter
    private lateinit var nearConcat: ConcatAdapter

    // ─────────── Image Preloading ───────────
    // Size providers let Glide preloader know the target image dimensions.
    private val popularSizeProvider = ViewPreloadSizeProvider<String>()
    private val nearSizeProvider = ViewPreloadSizeProvider<String>()

    // Keep references to the preload scroll listeners so we can remove them when offline
    private var popularPreloader: RecyclerView.OnScrollListener? = null
    private var nearPreloader: RecyclerView.OnScrollListener? = null

    // ─────────── Scroll & Refresh State ───────────
    // Track app bar offset and refresh transitions to drive UX.
    private var appBarOffset: Int = 0
    private var lastIsRefreshing: Boolean = false

    // ─────────── Permissions ───────────
    // Location permission gate; if granted we fetch a coarse last-known location.
    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val ok = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) fetchLocation() else {
            Toast.makeText(requireContext(), R.string.location_permission_required, Toast.LENGTH_LONG).show()
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
    // Isolated view types + stable ids avoid cross-section collisions.
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
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
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
            if (effectiveOnline()) vm.onOfflineBannerRetry() else com.google.android.material.snackbar.Snackbar.make(binding.root, "Offline", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
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
    // Creates adapters and their concat containers; wires item click routing.
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

    // ─────────── RecyclerViews Setup ───────────
    // Common tuning for smooth scroll, caching, and image preloading per section.
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
                if (horizontal) lm.initialPrefetchItemCount = 6 else lm.isItemPrefetchEnabled = false
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
        }

        rvNearMe.apply {
            tune(horizontal = false)
            adapter = nearConcat
        }
    }

    // ─────────── Pull-to-Refresh & Skeleton Swap ───────────
    // Ensures pull-to-refresh only works when content is at top and app bar is expanded.
    private fun setupPullToRefresh() = with(binding) {
        swipeRefresh.isEnabled = true
        appBar.doOnLayout {
            val start = appBar.height + dp(8)
            val end   = start + dp(64)
            swipeRefresh.setProgressViewOffset(true, start, end)
        }
        appBar.addOnOffsetChangedListener { _, verticalOffset -> appBarOffset = verticalOffset }
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
                com.google.android.material.snackbar.Snackbar.make(binding.root, "Offline", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    // ─────────── Collectors ───────────
    // React to VM effects and streams, and update UI/adapters accordingly.
    private fun setupCollectors() {
        collect(vm.effects) { eff ->
            if (eff is Effect.Message)
                Toast.makeText(requireContext(), eff.text, Toast.LENGTH_SHORT).show()
        }

        collect(vm.ui.flow) { ui ->
            binding.progressBar.isGone = true
            categoryAdapter.updateCategories(ui.categories)
            lastIsRefreshing = ui.isRefreshing
            binding.offlineBanner.root.isVisible = ui.isOffline
            updateSectionsVisibility()
        }

        collectLoadable(vm.featured) { loadable ->
            updateFeaturedSkeletonForLoadState(loadable)
            when (loadable) {
                is Loadable.Data -> {
                    popularItems = loadable.value
                    binding.root.post { photoCache.warm(popularItems, take = PRELOAD_AHEAD * 2) }
                    hideSkeletonsIfBothResolved(
                        featuredResolved = true,
                        nearResolved = vm.nearMe.value is Loadable.Data
                    )
                    popularAdapter.updateItems(loadable.value, vm.ui.value.currentLocation)
                    attachPreloadersIfNeeded()
                }
                is Loadable.Error -> {
                    // If one section errors but the other succeeds, we still want to hide skeletons
                    hideSkeletonsIfBothResolved(
                        featuredResolved = true,
                        nearResolved = vm.nearMe.value is Loadable.Data || vm.nearMe.value is Loadable.Error
                    )
                }
                Loadable.Uninitialized, Loadable.Loading -> Unit
            }
            updateSectionsVisibility()
        }

        collectLoadable(vm.nearMe) { loadable ->
            updateNearSkeletonForLoadState(loadable)
            when (loadable) {
                is Loadable.Data -> {
                    nearItems = loadable.value
                    binding.root.post { photoCache.warm(nearItems, take = PRELOAD_AHEAD * 2) }
                    hideSkeletonsIfBothResolved(
                        featuredResolved = vm.featured.value is Loadable.Data,
                        nearResolved = true
                    )
                    nearAdapter.updateItems(loadable.value, vm.ui.value.currentLocation)
                    attachPreloadersIfNeeded()
                }
                is Loadable.Error -> {
                    hideSkeletonsIfBothResolved(
                        featuredResolved = vm.featured.value is Loadable.Data || vm.featured.value is Loadable.Error,
                        nearResolved = true
                    )
                }
                Loadable.Uninitialized, Loadable.Loading -> Unit
            }
            updateSectionsVisibility()
        }
    }

    // ─────────── Skeleton helpers ───────────
    /** Show skeletons for sections that are currently empty, keeping lists visible. */
    private fun showInitialSkeletonsIfNeeded() {
        if (!effectiveOnline()) return
        if (popularItems.isEmpty()) popularSkeleton.show(POPULAR_SKELETON_COUNT)
        if (nearItems.isEmpty()) nearSkeleton.show(NEAR_SKELETON_COUNT)
        updateSectionsVisibility()
    }

    /** Show skeletons for both sections during a pull-to-refresh, regardless of existing data. */
    private fun showPullToRefreshSkeletons() {
        if (!effectiveOnline()) return
        // Expand and bring content to top so skeletons are visible
        binding.appBar.setExpanded(true, true)
        binding.rootScroll.post { binding.rootScroll.smoothScrollTo(0, 0) }

        popularSkeleton.show(POPULAR_SKELETON_COUNT)
        nearSkeleton.show(NEAR_SKELETON_COUNT)

        // Swap to skeleton-only adapters so the skeletons visually replace data without clearing adapters' state
        try {
            if (!showingSkeletonOnlyForPull) {
                binding.rvFeatured.adapter = ConcatAdapter(concatConfig, popularSkeleton)
                binding.rvNearMe.adapter = ConcatAdapter(concatConfig, nearSkeleton)
                showingSkeletonOnlyForPull = true
            }
        } catch (_: Throwable) {
            // best-effort swap; fall back to scrolling to top if swap fails
            binding.rvFeatured.post { binding.rvFeatured.scrollToPosition(0) }
            binding.rvNearMe.post { binding.rvNearMe.scrollToPosition(0) }
        }

        // Ensure recycler views are at position 0 so skeleton rows (which are prepended via ConcatAdapter)
        // are visible to the user even when data exists.
        binding.rvFeatured.post {
            try {
                binding.rvFeatured.stopScroll()
                binding.rvFeatured.scrollToPosition(0)
            } catch (_: Throwable) { /* best effort */ }
        }
        binding.rvNearMe.post {
            try {
                binding.rvNearMe.stopScroll()
                binding.rvNearMe.scrollToPosition(0)
            } catch (_: Throwable) { /* best effort */ }
        }

        updateSectionsVisibility()
    }

    /** Update skeletons for FEATURED based on load state and current items. */
    private fun updateFeaturedSkeletonForLoadState(loadable: Loadable<*>) {
        when (loadable) {
            Loadable.Uninitialized, Loadable.Loading -> {
                // If we're in pull-to-refresh mode, skeletons are already forced on.
                if (!pullToRefreshInProgress && effectiveOnline() && popularItems.isEmpty() && popularSkeleton.itemCount == 0) {
                    popularSkeleton.show(POPULAR_SKELETON_COUNT)
                }
            }
            is Loadable.Data, is Loadable.Error -> {
                // handled in hideSkeletonsIfBothResolved
            }
        }
    }

    private fun updateNearSkeletonForLoadState(loadable: Loadable<*>) {
        when (loadable) {
            Loadable.Uninitialized, Loadable.Loading -> {
                if (!pullToRefreshInProgress && effectiveOnline() && nearItems.isEmpty() && nearSkeleton.itemCount == 0) {
                    nearSkeleton.show(NEAR_SKELETON_COUNT)
                }
            }
            is Loadable.Data, is Loadable.Error -> { }
        }
    }

    private fun hideSkeletonsIfBothResolved(featuredResolved: Boolean, nearResolved: Boolean) {
        if (featuredResolved && nearResolved) {
            hideSkeletons()
            pullToRefreshInProgress = false
        }
        updateSectionsVisibility()
    }

    private fun hideSkeletons() {
        popularSkeleton.hide()
        nearSkeleton.hide()
        // If we temporarily swapped adapters for pull-to-refresh, restore the original concat adapters
        if (showingSkeletonOnlyForPull) {
            try {
                binding.rvFeatured.adapter = popularConcat
                binding.rvNearMe.adapter = nearConcat
            } catch (_: Throwable) { /* ignore */ }
            showingSkeletonOnlyForPull = false
            // Reattach preloaders now that the real adapters are back
            attachPreloadersIfNeeded()
        }
    }

    // ─────────── Location Permissions & Fetch ───────────
    // We attempt a last-known location to seed distance labels.
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
        AddPlacesToListBottomSheet.new(id).show(childFragmentManager, "AddPlacesToListBottomSheet")
    }

    private fun navigateToCafeDetailsId(id: String) {
        val bundle = Bundle().apply { putString("placeId", id) }
        findNavController().navigate(R.id.action_homeFragment_to_cafeDetailFragment, bundle)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun effectiveOnline(): Boolean = isEffectivelyOnlineUseCase() && !vm.ui.value.isOffline

    private fun updateOnlineState(online: Boolean) {
        if (!online) {
            detachPreloaders()
            if (popularItems.isEmpty()) popularSkeleton.hide()
            if (nearItems.isEmpty()) nearSkeleton.hide()
        } else {
            attachPreloadersIfNeeded()
            // If we regained connectivity and don't have data trigger refresh
            if ((popularItems.isEmpty() || nearItems.isEmpty()) && vm.ui.value.currentLocation != null) {
                showInitialSkeletonsIfNeeded()
                vm.refresh(force = true)
            }
        }
        updateSectionsVisibility()
    }

    private fun attachPreloadersIfNeeded() {
        if (!effectiveOnline()) return
        if (popularPreloader == null && popularItems.isNotEmpty()) {
            popularPreloader = ImagePreloadUtil.attachWithSkeleton(
                recyclerView = binding.rvFeatured,
                fragment = this,
                sizeProvider = popularSizeProvider,
                maxPreload = PRELOAD_AHEAD,
                skeletonCountProvider = { popularSkeleton.itemCount },
                dataItemCountProvider = { popularItems.size },
                urlProviderAtAdapterIndex = { idx -> popularItems.getOrNull(idx)?.let { place -> photoCache.peekOrCompute(place) } }
            )
        }
        if (nearPreloader == null && nearItems.isNotEmpty()) {
            nearPreloader = ImagePreloadUtil.attachWithSkeleton(
                recyclerView = binding.rvNearMe,
                fragment = this,
                sizeProvider = nearSizeProvider,
                maxPreload = PRELOAD_AHEAD,
                skeletonCountProvider = { nearSkeleton.itemCount },
                dataItemCountProvider = { nearItems.size },
                urlProviderAtAdapterIndex = { idx -> nearItems.getOrNull(idx)?.let { place -> photoCache.peekOrCompute(place) } }
            )
        }
    }

    private fun detachPreloaders() {
        popularPreloader?.let { binding.rvFeatured.removeOnScrollListener(it) }
        nearPreloader?.let { binding.rvNearMe.removeOnScrollListener(it) }
        popularPreloader = null
        nearPreloader = null
    }

    private fun updateSectionsVisibility() = with(binding) {
        val online = effectiveOnline()
        if (!online) {
            tvFeaturedTitle.isGone = true
            rvFeatured.isGone = true
            tvNearTitle.isGone = true
            rvNearMe.isGone = true
            rvCategories.isGone = true
            return
        }
        val showFeatured = popularItems.isNotEmpty() || popularSkeleton.itemCount > 0
        val showNear = nearItems.isNotEmpty() || nearSkeleton.itemCount > 0
        tvFeaturedTitle.isGone = !showFeatured
        rvFeatured.isGone = !showFeatured
        tvNearTitle.isGone = !showNear
        rvNearMe.isGone = !showNear
        rvCategories.isGone = false
    }

    // ─────────── State ───────────
    // Track pull-to-refresh state to avoid conflicts with load states.
    private var pullToRefreshInProgress: Boolean = false
    // When true we temporarily show skeleton-only adapters to visibly replace content during pull-to-refresh
    private var showingSkeletonOnlyForPull: Boolean = false
}
