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
import androidx.recyclerview.widget.LinearLayoutManager
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
import com.synaptix.capetowncoffees.ui.home.adapter.FeaturedAdapter
import com.synaptix.capetowncoffees.ui.home.adapter.NearMeItemAdapter
import com.synaptix.capetowncoffees.ui.savedLists.AddPlacesToList.AddPlacesToListBottomSheet
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeNewBinding? = null
    private val binding get() = _binding!!

    private val vm: HomeViewModel by activityViewModels()

    private lateinit var categoryAdapter: CategoryAdapter
    @Inject lateinit var featuredAdapterFactory: FeaturedAdapter.Factory
    private lateinit var featuredAdapter: FeaturedAdapter

    @Inject lateinit var nearMeAdapterFactory: NearMeItemAdapter.Factory
    private lateinit var nearMeAdapter: NearMeItemAdapter

    private lateinit var featuredSkeletonAdapter: SkeletonAdapter
    private lateinit var nearSkeletonAdapter: SkeletonAdapter

    // Permissions launcher
    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val ok = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) fetchLocation() else
            Toast.makeText(requireContext(), "Location permission required", Toast.LENGTH_LONG).show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeNewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // SimpleVM lifecycle entry
        start(vm)

        initAdapters()
        setupRecyclerViews()
        setupCollectors()
        setupClicks()

        ensureLocation()
    }

    private fun initAdapters() {
        categoryAdapter = CategoryAdapter { category: Category ->
            vm.onCategorySelected(category)
        }
        featuredAdapter = featuredAdapterFactory.create(
            currentLocation = null,
            coroutineScope = viewLifecycleOwner.lifecycleScope,
            onItemClick = { item -> navigateToCafeDetailsId(item.id) }
        )
        nearMeAdapter = nearMeAdapterFactory.create(
            currentLocation = null,
            coroutineScope = viewLifecycleOwner.lifecycleScope,
            onClick = ::onNearMeClick
        )

        // ⭐ Self-shimmering skeleton rows (5 each)
        featuredSkeletonAdapter = SkeletonAdapter(
            count = 5,
            layoutResId = R.layout.item_place_skeleton
        )
        nearSkeletonAdapter = SkeletonAdapter(
            count = 5,
            layoutResId = R.layout.item_place_skeleton
        )

        // Disable change animations (prevents flicker on partial updates)
        (binding.rvNearMe.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        (binding.rvFeatured.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

    private fun setupRecyclerViews() = with(binding) {
        rvCategories.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
        }
        rvFeatured.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = featuredAdapter
            setHasFixedSize(true)
        }
        rvNearMe.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = nearMeAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupClicks() = with(binding) {
        searchBar.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_searchFragment)
        }
    }

    private fun setupCollectors() {
        // Effects (snackbars, nav, etc.)
        collect(vm.effects) { eff ->
            when (eff) {
                is Effect.Message  -> Toast.makeText(requireContext(), eff.text, Toast.LENGTH_SHORT).show()
                else               -> Unit
            }
        }

        // Bind UI (categories, selection, current location, refreshing)
        collect(vm.ui.flow) { ui ->
            categoryAdapter.updateCategories(ui.categories)
            // Let the section skeletons indicate loading; no center spinner
            binding.progressBar.isGone = true
        }

        // Featured
        collectLoadable(vm.featured) { loadable ->
            when (loadable) {
                Loadable.Uninitialized, Loadable.Loading -> {
                    if (binding.rvFeatured.adapter !== featuredSkeletonAdapter) {
                        binding.rvFeatured.adapter = featuredSkeletonAdapter
                    }
                    binding.rvFeatured.isVisible = true
                }
                is Loadable.Data -> {
                    if (binding.rvFeatured.adapter !== featuredAdapter) {
                        binding.rvFeatured.adapter = featuredAdapter
                    }
                    featuredAdapter.updateItems(loadable.value, vm.ui.value.currentLocation)
                    binding.rvFeatured.isVisible = loadable.value.isNotEmpty()
                }
                is Loadable.Error -> {
                    if (binding.rvFeatured.adapter !== featuredAdapter) {
                        binding.rvFeatured.adapter = featuredAdapter
                    }
                    binding.rvFeatured.isVisible = false
                    Toast.makeText(requireContext(), loadable.error.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Near Me
        collectLoadable(vm.nearMe) { loadable ->
            when (loadable) {
                Loadable.Uninitialized, Loadable.Loading -> {
                    if (binding.rvNearMe.adapter !== nearSkeletonAdapter) {
                        binding.rvNearMe.adapter = nearSkeletonAdapter
                    }
                    binding.rvNearMe.isVisible = true
                }
                is Loadable.Data -> {
                    if (binding.rvNearMe.adapter !== nearMeAdapter) {
                        binding.rvNearMe.adapter = nearMeAdapter
                    }
                    nearMeAdapter.updateItems(loadable.value, vm.ui.value.currentLocation)
                    binding.rvNearMe.isVisible = loadable.value.isNotEmpty()
                }
                is Loadable.Error -> {
                    if (binding.rvNearMe.adapter !== nearMeAdapter) {
                        binding.rvNearMe.adapter = nearMeAdapter
                    }
                    Toast.makeText(requireContext(), loadable.error.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ───────────────────── Location permissions & fetch ─────────────────────

    private fun ensureLocation() {
        val fine = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) fetchLocation() else requestPerms.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
        ))
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
                Toast.makeText(requireContext(), "Failed to get last location", Toast.LENGTH_SHORT).show()
                fetchFreshLocationFallback()
            }
    }

    private fun fetchFreshLocationFallback() {
        // If you have a suspend util (like LocationUtil.getCurrentLatLng()), call it here in a coroutine.
    }

    // ─────────────────────────── Click routing ───────────────────────────

    private fun onNearMeClick(c: NearMeItemAdapter.Click) = when (c) {
        is NearMeItemAdapter.Click.Open -> navigateToCafeDetailsId(c.id)
        is NearMeItemAdapter.Click.ToggleFavorite -> {
            // Hook to VM if you track favorites:
            // vm.toggleFavorite(c.id, c.newValue)
            Toast.makeText(requireContext(), "Fav ${c.id}: ${c.newValue}", Toast.LENGTH_SHORT).show()
        }
        is NearMeItemAdapter.Click.AddToList -> showAddToListBottomSheet(c.id)
    }

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
        _binding = null
    }
}
