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
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.databinding.FragmentHomeNewBinding
import com.synaptix.capetowncoffees.domain.model.Category
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

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeNewBinding? = null
    private val binding get() = _binding!!

    private val vm: HomeViewModel by activityViewModels()

    private lateinit var categoryAdapter: CategoryAdapter

    // Unified item adapter factory
    @Inject lateinit var placeItemAdapterFactory: CoffeePlaceItemAdapter.Factory

    // Two instances: one for Popular (chip ON), one for Near Me (chip OFF)
    private lateinit var popularAdapter: CoffeePlaceItemAdapter
    private lateinit var nearAdapter: CoffeePlaceItemAdapter

    // Self-shimmering skeleton rows (we’ll toggle item count instead of swapping adapters)
    private lateinit var popularSkeleton: SkeletonAdapter
    private lateinit var nearSkeleton: SkeletonAdapter

    // Concat to keep adapters stable and avoid scroll jumps
    private lateinit var popularConcat: ConcatAdapter
    private lateinit var nearConcat: ConcatAdapter

    // Share one pool so both recyclers reuse the same view holders
    private val sharedPool = RecyclerView.RecycledViewPool()

    // Permissions launcher
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
        setupCollectors()
        setupClicks()
        ensureLocation()
    }

    // ───────────────────────── Adapters ─────────────────────────

    private fun initAdapters() {
        categoryAdapter = CategoryAdapter { category: Category ->
            vm.onCategorySelected(category)
        }

        // One onClick router shared by both lists
        val itemClick: (CoffeePlaceItemAdapter.Click) -> Unit = { click ->
            when (click) {
                is CoffeePlaceItemAdapter.Click.Open -> navigateToCafeDetailsId(click.id)
                is CoffeePlaceItemAdapter.Click.ToggleFavorite -> {
                    // vm.toggleFavorite(click.id, click.newValue)
                    Toast.makeText(requireContext(), "Fav ${click.id}: ${click.newValue}", Toast.LENGTH_SHORT).show()
                }
                is CoffeePlaceItemAdapter.Click.AddToList -> showAddToListBottomSheet(click.id)
            }
        }

        popularAdapter = placeItemAdapterFactory.create(
            currentLocation = null,
            onClick = itemClick,
            showPopularChip = true
        )
        nearAdapter = placeItemAdapterFactory.create(
            currentLocation = null,
            onClick = itemClick,
            showPopularChip = false
        )

        // Skeletons (we'll toggle item count to show/hide)
        popularSkeleton = SkeletonAdapter(count = 5, layoutResId = R.layout.item_place_skeleton)
        nearSkeleton = SkeletonAdapter(count = 5, layoutResId = R.layout.item_place_skeleton)

        // Keep one adapter instance mounted to avoid jank; add/remove skeleton items logically
        popularConcat = ConcatAdapter(popularSkeleton, popularAdapter)
        nearConcat = ConcatAdapter(nearSkeleton, nearAdapter)
    }

    // ───────────────────────── RecyclerViews ─────────────────────────

    private fun setupRecyclerViews() = with(binding) {
        // Shared pool & animator tweaks for both lists
        fun RecyclerView.tune(commonHorizontal: Boolean) {
            setRecycledViewPool(sharedPool)
            setHasFixedSize(true)
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
            setItemViewCacheSize(12) // cache a few offscreen rows
            isNestedScrollingEnabled = false

            val lm = if (commonHorizontal)
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            else
                LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)

            // prefetch siblings (helps when scrolling quickly)
            lm.initialPrefetchItemCount = 6
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
        }

        rvNearMe.apply {
            tune(commonHorizontal = false)
            adapter = nearConcat
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
        // Effects
        collect(vm.effects) { eff ->
            when (eff) {
                is Effect.Message -> Toast.makeText(requireContext(), eff.text, Toast.LENGTH_SHORT).show()
                else -> Unit
            }
        }

        // Top-level UI (categories etc.)
        collect(vm.ui.flow) { ui ->
            categoryAdapter.updateCategories(ui.categories)
            binding.progressBar.isGone = true
        }

        // Popular (Featured) section
        collectLoadable(vm.featured) { loadable ->
            when (loadable) {
                Loadable.Uninitialized, Loadable.Loading -> {
                    setSkeletonVisible(popularSkeleton, true)
                    binding.rvFeatured.isVisible = true
                }
                is Loadable.Data -> {
                    setSkeletonVisible(popularSkeleton, false)
                    popularAdapter.updateItems(loadable.value, vm.ui.value.currentLocation)
                    binding.rvFeatured.isVisible = loadable.value.isNotEmpty()
                }
                is Loadable.Error -> {
                    setSkeletonVisible(popularSkeleton, false)
                    binding.rvFeatured.isVisible = false
                    Toast.makeText(requireContext(), loadable.error.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Near Me section
        collectLoadable(vm.nearMe) { loadable ->
            when (loadable) {
                Loadable.Uninitialized, Loadable.Loading -> {
                    setSkeletonVisible(nearSkeleton, true)
                    binding.rvNearMe.isVisible = true
                }
                is Loadable.Data -> {
                    setSkeletonVisible(nearSkeleton, false)
                    nearAdapter.updateItems(loadable.value, vm.ui.value.currentLocation)
                    binding.rvNearMe.isVisible = loadable.value.isNotEmpty()
                }
                is Loadable.Error -> {
                    setSkeletonVisible(nearSkeleton, false)
                    // Keep list mounted to preserve scroll; show toast only
                    Toast.makeText(requireContext(), loadable.error.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Toggles skeleton item count without swapping adapters, preventing scroll jumps.
     * If your SkeletonAdapter doesn't expose a setter, add one; or re-create with desired count and call notifyDataSetChanged().
     */
    private fun setSkeletonVisible(skeleton: SkeletonAdapter, visible: Boolean) {
        val newCount = if (visible) 5 else 0
        if (skeleton.itemCount != newCount) {
            skeleton.setCount(newCount) // add this method to SkeletonAdapter
        }
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
        // TODO: If you have a suspend helper, call it here via lifecycleScope.launch { ... }
    }

    // ─────────────────────────── Helpers ───────────────────────────

    private fun showAddToListBottomSheet(id: String) {
        val bottomSheet = AddPlacesToListBottomSheet.new(id)
        bottomSheet.show(childFragmentManager, "AddPlacesToListBottomSheet")
    }

    private fun navigateToCafeDetailsId(id: String) {
        val bundle = Bundle().apply { putString("placeId", id) }
        findNavController().navigate(R.id.action_homeFragment_to_cafeDetailFragment, bundle)
    }

    // ─────────────────────────── Lifecycle ───────────────────────────

    override fun onDestroyView() {
        super.onDestroyView()
        // (The adapters live only while view is alive; no leak because we don't hold view refs.)
        _binding = null
    }
}
