package com.synaptix.capetowncoffees.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.synaptix.capetowncoffees.databinding.FragmentHomeNewBinding
import com.synaptix.capetowncoffees.domain.model.Category
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.ui.home.adapter.CategoryAdapter
import com.synaptix.capetowncoffees.ui.home.adapter.FeaturedAdapter
import com.synaptix.capetowncoffees.ui.home.adapter.NearMeAdapter
import com.synaptix.capetowncoffees.util.LocationUtil
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeNewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    @Inject
    lateinit var placesClient: com.google.android.libraries.places.api.net.PlacesClient

    private val categories = listOf(
        Category(1, "All", R.drawable.ic_medal),
        Category(3, "Pet Friendly", R.drawable.baseline_pets_24),
        Category(4, "Nearby", R.drawable.ic_location),
        Category(5, "Dates", R.drawable.ic_heart)
    )

    private val categoryAdapter by lazy {
        CategoryAdapter { category ->
            // Handle category selection
            viewModel.filterByCategory(category)
        }
    }

    private val nearMeAdapter by lazy {
        NearMeAdapter(
            emptyList(),
            placesClient,
            onItemClick = { cafe ->
                navigateToCafeDetails(cafe)
            }
        )
    }

    private val featuredAdapter by lazy {
        FeaturedAdapter(emptyList(), placesClient) { featuredItem ->
            // Handle featured item click
            // navigateToCafeDetails(featuredItem.id)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeNewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupObservers()
        requestLocation()
    }

    private fun setupUI() {
        // Setup categories RecyclerView
        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
            setHasFixedSize(true)
        }
        categoryAdapter.updateCategories(categories)

        // Setup near me RecyclerView
        binding.rvNearMe.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            adapter = nearMeAdapter
            setHasFixedSize(true)
        }

        // Setup featured RecyclerView
        binding.rvFeatured.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = featuredAdapter
            setHasFixedSize(true)
        }

        // Setup search
        binding.searchBar.setOnClickListener {
            // Handle search click
            findNavController().navigate(R.id.action_homeFragment_to_searchFragment)
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is HomeViewModel.HomeUiState.Loading -> showLoading(true)
                        is HomeViewModel.HomeUiState.Error -> {
                            showLoading(false)
                            showError(state.message)
                        }
                        is HomeViewModel.HomeUiState.Success -> {
                            showLoading(false)
                            updateUI(state)
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocation() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        // Convert Location to LatLng
                        val latLng = com.google.android.gms.maps.model.LatLng(
                            location.latitude,
                            location.longitude
                        )
                        viewModel.setCurrentLocation(latLng)
                    } else {
                        // Last location is null, request a new one using the suspend function
                        viewLifecycleOwner.lifecycleScope.launch {
                            try {
                                val newLocation = LocationUtil.getCurrentLocation(requireContext())
                                newLocation?.let {
                                    val newLatLng = com.google.android.gms.maps.model.LatLng(
                                        it.latitude,
                                        it.longitude
                                    )
                                    viewModel.setCurrentLocation(newLatLng)
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Error getting current location")
                                showError("Could not get current location")
                            }
                        }
                    }
                }.addOnFailureListener { e ->
                    Timber.e(e, "Error getting last location")
                    showError("Could not get location")
                }
            } catch (e: Exception) {
                Timber.e(e, "Location permission not granted")
                showError("Location permission required")
            }
        }
    }

    private fun updateUI(state: HomeViewModel.HomeUiState.Success) {
        // Update near me list
        nearMeAdapter.updateItems(state.places)

        // Update featured items
        featuredAdapter.updateItems(state.featuredPlaces)
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun navigateToCafeDetails(coffeePlace: CoffeePlaceLite) {
        val action = HomeFragmentDirections.actionHomeFragmentToCafeDetailFragment(
            cafeName = coffeePlace.name ?: "Cafe",
            cafeRating = coffeePlace.rating?.toFloat() ?: 0f,
            cafeDistance = 0f, // You might want to calculate this
            cafePriceRange = coffeePlace.priceLevel?.let { "$".repeat(it.coerceAtMost(4)) } ?: "$"
        )
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
