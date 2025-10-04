package com.synaptix.capetowncoffees.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.databinding.FragmentHomeNewBinding
import com.synaptix.capetowncoffees.domain.model.Category
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite
import com.synaptix.capetowncoffees.ui._simple.*
import com.synaptix.capetowncoffees.ui._simple.viewmodel.Effect
import com.synaptix.capetowncoffees.ui._simple.viewmodel.Loadable
import com.synaptix.capetowncoffees.ui._simple.viewmodel.collect
import com.synaptix.capetowncoffees.ui._simple.viewmodel.collectLoadable
import com.synaptix.capetowncoffees.ui._simple.viewmodel.start
import com.synaptix.capetowncoffees.ui.home.adapter.CategoryAdapter
import com.synaptix.capetowncoffees.ui.home.adapter.FeaturedAdapter
import com.synaptix.capetowncoffees.ui.home.adapter.NearMeItemAdapter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeNewBinding? = null
    private val binding get() = _binding!!

    private val vm: HomeViewModel by viewModels()

    private lateinit var categoryAdapter: CategoryAdapter
    @Inject lateinit var featuredAdapterFactory: FeaturedAdapter.Factory
    private lateinit var featuredAdapter: FeaturedAdapter

    @Inject lateinit var nearMeAdapterFactory: NearMeItemAdapter.Factory
    private lateinit var nearMeAdapter: NearMeItemAdapter

    // Permissions launcher
    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val ok = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) fetchLocation() else Toast.makeText(requireContext(), "Location permission required", Toast.LENGTH_LONG).show()
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
                else               -> { /* none yet */}
            }
        }

        // Bind UI (categories, selection, current location, refreshing)
        collect(vm.ui.flow) { ui ->
            categoryAdapter.updateCategories(ui.categories)
            binding.progressBar.visibility = if (ui.isRefreshing) View.VISIBLE else View.GONE
        }

        // Featured section
        collectLoadable(vm.featured) { loadable ->
            when (loadable) {
                Loadable.Uninitialized, Loadable.Loading -> showFeaturedSkeleton(true)
                is Loadable.Data -> {
                    showFeaturedSkeleton(false)
                    featuredAdapter.updateItems(loadable.value, vm.ui.value.currentLocation)
                    binding.rvFeatured.visibility = if (loadable.value.isEmpty()) View.GONE else View.VISIBLE
                }
                is Loadable.Error -> {
                    showFeaturedSkeleton(false)
                    Toast.makeText(requireContext(), loadable.error.message, Toast.LENGTH_SHORT).show()
                    binding.rvFeatured.visibility = View.GONE
                }
            }
        }

        // Near-me list
        collectLoadable(vm.nearMe) { loadable ->
            when (loadable) {
                Loadable.Uninitialized, Loadable.Loading -> showNearMeSkeleton(true)
                is Loadable.Data -> {
                    showNearMeSkeleton(false)
                    nearMeAdapter.updateItems(loadable.value, vm.ui.value.currentLocation)
                    binding.rvNearMe.visibility = if (loadable.value.isEmpty()) View.GONE else View.VISIBLE
                }
                is Loadable.Error -> {
                    showNearMeSkeleton(false)
                    Toast.makeText(requireContext(), loadable.error.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showFeaturedSkeleton(show: Boolean) {
        // TODO: show/hide shimmer container for featured
        binding.rvFeatured.visibility = if (show) View.VISIBLE else binding.rvFeatured.visibility
    }

    private fun showNearMeSkeleton(show: Boolean) {
        // TODO: show/hide shimmer container for near-me
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
        // For brevity, we just give up silently; VM shows a message if it can’t fetch without location.
    }

    // ─────────────────────────── Click routing ───────────────────────────

    private fun onNearMeClick(nearMeItemClick: NearMeItemAdapter.Click) = when (nearMeItemClick) {
        is NearMeItemAdapter.Click.Open -> navigateToCafeDetailsId(nearMeItemClick.id)
        is NearMeItemAdapter.Click.ToggleFavorite -> {
            // TODO: hook up to VM if you track favorites
            // vm.toggleFavorite(c.id, c.newValue)
            Toast.makeText(requireContext(), "Fav ${nearMeItemClick.id}: ${nearMeItemClick.newValue}", Toast.LENGTH_SHORT).show()
        }
        is NearMeItemAdapter.Click.AddToList -> showAddToListBottomSheet(nearMeItemClick.id)
    }

    private fun showAddToListBottomSheet(id: String) {
        // TODO: open your AddPlacesToListBottomSheet here
        Toast.makeText(requireContext(), "Add to list: $id", Toast.LENGTH_SHORT).show()
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
