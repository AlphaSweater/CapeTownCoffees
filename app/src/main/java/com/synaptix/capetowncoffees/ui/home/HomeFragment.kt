package com.synaptix.capetowncoffees.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.net.PlacesClient
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.databinding.FragmentHomeNewBinding
import com.synaptix.capetowncoffees.domain.model.Category
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite
import com.synaptix.capetowncoffees.ui.home.adapter.*
import com.synaptix.capetowncoffees.ui.coffeeDetail.CafeDetailViewModel
import com.synaptix.capetowncoffees.util.LocationUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment() {

    @Inject
    lateinit var placesClient: PlacesClient
    
    @Inject
    lateinit var fusedLocationClient: FusedLocationProviderClient
    
    private var _binding: FragmentHomeNewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private val cafeDetailViewModel: CafeDetailViewModel by activityViewModels()
    
    private val categories = listOf(
        Category(1, "All", R.drawable.ic_medal),
        Category(2, "Popular", R.drawable.ic_star),
        Category(3, "Pet Friendly", R.drawable.baseline_pets_24),
        Category(4, "Nearby", R.drawable.ic_location),
        Category(5, "Dates", R.drawable.ic_heart)
    )

    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var featuredAdapter: FeaturedAdapter
    private lateinit var nearMeAdapter: NearMeAdapter
    
    private fun initAdapters() {
        categoryAdapter = CategoryAdapter { category ->
            viewModel.filterByCategory(category)
        }

        featuredAdapter = FeaturedAdapter(
            placesClient = placesClient,
            currentLocation = null,
            onItemClick = { featuredItem ->
                // Handle featured item click
                navigateToCafeDetails(featuredItem)
            }
        )

        nearMeAdapter = NearMeAdapter(
            placesClient = placesClient,
            currentLocation = null, // Will be updated when location is available
            onItemClick = { cafe ->
                navigateToCafeDetails(cafe)
            }
        )
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
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                    permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                // Precise location access granted
                requestLocation()
            }
            else -> {
                // No location access granted
                showError("Location permission is required to show nearby coffee places")
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize adapters after view is created and PlacesClient is injected
        initAdapters()
        setupUI()
        setupObservers()
        
        loadData()
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh data when returning to this fragment
        loadData()
    }
    
    private fun loadData() {
        if (checkLocationPermission()) {
            requestLocation()
        } else {
            requestLocationPermission()
        }
    }
    
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    
    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestLocationPermission() {
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
    
    private fun setupUI() {
        // Set up RecyclerViews
        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
        }

        binding.rvFeatured.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = featuredAdapter
            setHasFixedSize(true)
        }
        
        binding.rvNearMe.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = nearMeAdapter
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
    private var currentLocation: LatLng? = null
    
    private fun requestLocation() {
        if (!checkLocationPermission()) {
            showError("Location permission not granted")
            return
        }
        
        if (!isNetworkAvailable()) {
            showError("No internet connection")
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        // Convert Location to LatLng
                        val latLng = com.google.android.gms.maps.model.LatLng(
                            location.latitude,
                            location.longitude
                        )
                        currentLocation = latLng
                        viewModel.setCurrentLocation(latLng)
                        // The UI will be updated automatically through the uiState flow in updateUI()
                    } else {
                        // Last location is null, request a new one using the suspend function
                        viewLifecycleOwner.lifecycleScope.launch {
                            try {
                                if (!checkLocationPermission()) {
                                    showError("Location permission not granted")
                                    return@launch
                                }
                                
                                val newLocation = LocationUtil.getCurrentLocation(requireContext())
                                newLocation?.let {
                                    val newLatLng = com.google.android.gms.maps.model.LatLng(
                                        it.latitude,
                                        it.longitude
                                    )
                                    viewModel.setCurrentLocation(newLatLng)
                                } ?: showError("Could not get current location")
                            } catch (e: Exception) {
                                Timber.e(e, "Error getting current location")
                                showError("Could not get current location: ${e.message}")
                            }
                        }
                    }
                }.addOnFailureListener { e ->
                    Timber.e(e, "Error getting last location")
                    showError("Error getting location: ${e.message}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error in requestLocation")
                showError("Error: ${e.message}")
            }
        }
    }

    private fun updateUI(state: HomeViewModel.HomeUiState.Success) {
        try {
            // Update near me list with current location
            currentLocation?.let { location ->
                nearMeAdapter.updateItems(state.places, location)
                
                // Update featured items with current location
                if (state.featuredPlaces.isNotEmpty()) {
                    featuredAdapter.updateItems(state.featuredPlaces, location)
                    binding.rvFeatured.visibility = View.VISIBLE
                } else {
                    binding.rvFeatured.visibility = View.GONE
                }
                
                // Update categories
                categoryAdapter.updateCategories(categories)
            } ?: run {
                // If we don't have location, still try to show featured items without distance
                if (state.featuredPlaces.isNotEmpty()) {
                    featuredAdapter.updateItems(state.featuredPlaces, null)
                    binding.rvFeatured.visibility = View.VISIBLE
                } else {
                    binding.rvFeatured.visibility = View.GONE
                }
                
                // Update categories without location
                categoryAdapter.updateCategories(categories)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error updating UI")
            // Fallback to basic update if there's an error
            nearMeAdapter.updateItems(state.places, null)
            featuredAdapter.updateItems(state.featuredPlaces, null)
            categoryAdapter.updateCategories(categories)
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.visibility = View.GONE
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun navigateToCafeDetails(coffeePlace: CoffeePlaceLite) {
        try {
            val bundle = Bundle().apply {
                putString("placeId", coffeePlace.id)
            }
            findNavController().navigate(R.id.action_homeFragment_to_cafeDetailFragment, bundle)
        } catch (e: Exception) {
            Timber.e(e, "Error navigating to cafe detail")
            if (findNavController().currentDestination?.id == R.id.homeFragment) {
                findNavController().navigate(R.id.action_homeFragment_to_cafeDetailFragment)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
