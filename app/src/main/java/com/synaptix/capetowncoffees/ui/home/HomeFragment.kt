package com.synaptix.capetowncoffees.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.maps.model.LatLng
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.databinding.FragmentHomeNewBinding
import com.synaptix.capetowncoffees.domain.model.Category
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite
import com.synaptix.capetowncoffees.domain.usecase.coffeePlace.CoffeePlaceUtilsUseCase
import com.synaptix.capetowncoffees.ui.coffeeDetail.CafeDetailViewModel
import com.synaptix.capetowncoffees.ui.home.adapter.CategoryAdapter
import com.synaptix.capetowncoffees.ui.home.adapter.FeaturedAdapter
import com.synaptix.capetowncoffees.ui.home.adapter.NearMeAdapter
import com.synaptix.capetowncoffees.util.LocationUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment() {
    @Inject lateinit var locationUtil: LocationUtil
    @Inject lateinit var coffeePlaceUtilsUseCase: CoffeePlaceUtilsUseCase

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

    private var currentLocation: LatLng? = null
    private lateinit var categoryAdapter: CategoryAdapter

    @Inject lateinit var featuredAdapterFactory: FeaturedAdapter.Factory
    private lateinit var featuredAdapter: FeaturedAdapter
    @Inject lateinit var nearbyAdapterFactory: NearMeAdapter.Factory
    private lateinit var nearMeAdapter: NearMeAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeNewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fetchLocationAndInitUI()
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocationAndInitUI() {
        lifecycleScope.launch {
            showLoading(true)
            val result = locationUtil.getCurrentLatLng()
            showLoading(false)
            result.onSuccess { location ->
                currentLocation = location
                viewModel.setCurrentLocation(location)
                initAdapters(location)
                setupUI()
                setupObservers()
            }.onFailure { error ->
                showError("Could not get current location: ${error.message ?: "Unknown error"}")
                // Optionally, initialize UI with fallback (no location)
                initAdapters(null)
                setupUI()
                setupObservers()
            }
        }
    }

    private fun initAdapters(location: LatLng?) {
        categoryAdapter = CategoryAdapter { category ->
            viewModel.filterByCategory(category)
        }
        featuredAdapter = featuredAdapterFactory.create(
            currentLocation = location,
            coroutineScope = viewLifecycleOwner.lifecycleScope,
            onItemClick = { featuredItem -> navigateToCafeDetails(featuredItem) }
        )
        nearMeAdapter = nearbyAdapterFactory.create(
            currentLocation = location,
            coroutineScope = viewLifecycleOwner.lifecycleScope,
            onItemClick = { cafe ->
                navigateToCafeDetails(cafe)
            }
        )
    }


    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
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
