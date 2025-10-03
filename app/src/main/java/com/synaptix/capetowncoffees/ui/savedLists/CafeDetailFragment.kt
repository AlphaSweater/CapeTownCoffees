package com.synaptix.capetowncoffees.ui.savedLists

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.net.PlacesClient
import com.synaptix.capetowncoffees.databinding.FragmentCafeDetailBinding
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.synaptix.capetowncoffees.R
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceFull
import com.synaptix.capetowncoffees.domain.model.CoffeePlaceLite
import com.synaptix.capetowncoffees.util.LocationUtil
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@AndroidEntryPoint
class CafeDetailFragment : Fragment() {

    @Inject
    lateinit var placesClient: PlacesClient

    private val viewModel: CafeDetailViewModel by activityViewModels()
    private var _binding: FragmentCafeDetailBinding? = null
    private val binding get() = _binding!!
    private var currentLocation: LatLng? = null
    private var currentCafe: CoffeePlaceFull? = null
    
    // Navigation arguments
    private val args: CafeDetailFragmentArgs by navArgs()
    
    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCafeDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Set up UI listeners
        setupUiListeners()
        
        // Observe ViewModel state
        observeViewModel()
        
        // Load coffee place details
        loadCoffeePlace()
        
        // Request location when the fragment starts
        getCurrentLocation()
    }
    
    private fun setupUiListeners() {
        binding.apply {
            // Set up back button
            btnBack.setOnClickListener {
                findNavController().navigateUp()
            }
            
            // Set up favorite button
            btnHeart.setOnClickListener {
                // TODO: Implement favorite functionality
            }
            
            // Set up distance refresh
            tvDistance.setOnClickListener {
                getCurrentLocation()
            }
        }
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is CafeDetailUiState.Loading -> {
                        showLoading(true)
                    }
                    is CafeDetailUiState.Success -> {
                        showLoading(false)
                        updateUI(state.coffeePlace)
                        
                        // If we have a location, update the distance
                        currentLocation?.let {
                            updateDistance(state.coffeePlace)
                        } ?: run {
                            // If we don't have a location yet, try to get it
                            getCurrentLocation()
                        }
                    }
                    is CafeDetailUiState.Error -> {
                        showLoading(false)
                        Timber.e("Error loading cafe details: ${state.message}")
                    }
                }
            }
        }
    }
    
    private fun loadCoffeePlace() {
        // Get the coffee place from navigation arguments
        viewModel.loadCoffeePlace(placeId = args.placeId.ifEmpty { null })
    }

    private fun updateUI(cafe: CoffeePlaceFull) {
        Timber.d("Updating UI for cafe: ${cafe.name}")
        Timber.d("Phone number fields - national: '${cafe.nationalPhoneNumber}', international: '${cafe.internationalPhoneNumber}'")
        
        // Store the cafe reference for later use
        currentCafe = cafe
        
        // If we already have a location, update the distance
        currentLocation?.let {
            updateDistance(cafe)
        } ?: run {
            // If we don't have a location yet, try to get it
            getCurrentLocation()
        }
        
        binding.apply {
            // Load cafe image if available
            cafe.images?.firstOrNull()?.let { photoMetadata ->
                val photoRequest = FetchPhotoRequest.builder(photoMetadata)
                    .setMaxWidth(1000) // Adjust based on your needs
                    .build()
                    
                placesClient.fetchPhoto(photoRequest)
                    .addOnSuccessListener { fetchPhotoResponse ->
                        val bitmap = fetchPhotoResponse.bitmap
                        ivImage.setImageBitmap(bitmap)
                    }
                    .addOnFailureListener { exception ->
                        Log.e("CafeDetailFragment", "Error loading image: ${exception.message}")
                        // Set a default image if loading fails
                        ivImage.setImageResource(R.drawable.featured_placeholder)
                    }
            } ?: run {
                // No images available, set default image
                ivImage.setImageResource(R.drawable.featured_placeholder)
            }
            
            // Cafe Name
            tvCafeName.text = cafe.name ?: ""

            // Address
            val addressText = cafe.address ?: ""
            tvCafeAddress.text = addressText
            
            // Set up opening hours with grouped days
            cafe.currentOpeningHours?.let { hours ->
                if (hours.isNotEmpty()) {
                    val formattedHours = formatOpeningHours(hours)
                    tvOpeningHours.text = formattedHours
                } else {
                    tvOpeningHours.text = getString(R.string.no_opening_hours_available)
                }
            } ?: run {
                tvOpeningHours.text = getString(R.string.no_opening_hours_available)
            }
            
            // Set up click listener for address to open maps if location is available
            if (cafe.location != null) {
                tvCafeAddress.paintFlags = tvCafeAddress.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
                tvCafeAddress.setOnClickListener {
                    openMaps(cafe.location, addressText)
                }
            } else {
                tvCafeAddress.paintFlags = tvCafeAddress.paintFlags and android.graphics.Paint.UNDERLINE_TEXT_FLAG.inv()
                tvCafeAddress.setOnClickListener(null)
            }
            
            // Phone Number
            val phoneNumber = cafe.nationalPhoneNumber ?: cafe.internationalPhoneNumber
            Log.d("CafeDetailFragment", "Using phone number: '$phoneNumber'")
            
            if (!phoneNumber.isNullOrEmpty()) {
                // Show phone number in the dedicated phone TextView
                tvPhone.text = phoneNumber
                tvPhone.visibility = View.VISIBLE
                
                // Make phone number clickable
                tvPhone.setOnClickListener {
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:${phoneNumber.filter { it.isDigit() }}")
                    }
                    startActivity(intent)
                }
            } else {
                // Hide phone number view if no number is available
                tvPhone.visibility = View.GONE
            }

            // Rating and Distance
            cafe.rating?.let { rating ->
                tvRating.text = String.format("%.1f", rating)
                ratingBar.rating = rating.toFloat()
                ratingBar.visibility = View.VISIBLE
                tvRating.visibility = View.VISIBLE
                
                // Update distance if location is available
                currentLocation?.let { userLocation ->
                    cafe.location?.let { cafeLocation ->
                        val distanceText = LocationUtil.getFormattedDistance(userLocation, cafeLocation)
                        tvDistance.text = distanceText
                        tvDistance.visibility = View.VISIBLE
                        ivPin.visibility = View.VISIBLE
                    } ?: run {
                        tvDistance.visibility = View.GONE
                        ivPin.visibility = View.GONE
                    }
                } ?: run {
                    tvDistance.visibility = View.GONE
                    ivPin.visibility = View.GONE
                }
            } ?: run {
                ratingBar.visibility = View.GONE
                tvRating.visibility = View.GONE
                tvDistance.visibility = View.GONE
                ivPin.visibility = View.GONE
            }
            
            // Show rating count if available
            cafe.ratingCount?.let { count ->
                tvRating.text = "($count)"
                tvRating.visibility = View.VISIBLE
            } ?: run {
                tvRating.visibility = View.GONE
            }
        }
    }
    
    private fun getCurrentLocation() {
        Timber.d("getCurrentLocation called")
        
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Timber.d("Location permissions not granted, requesting...")
            // Request permissions if not granted
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }

        Timber.d("Location permissions granted, fetching location...")
        
        // Get the current location in a coroutine
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Timber.d("Starting location fetch...")
                currentLocation = withContext(Dispatchers.IO) {
                    val location = LocationUtil.getCurrentLocation(requireContext())
                    Timber.d("Location fetched: $location")
                    location
                }
                
                Timber.d("Current location set to: $currentLocation")
                
                // Update the UI with the new location
                (viewModel.uiState.value as? CafeDetailUiState.Success)?.let { state ->
                    Timber.d("Updating distance for cafe: ${state.coffeePlace.name}")
                    updateDistance(state.coffeePlace)
                } ?: run {
                    Timber.d("UI state is not in Success state")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error getting location")
                withContext(Dispatchers.Main) {
                    Timber.d("Error occurred, hiding distance views")
                    binding.tvDistance.visibility = View.GONE
                    binding.ivPin.visibility = View.GONE
                }
            }
        }
    }
    
    private fun updateDistance(cafe: CoffeePlaceFull) {
        Timber.d("updateDistance called for cafe: ${cafe.name}")
        Timber.d("Cafe location: ${cafe.location}")
        Timber.d("Current user location: $currentLocation")
        
        cafe.location?.let { cafeLocation ->
            currentLocation?.let { userLocation ->
                Timber.d("Calculating distance between $userLocation and $cafeLocation")
                val distanceText = LocationUtil.getFormattedDistance(userLocation, cafeLocation)
                Timber.d("Calculated distance: $distanceText")
                
                // Make sure we're on the main thread when updating the UI
                view?.post {
                    binding.tvDistance.text = distanceText
                    binding.tvDistance.visibility = View.VISIBLE
                    binding.ivPin.visibility = View.VISIBLE
                    Timber.d("Distance UI updated with: $distanceText")
                }
                return@let
            } ?: run {
                Timber.d("Current user location is null")
                view?.post {
                    binding.tvDistance.visibility = View.GONE
                    binding.ivPin.visibility = View.GONE
                }
            }
        } ?: run {
            Timber.d("Cafe location is null")
            view?.post {
                binding.tvDistance.visibility = View.GONE
                binding.ivPin.visibility = View.GONE
            }
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && 
                grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                Timber.d("Location permissions granted, getting current location...")
                // Permission granted, get the location
                getCurrentLocation()
            } else {
                Timber.d("Location permissions denied")
                // Permission denied
                binding.tvDistance.visibility = View.GONE
                binding.ivPin.visibility = View.GONE
            }
        }
    }
    
    private fun formatOpeningHours(hours: List<String>): String {
        if (hours.isEmpty()) return getString(R.string.no_opening_hours_available)
        
        val dayAbbreviations = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        
        // First, extract just the time part from each day's hours
        val hoursByDay = hours.mapIndexed { index, fullDayHours ->
            // Extract just the time part (e.g., "9:00 AM - 6:00 PM" from "Monday: 9:00 AM - 6:00 PM")
            val timePart = fullDayHours.substringAfter(": ", fullDayHours)
            dayAbbreviations[index] to timePart
        }
        
        val result = mutableListOf<String>()
        var currentRange = mutableListOf<String>()
        var currentHours = ""
        
        for ((day, hour) in hoursByDay) {
            if (currentHours != hour) {
                if (currentRange.isNotEmpty()) {
                    result.add(formatDayRange(currentRange, currentHours))
                    currentRange.clear()
                }
                currentHours = hour
            }
            currentRange.add(day)
        }
        
        // Add the last range
        if (currentRange.isNotEmpty()) {
            result.add(formatDayRange(currentRange, currentHours))
        }
        
        return result.joinToString("\n")
    }
    
    private fun formatDayRange(days: List<String>, hours: String): String {
        return when (days.size) {
            1 -> "${days[0]}: $hours"
            2 -> "${days[0]} & ${days[1]}: $hours"
            else -> "${days.first()} - ${days.last()}: $hours"
        }
    }
    
    private fun openMaps(location: LatLng, address: String) {
        val gmmIntentUri = Uri.parse("geo:${location.latitude},${location.longitude}?q=${Uri.encode(address)}")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")
        
        if (mapIntent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(mapIntent)
        } else {
            // Fallback to browser if Google Maps is not installed
            val browserIntent = Intent(
                Intent.ACTION_VIEW, 
                Uri.parse("https://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}")
            )
            startActivity(browserIntent)
        }
    }
    
    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        viewModel.clearSelectedCafe()
    }
}