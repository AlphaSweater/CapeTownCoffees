package com.synaptix.capetowncoffees.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.bumptech.glide.Glide
import com.bumptech.glide.ListPreloader
import com.bumptech.glide.RequestManager
import com.bumptech.glide.integration.recyclerview.RecyclerViewPreloader
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.util.ViewPreloadSizeProvider
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
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
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.min
import kotlinx.coroutines.launch

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

    // Lists for preloader
    private var popularItems: List<CoffeePlaceLite> = emptyList()
    private var nearItems: List<CoffeePlaceLite> = emptyList()

    // URL cache for sync preloader lookups
    private val photoUrlCache = mutableMapOf<String, String?>()

    // Skeletons via Concat
    private lateinit var popularSkeleton: SkeletonAdapter
    private lateinit var nearSkeleton: SkeletonAdapter

    private lateinit var popularConcat: ConcatAdapter
    private lateinit var nearConcat: ConcatAdapter

    // Shared pool between lists
    private val sharedPool = RecyclerView.RecycledViewPool()

    // Preloader tuning
    private val PRELOAD_AHEAD = 6

    // Size providers that learn the real ImageView size from the adapter
    private val popularSizeProvider = ViewPreloadSizeProvider<String>()
    private val nearSizeProvider = ViewPreloadSizeProvider<String>()

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeNewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        start(vm)
        initAdapters()
        setupRecyclerViews()
        setupScrollHandoff()
        setupCollectors()
        setupClicks()
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
        nearSkeleton = SkeletonAdapter(count = 5, layoutResId = R.layout.item_place_skeleton)

        popularConcat = ConcatAdapter(popularSkeleton, popularAdapter)
        nearConcat = ConcatAdapter(nearSkeleton, nearAdapter)
    }

    // ───────────────────────── RecyclerViews ─────────────────────────

    private fun setupRecyclerViews() = with(binding) {
        fun RecyclerView.tune(commonHorizontal: Boolean) {
            setRecycledViewPool(sharedPool)
            setHasFixedSize(true)
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
            setItemViewCacheSize(if (commonHorizontal) 8 else 2)

            val lm = if (commonHorizontal)
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            else
                LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)

            if (commonHorizontal) {
                lm.initialPrefetchItemCount = 6
            } else {
                lm.isItemPrefetchEnabled = false
            }
            layoutManager = lm
        }

        rvCategories.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
            setHasFixedSize(true)
        }

        rvFeatured.apply {
            tune(commonHorizontal = true)
            adapter = popularConcat
            attachPreloader(
                items = { popularItems },
                skeletonCount = { popularSkeleton.itemCount },
                maxPreload = PRELOAD_AHEAD,
                sizeProvider = popularSizeProvider
            )
        }

        rvNearMe.apply {
            tune(commonHorizontal = false)
            adapter = nearConcat
            enforceBoundedHeightIfNeeded(this, dp(720))
            attachPreloader(
                items = { nearItems },
                skeletonCount = { nearSkeleton.itemCount },
                maxPreload = PRELOAD_AHEAD,
                sizeProvider = nearSizeProvider
            )
        }
    }

    /** Outer/inner scroll handoff to prevent half-section split and jitter. */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupScrollHandoff() {
        val root: NestedScrollView = binding.rootScroll
        val child: RecyclerView = binding.rvNearMe

        child.isNestedScrollingEnabled = false
        child.overScrollMode = View.OVER_SCROLL_NEVER
        root.overScrollMode = View.OVER_SCROLL_NEVER

        root.setOnScrollChangeListener { _: View, _: Int, _: Int, _: Int, _: Int ->
            val atBottom = !root.canScrollVertically(1)
            if (atBottom && !child.isNestedScrollingEnabled) {
                child.isNestedScrollingEnabled = true
                child.parent?.requestDisallowInterceptTouchEvent(true)
            } else if (!atBottom && child.isNestedScrollingEnabled) {
                child.isNestedScrollingEnabled = false
                child.stopScroll()
            }
        }

        var lastY = 0f
        var lastX = 0f
        var isClickCandidate = false
        val touchSlop = ViewConfiguration.get(requireContext()).scaledTouchSlop

        child.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastY = ev.y
                    lastX = ev.x
                    isClickCandidate = true
                    if (child.isNestedScrollingEnabled) child.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = ev.y - lastY
                    val dx = ev.x - lastX
                    if (isClickCandidate && (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop)) {
                        isClickCandidate = false
                    }
                    lastY = ev.y
                    lastX = ev.x

                    val atTopOfChild = !child.canScrollVertically(-1)
                    val draggingDown = dy > 0f
                    if (child.isNestedScrollingEnabled && atTopOfChild && draggingDown) {
                        child.isNestedScrollingEnabled = false
                        child.parent?.requestDisallowInterceptTouchEvent(false)
                    } else if (!child.isNestedScrollingEnabled && !root.canScrollVertically(1)) {
                        child.isNestedScrollingEnabled = true
                        child.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (isClickCandidate) v.performClick()
                    if (!child.isNestedScrollingEnabled) {
                        child.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    isClickCandidate = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (!child.isNestedScrollingEnabled) {
                        child.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    isClickCandidate = false
                }
            }
            false
        }
    }

    /** If RV height is wrap_content (or <= 0), set a sensible bounded height in px. */
    private fun enforceBoundedHeightIfNeeded(rv: RecyclerView, boundedHeightPx: Int) {
        val lp = rv.layoutParams
        val needsBound = lp.height <= 0
        if (needsBound) {
            lp.height = boundedHeightPx
            rv.layoutParams = lp
        }
    }

    // ───────────────────────── Clicks ─────────────────────────

    private fun setupClicks() = with(binding) {
        searchBar.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_searchFragment)
        }
    }

    // ───────────────────────── Collectors ─────────────────────────

    private fun setupCollectors() {
        collect(vm.effects) { eff ->
            when (eff) {
                is Effect.Message -> Toast.makeText(requireContext(), eff.text, Toast.LENGTH_SHORT).show()
                else -> Unit
            }
        }

        collect(vm.ui.flow) { ui ->
            categoryAdapter.updateCategories(ui.categories)
            binding.progressBar.isGone = true
        }

        // Popular (Featured)
        collectLoadable(vm.featured) { loadable ->
            when (loadable) {
                Loadable.Uninitialized, Loadable.Loading -> {
                    popularSkeleton.show(5)
                    binding.rvFeatured.isVisible = true
                }
                is Loadable.Data -> {
                    popularItems = loadable.value
                    warmPhotoUrls(popularItems, take = PRELOAD_AHEAD * 2)
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
                    nearSkeleton.show(5)
                    binding.rvNearMe.isVisible = true
                }
                is Loadable.Data -> {
                    nearItems = loadable.value
                    warmPhotoUrls(nearItems, take = PRELOAD_AHEAD * 2)
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

    // ───────────────────── URL cache + preloader integration ─────────────────────

    /** Try cache; if missing, schedule async compute and return null for now. */
    private fun cachedImageUrl(place: CoffeePlaceLite): String? {
        val cached = photoUrlCache[place.id]
        if (cached != null || photoUrlCache.containsKey(place.id)) return cached

        viewLifecycleOwner.lifecycleScope.launch {
            val url = place.images?.firstOrNull()?.let { meta ->
                coffeePlaceUtils.getPhotoUriFromMetadata(meta, maxWidthDp = 500)?.toString()
            }
            photoUrlCache[place.id] = url
        }
        return null
    }

    /** Eagerly compute first N URLs to avoid cold misses on initial draw. */
    private fun warmPhotoUrls(items: List<CoffeePlaceLite>, take: Int) {
        if (!isAdded || items.isEmpty()) return
        val n = min(items.size, take)
        viewLifecycleOwner.lifecycleScope.launch {
            for (i in 0 until n) {
                val p = items[i]
                if (!photoUrlCache.containsKey(p.id)) {
                    val url = p.images?.firstOrNull()?.let { meta ->
                        coffeePlaceUtils.getPhotoUriFromMetadata(meta, maxWidthDp = 500)?.toString()
                    }
                    photoUrlCache[p.id] = url
                }
            }
        }
    }

    /** Attach Glide preloader that cooperates with the skeleton header and real ImageView size. */
    private fun RecyclerView.attachPreloader(
        items: () -> List<CoffeePlaceLite>,
        skeletonCount: () -> Int,
        maxPreload: Int,
        sizeProvider: ViewPreloadSizeProvider<String>
    ) {
        val requestManager: RequestManager = Glide.with(this@HomeFragment)

        val provider = object : ListPreloader.PreloadModelProvider<String> {
            override fun getPreloadItems(position: Int): List<String> {
                val idx = position - skeletonCount()
                val list = items()
                if (idx !in list.indices) return emptyList()
                val place = list[idx]
                val url = photoUrlCache[place.id] ?: cachedImageUrl(place)
                return if (url == null) emptyList() else listOf(url)
            }

            override fun getPreloadRequestBuilder(item: String) =
                requestManager
                    .load(item)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .thumbnail(0.25f)
                    .centerCrop()
        }

        val preloader = RecyclerViewPreloader(
            requestManager,
            provider,
            sizeProvider, // exact ImageView size provided from adapter.bind()
            maxPreload
        )
        addOnScrollListener(preloader)
    }

    // ───────────────────── Location permissions & fetch ─────────────────────

    private fun ensureLocation() {
        val fine = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
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
                else fetchFreshLocationFallback()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), R.string.location_fetch_failed, Toast.LENGTH_SHORT).show()
                fetchFreshLocationFallback()
            }
    }

    private fun fetchFreshLocationFallback() {
        // If you have a suspend helper, call it here via lifecycleScope.launch { ... }
    }

    // ─────────────────────────── UI helpers ───────────────────────────

    private fun showAddToListBottomSheet(id: String) {
        val bottomSheet = AddPlacesToListBottomSheet.new(id)
        bottomSheet.show(childFragmentManager, "AddPlacesToListBottomSheet")
    }

    private fun navigateToCafeDetailsId(id: String) {
        val bundle = Bundle().apply { putString("placeId", id) }
        findNavController().navigate(R.id.action_homeFragment_to_cafeDetailFragment, bundle)
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}